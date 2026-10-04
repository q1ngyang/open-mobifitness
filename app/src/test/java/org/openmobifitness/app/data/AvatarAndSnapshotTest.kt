package org.openmobifitness.app.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarAndSnapshotTest {
    @Test fun historicalUnitsOnlyUnlockReportAxesWithExplicitCanonicalSemantics() {
        val snapshot="""{"version":1,"machine":"ELLIPTICAL","protocol":"DEMO","model":"Test","evidence":"test-v1","units":{"incline":"percent","force":"N","speed":"mph"}}"""
        val session=Session(machine=Machine.ELLIPTICAL,protocol=Protocol.DEMO,capabilitySnapshot=snapshot)
        assertEquals(setOf(SeriesMetric.INCLINE,SeriesMetric.FORCE),session.reportEvidence())
        assertTrue(session.copy(capabilitySnapshot=snapshot.replace("\"version\":1","\"version\":2")).reportEvidence().isEmpty())
    }
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var repo: Repository
    @Before fun setup() { context.deleteDatabase("openmobi.db"); context.getSharedPreferences("preferences",0).edit().clear().commit(); repo=Repository(context) }
    @After fun close() { repo.db.close() }
    @Test fun interruptedAvatarDeletionRetriesWithoutRemovingSharedPhotosOrUnrelatedDrafts()=runBlocking(Dispatchers.IO) {
        val reference="1".repeat(64)+".jpg"; val draft="2".repeat(64)+".jpg"
        repo.avatars.file(reference).writeBytes(byteArrayOf(1)); repo.avatars.file(draft).writeBytes(byteArrayOf(2))
        val user=UserProfile(name="Removed photo",avatar=reference); repo.saveUser(user)
        // A journal left before a rolled-back DB change must preserve its still referenced image.
        repo.avatars.queueCleanup(setOf(reference)); repo.recoverAvatarCleanup()
        assertTrue(repo.avatars.file(reference).isFile)
        // Make deletion fail once, standing in for a transient private-storage failure.
        val target=repo.avatars.file(reference); assertTrue(target.delete()); assertTrue(target.mkdir())
        val obstacle=File(target,"busy").apply { writeText("test") }
        repo.removeUser(repo.removalPreview(user.id),true,true)
        assertTrue(repo.db.records().user(user.id).deleted); assertTrue(target.exists())
        assertTrue(obstacle.delete())
        repo.db.close(); repo=Repository(context); repo.load()
        assertFalse(target.exists()); assertTrue(repo.avatars.file(draft).exists())
        assertTrue(context.getSharedPreferences("avatar_cleanup",0).getStringSet("pending",emptySet()).isNullOrEmpty())
    }
    @Test fun localImageBecomesPrivateSquareAndAvatarResourceSurvivesAllUserBackup()=runBlocking(Dispatchers.IO) {
        val input=File(context.cacheDir,"avatar-input.png")
        val bitmap=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        val reference=repo.avatars.select(Uri.fromFile(input)); input.delete()
        val bytes=repo.avatars.file(reference).readBytes(); repo.avatars.verify(reference,bytes)
        val user=UserProfile(name="Photo user",avatar=reference); repo.saveUser(user)
        repo.discardAvatars(setOf(reference)); assertTrue(repo.avatars.file(reference).exists())
        val discarded="0".repeat(64)+".jpg"; repo.avatars.file(discarded).writeBytes(byteArrayOf(1))
        repo.discardAvatars(setOf(discarded)); assertFalse(repo.avatars.file(discarded).exists())
        val backup=ByteArrayOutputStream(); BackupTransfer.export(repo,backup,null)
        repo.avatars.file(reference).delete()
        BackupTransfer.stage(context,repo,backup.toByteArray().inputStream()).use { assertEquals(0L,it.preview.conflicts); BackupTransfer.restore(repo,it,emptySet()) }
        assertArrayEquals(bytes,repo.avatars.file(reference).readBytes()); assertEquals(reference,repo.users.value.single().avatar)
        try { repo.avatars.verify(reference,bytes.copyOfRange(0,bytes.size-2)); fail() } catch(_: IllegalArgumentException) { }
        repo.removeUser(repo.removalPreview(user.id),true,true)
        assertFalse(repo.avatars.file(reference).exists())
    }
    @Test fun unknownCapabilityVersionsAndMismatchedPlanOwnersCannotReachLiveDatabase()=runBlocking(Dispatchers.IO) {
        val user=UserProfile(name="Owner"); repo.saveUser(user)
        val session=Session(machine=Machine.BIKE,protocol=Protocol.DEMO,status="completed",ownerUserId=user.id,startedUserId=user.id,identityVersion=1,
            capabilitySnapshot="{\"version\":2,\"machine\":\"BIKE\",\"protocol\":\"DEMO\"}")
        try { BackupTransfer.stage(context,repo,Exchange.sessions(listOf(session)).byteInputStream()).close(); fail() } catch(_: IllegalArgumentException) { }
        assertEquals(0L,repo.db.records().sessionCount())
        val plan=Workout(title="Historical",machine=Machine.BIKE,ownerUserId=user.id,steps=listOf(Step(target=60.0,id="first"),Step(target=60.0,id="second")))
        val wrong=session.copy(capabilitySnapshot="",workoutId=plan.id,workoutSnapshot=Exchange.workouts(listOf(plan)),enteredStageIds="second")
        try { BackupTransfer.stage(context,repo,Exchange.sessions(listOf(wrong)).byteInputStream()).close(); fail() } catch(_: IllegalArgumentException) { }
        val valid=wrong.copy(enteredStageIds="first;second")
        BackupTransfer.stage(context,repo,Exchange.sessions(listOf(valid)).byteInputStream()).use { BackupTransfer.restore(repo,it,emptySet()) }
        assertEquals("first;second",repo.history.session(valid.id)!!.enteredStageIds)
    }
    @Test fun frozenAllUserQueryDoesNotExpandWhenAUserArrivesDuringFileSelection()=runBlocking {
        val a=UserProfile(name="Before picker"); repo.saveUser(a)
        val frozen=HistoryQuery(owner="all").frozen(repo.users.value)
        val b=UserProfile(name="After picker"); repo.saveUser(b)
        val first=Session(ownerUserId=a.id,status="stopped"); val second=first.copy(id=java.util.UUID.randomUUID().toString(),ownerUserId=b.id)
        repo.save(first); repo.save(second); repo.profilePreferences.select(b.id)
        assertEquals(listOf(first.id),repo.history.reportIds(HistoryQuery.decode(frozen.encode())))
        assertEquals(2,repo.history.reportIds(HistoryQuery(owner="all")).size)
    }
}
