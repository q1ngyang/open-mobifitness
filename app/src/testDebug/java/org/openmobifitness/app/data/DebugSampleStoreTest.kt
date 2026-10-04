package org.openmobifitness.app.data

import android.app.Application
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.core.*
import java.time.*
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class DebugSampleStoreTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var repo: Repository
    private var anchor=Instant.parse("2026-10-04T11:31:00Z")
    private lateinit var store: DebugSampleStore
    private val names=listOf("Demo A","Demo B","Demo C","Demo D")
    @Before fun setup() {
        context.deleteDatabase("openmobi.db"); context.getSharedPreferences("debug_sample_dataset",0).edit().clear().commit(); context.getSharedPreferences("preferences",0).edit().clear().commit()
        repo=Repository(context); store=DebugSampleStore(repo,{ anchor },{ ZoneId.of("Asia/Shanghai") })
    }
    @After fun close() { repo.db.close() }
    @Test fun loadIsIdempotentRepairsOnlyMissingRowsAndBackupUsesFormalFormats()=runBlocking(Dispatchers.IO) {
        assertEquals(780,store.load(names).sessions); val first=repo.db.records().session(DebugDataset.entries.first().id)!!.model(); val count=repo.db.records().sampleCount()
        repo.saveUser(repo.users.value.first().copy(name="Changed locally"))
        repo.deleteSession(DebugDataset.entries.last().id); anchor=anchor.plusSeconds(86400*40)
        assertEquals(780,store.load(names).sessions); assertEquals(count,repo.db.records().sampleCount()); assertEquals(first,repo.history.session(first.id)); assertEquals("Changed locally",repo.users.value.first().name)
        val bytes=ByteArrayOutputStream(); BackupTransfer.export(repo,bytes,null)
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { staged -> assertEquals(0L,staged.preview.conflicts); assertEquals(0L,BackupTransfer.restore(repo,staged,emptySet())) }
        val query=HistoryQuery(owner=DebugDataset.userIds[0],machine=Machine.ROWER.name,source=2)
        val ids=repo.history.reportIds(query); assertEquals(ids.size,repo.history.overview(query).count); assertEquals(ids.size,repo.history.page(query).count)
        assertEquals(0,repo.history.page(query.copy(source=1)).count)
    }
    @Test fun injectedFailureAndNonSampleIdCollisionRollBackEveryInsertedRow()=runBlocking(Dispatchers.IO) {
        try { store.load(names,beforeInsert={ if(it==5) error("injected") }); fail() } catch(_: IllegalStateException) { }
        assertEquals(0L,repo.db.records().sessionCount()); assertTrue(repo.db.records().users().isEmpty())
        val collision=Session(id=DebugDataset.entries[7].id,status="stopped",demo=false); repo.save(collision)
        try { store.load(names); fail() } catch(e: IllegalArgumentException) { assertEquals("debug_sample_conflict",e.message) }
        assertEquals(1L,repo.db.records().sessionCount()); assertTrue(repo.db.records().users().isEmpty()); assertEquals(collision,repo.history.session(collision.id))
    }
    @Test fun cleanupKeepsRealAndOtherDemoRecordsAndDoesNotReviveUserRemovedProfiles()=runBlocking(Dispatchers.IO) {
        store.load(names); val a=repo.users.value.first { it.id==DebugDataset.userIds[0] }; val b=repo.users.value.first { it.id==DebugDataset.userIds[1] }
        val real=Session(ownerUserId=a.id,startedUserId=a.id,startedUserName=a.name,identityVersion=1,status="stopped",demo=false)
        val other=real.copy(id=java.util.UUID.randomUUID().toString(),demo=true,protocol=Protocol.DEMO)
        repo.save(real); repo.save(other)
        repo.removeUser(repo.removalPreview(b.id),false,false)
        repo.profilePreferences.select(DebugDataset.userIds[2])
        val preview=store.preview(); assertTrue(a.id in preview.keptUsers); assertFalse(b.id in preview.removeUsers)
        assertEquals(0,store.clear(preview).sessions); assertEquals(2L,repo.db.records().sessionCount()); assertEquals(real,repo.history.session(real.id)); assertEquals(other,repo.history.session(other.id)); assertNull(repo.profilePreferences.userId)
        val oldAnchor=anchor; anchor=anchor.plusSeconds(86400*40); store.load(names)
        assertTrue(repo.users.value.first { it.id==a.id }.available); assertFalse(repo.users.value.first { it.id==b.id }.available); assertTrue(repo.users.value.first { it.id==DebugDataset.userIds[2] }.available)
        assertTrue(repo.db.records().session(DebugDataset.entries.first().id)!!.start!=DebugDataset.fixture(DebugDataset.entries.first(),oldAnchor,ZoneId.of("Asia/Shanghai"),names).session.start)
    }
    @Test fun previewRechecksOutsideReferencesAndActiveRecordingBlocksStore()=runBlocking(Dispatchers.IO) {
        store.load(names); val preview=store.preview(); val a=repo.users.value.first { it.id==DebugDataset.userIds[0] }
        repo.save(Session(ownerUserId=a.id,status="stopped"))
        try { store.clear(preview); fail() } catch(e: IllegalArgumentException) { assertEquals("removal_preview_changed",e.message) }
        assertEquals(780,store.info().sessions)
        repo.save(Session(ownerUserId=a.id,status="active"))
        try { store.load(names); fail() } catch(e: IllegalStateException) { assertEquals("identity_locked",e.message) }
        assertEquals(780,store.info().sessions)
    }
}
