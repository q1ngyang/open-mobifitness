package org.openmobifitness.app.data

import android.app.Application
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class UserStoreTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var repo: Repository
    @Before fun setup() { context.deleteDatabase("openmobi.db"); context.getSharedPreferences("preferences",0).edit().clear().commit(); repo=Repository(context) }
    @After fun close() { repo.db.close() }
    private suspend fun person(name: String)=UserProfile(name=name).also { repo.saveUser(it) }
    private fun record(user: UserProfile)=Session(ownerUserId=user.id,startedUserId=user.id,startedUserName=user.name,identityVersion=1,weightSource="default",metSource="default",status="stopped",elapsedMs=2000)
    @Test fun preferencesNeverFallBackToAnotherPersonAndDelayedEditsKeepTheirOwner()=runBlocking {
        val a=person("A"); val b=person("B"); val preferences=repo.profilePreferences
        repo.preferences.edit().putString("theme","dark").commit()
        preferences.select(a.id); assertNull(preferences.getString("theme",null))
        val delayed=preferences.edit().putString("theme","light")
        preferences.select(b.id); delayed.commit()
        assertNull(preferences.getString("theme",null)); preferences.select(a.id); assertEquals("light",preferences.getString("theme",null))
        preferences.edit().putString("language","de").commit(); preferences.select(b.id); assertEquals("de",preferences.getString("language",null))
    }
    @Test fun ownerCorrectionPreservesSnapshotsAndDeletingPreviousOwnerKeepsTheReassignedRecord()=runBlocking {
        val a=person("A"); val b=person("B"); val s=record(a); repo.save(s,Sample(s.id,1000,Metrics(cadence=60.0)))
        repo.changeOwners(listOf(s.id),b.id,mapOf(s.id to a.id))
        val updated=repo.history.session(s.id)!!
        assertEquals(a.id,updated.startedUserId); assertEquals("A",updated.startedUserName); assertEquals(b.id,updated.ownerUserId)
        assertTrue(updated.ownerHistory.contains(a.id)); assertTrue(updated.ownerHistory.contains(b.id))
        repo.removeUser(repo.removalPreview(a.id),true,true)
        assertEquals(updated,repo.history.session(s.id)); assertEquals(1,repo.archive().samples.size)
        assertTrue(repo.users.value.first { it.id==a.id }.deleted)
    }
    @Test fun softRemovalAndConfirmedHardDeletionAreDifferentAndPreviewIsRechecked()=runBlocking {
        val a=person("A"); repo.save(record(a)); val preview=repo.removalPreview(a.id)
        try { repo.removeUser(preview,true,false); fail() } catch(_: IllegalArgumentException) { }
        assertEquals(1,repo.archive().sessions.size)
        repo.save(record(a))
        try { repo.removeUser(preview,true,true); fail() } catch(_: IllegalArgumentException) { }
        repo.removeUser(repo.removalPreview(a.id),false,false)
        assertEquals(2,repo.archive().sessions.size); assertFalse(repo.users.value.single().available)
        repo.restoreUser(a.id); repo.removeUser(repo.removalPreview(a.id),true,true)
        assertTrue(repo.archive().sessions.isEmpty()); assertTrue(repo.users.value.single().deleted)
    }
    @Test fun activeAndWrongOwnerMutationsAreRejected()=runBlocking {
        val a=person("A"); val b=person("B"); val w=Workout(title="A plan",machine=Machine.BIKE,ownerUserId=a.id,steps=listOf(Step(target=60.0)))
        repo.profilePreferences.select(a.id)
        repo.saveWorkout(w)
        try { repo.saveWorkout(w.copy(ownerUserId=b.id)); fail() } catch(_: IllegalArgumentException) { }
        val active=record(a).copy(status="active"); repo.save(active)
        try { repo.changeOwners(listOf(active.id),b.id,mapOf(active.id to a.id)); fail() } catch(_: IllegalArgumentException) { }
        try { repo.removeUser(repo.removalPreview(a.id),true,true); fail() } catch(_: IllegalArgumentException) { }
        assertEquals(active,repo.history.session(active.id))
    }
    @Test fun allUsersBackupRestoresPersonalPreferencesWithoutSelectingOrOverwritingExistingUsers()=runBlocking(Dispatchers.IO) {
        val a=person("A"); val b=person("B"); repo.save(record(a)); repo.save(record(b))
        repo.profilePreferences.forUser(a.id).edit().putString("theme","dark").commit()
        repo.removeUser(repo.removalPreview(b.id),false,false)
        val bytes=ByteArrayOutputStream(); BackupTransfer.export(repo,bytes,null)
        repo.saveUser(a.copy(name="Renamed locally")); repo.profilePreferences.select(a.id)
        repo.profilePreferences.forUser(a.id).edit().putString("theme","light").commit()
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { staged ->
            assertEquals(2L,staged.preview.users); assertEquals(1L,staged.preview.removedUsers)
            assertEquals(0L,BackupTransfer.restore(repo,staged,emptySet()))
        }
        assertEquals("Renamed locally",repo.users.value.first { it.id==a.id }.name)
        assertEquals("light",repo.profilePreferences.forUser(a.id).getString("theme",null)); assertEquals(a.id,repo.profilePreferences.userId)
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { BackupTransfer.restore(repo,it,emptySet(),overwriteProfiles=true) }
        assertEquals("A",repo.users.value.first { it.id==a.id }.name); assertEquals("dark",repo.profilePreferences.forUser(a.id).getString("theme",null))
        assertFalse(repo.users.value.first { it.id==b.id }.available)
    }
    @Test fun oldBackupCannotClearAClaimedOwnerAndExplicitOwnerConflictsAbort()=runBlocking(Dispatchers.IO) {
        val a=person("A"); val b=person("B"); val old=Session(status="stopped",elapsedMs=1000); repo.save(old)
        repo.changeOwners(listOf(old.id),a.id,mapOf(old.id to null))
        BackupTransfer.stage(context,repo,Exchange.sessions(listOf(old)).byteInputStream()).use { assertEquals(0L,it.preview.conflicts); BackupTransfer.restore(repo,it,emptySet()) }
        assertEquals(a.id,repo.history.session(old.id)!!.ownerUserId)
        val changed=repo.history.session(old.id)!!.copy(ownerUserId=b.id)
        BackupTransfer.stage(context,repo,Exchange.sessions(listOf(changed)).byteInputStream()).use { assertEquals(1L,it.preview.conflicts) }
    }
    @Test fun identicalFiltersDrivePagesOverviewAndExportAcrossOwnersAndSources()=runBlocking {
        val a=person("A"); val b=person("B")
        repeat(135) { i -> repo.save(record(if(i%2==0) a else b).copy(machine=if(i%3==0) Machine.BIKE else Machine.ROWER,demo=i%5==0)) }
        val query=HistoryQuery(owner=a.id,machine=Machine.ROWER.name,source=1)
        val ids=repo.history.reportIds(query); val pages=mutableListOf<String>()
        var offset=0
        do { val page=repo.history.page(query,offset,10); pages+=page.rows.map { it.id }; offset+=page.rows.size } while(offset<page.count)
        assertEquals(ids,pages); assertEquals(ids.size,repo.history.overview(query).count)
        assertTrue(ids.all { id -> repo.history.session(id)!!.let { it.ownerUserId==a.id && it.machine==Machine.ROWER && !it.demo } })
    }
}
