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
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class BackupTransferTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val prefs get()=context.getSharedPreferences("preferences",0)
    private lateinit var repo: Repository
    @Before fun setup() { context.deleteDatabase("openmobi.db"); prefs.edit().clear().commit(); repo=Repository(context) }
    @After fun close() { repo.db.close() }
    private fun resetRecords() { repo.db.close(); context.deleteDatabase("openmobi.db"); repo=Repository(context) }
    private fun zip(entries: Map<String,String>): ByteArray=ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> entries.forEach { (name,value) -> z.putNextEntry(ZipEntry(name)); z.write(value.toByteArray()); z.closeEntry() } } }.toByteArray()

    @Test fun largeBackupBeyondOldByteAndRowLimitsRoundTripsWithBoundedChunks()=runBlocking(Dispatchers.IO) {
        val records=360_001
        val s=Session(elapsedMs=records*1000L,status="completed",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,device="Large backup fixture")
        val random=java.util.Random(20261003)
        fun n(max: Double)=random.nextDouble()*max
        repo.db.runInTransaction {
            repo.db.records().session(s.row())
            repeat(records) { i -> repo.db.records().sample(Sample(s.id,(i+1)*1000L,Metrics(cadence=n(200.0),resistance=n(24.0),speedMps=n(10.0),distanceM=n(100000.0),heartBpm=random.nextInt(200),powerW=n(500.0),strokes=random.nextInt(100000),caloriesKcal=n(10000.0),inclinePercent=n(20.0),strideM=n(2.0),forceN=n(500.0),stepRate=n(200.0),stepCount=random.nextInt(100000),targetCadence=n(200.0),powerEstimated=true,jumpCount=random.nextInt(100000),continuousJumps=random.nextInt(65536),jumpInterruptions=random.nextInt(1000),repetitions=random.nextInt(100000),loadKg=n(100.0),deviceDurationSec=random.nextInt(65536),dumbbellFewActions=random.nextInt(256),dumbbellActionNumber=random.nextInt(256))).row()) }
        }
        prefs.edit().putString("theme","dark").putBoolean("imperial",true).putInt("target_cadence",27).commit()
        val digest=digest(repo.db)
        val evidence=File(requireNotNull(System.getenv("DEV_TEMP_BASE")),"work/open-mobifitness-v020/qa/large-backup-test").apply { mkdirs() }
        val file=File(evidence,"360001-samples-v7.zip")
        file.outputStream().use { BackupTransfer.export(repo,it,PortablePreferences.export(prefs)) }
        assertTrue("compressed backup must also exceed old 32 MiB limit: ${file.length()}",file.length()>32*1024*1024)
        resetRecords(); prefs.edit().putString("theme","light").putBoolean("imperial",false).commit()
        file.inputStream().use { BackupTransfer.stage(context,repo,it) }.use { staged ->
            assertEquals(records.toLong(),staged.preview.samples); assertEquals(0L,staged.preview.conflicts)
            BackupTransfer.restore(repo,staged,setOf(PreferenceGroup.APPEARANCE))
        }
        assertEquals(records.toLong(),repo.db.records().sampleCount()); assertEquals(digest,digest(repo.db)); assertEquals("dark",prefs.getString("theme",null)); assertEquals(27,prefs.getInt("target_cadence",0))
        file.inputStream().use { BackupTransfer.stage(context,repo,it) }.use { staged ->
            assertEquals(0L,staged.preview.added); assertEquals(1L,staged.preview.duplicates); assertEquals(0L,BackupTransfer.restore(repo,staged,emptySet()))
        }
        File(evidence,"result.txt").writeText("samples=$records\ncompressed_bytes=${file.length()}\nsample_digest=$digest\nroundtrip=passed\nrepeat_import=skipped\npreferences=restored\n")
    }
    private fun digest(db: MobiDatabase): String {
        val hash=MessageDigest.getInstance("SHA-256"); var id=""; var time=-1L
        while(true) { val page=db.records().samplePage(id,time,2000); if(page.isEmpty()) break; hash.update(Exchange.samples(page.map { it.model() }).toByteArray()); id=page.last().sessionId; time=page.last().elapsedMs }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    @Test fun legacyArchivesOneThroughSixAndMultilineCsvStillRestore()=runBlocking(Dispatchers.IO) {
        val s=Session(status="stopped",device="First,\nSecond \"line\"",elapsedMs=1000)
        for(version in 1..6) {
            val bytes=zip(mapOf("manifest.txt" to "${if(version<3) "OpenMobi" else "OpenMOBI"} backup $version\n","sessions.csv" to Exchange.sessions(listOf(s)),"samples.csv" to Exchange.samples(listOf(Sample(s.id,1000,Metrics(resistance=4.0)))),"workouts.csv" to Exchange.workouts(emptyList())))
            BackupTransfer.stage(context,repo,bytes.inputStream()).use { BackupTransfer.restore(repo,it,emptySet()) }
            assertEquals(s,repo.archive().sessions.single())
        }
        assertEquals(1L,repo.db.records().sampleCount())
    }
    @Test fun conflictAndCancellationLeaveExistingDatabaseUntouched()=runBlocking(Dispatchers.IO) {
        val s=Session(status="stopped",elapsedMs=10000)
        val sample=Sample(s.id,1000,Metrics(resistance=4.0))
        val bytes=ByteArrayOutputStream(); Files.backup(bytes,Archive(listOf(s),listOf(sample)))
        val staged=BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream())
        repo.save(s.copy(distanceM=9.0)) // Change after preview: commit rechecks.
        try { BackupTransfer.restore(repo,staged,emptySet()); fail("must abort") } catch(_: IllegalArgumentException) { }
        assertEquals(0L,repo.db.records().sampleCount()); staged.close()
        resetRecords()
        val next=BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream())
        lateinit var task: Job
        task=launch(start=CoroutineStart.LAZY) { BackupTransfer.restore(repo,next,emptySet(),{ task.cancel() }) }
        task.start(); task.join()
        assertEquals(0L,repo.db.records().sessionCount()); assertEquals(0L,repo.db.records().sampleCount()); next.close()
    }
    @Test fun durablePreferenceJournalRecoversAfterCommitInterruption()=runBlocking(Dispatchers.IO) {
        prefs.edit().putString("theme","dark").putBoolean("auto_floating",false).commit()
        val portable=PortablePreferences.select(PortablePreferences.export(prefs),setOf(PreferenceGroup.APPEARANCE))
        prefs.edit().putString("theme","light").putBoolean("auto_floating",true).commit()
        val s=Session(status="stopped")
        repo.db.runInTransaction { repo.db.records().session(s.row()); repo.db.records().pendingPreferences(PreferenceRestoreRow().apply { json=portable }) }
        repo.db.close(); repo=Repository(context); repo.load()
        assertEquals(s,repo.archive().sessions.single()); assertEquals("dark",prefs.getString("theme",null)); assertTrue(prefs.getBoolean("auto_floating",false)); assertNull(repo.db.records().pendingPreferences())
    }
    @Test fun unsafeEntriesMissingChunksAndMalformedCsvAreRejectedBeforeCommit()=runBlocking(Dispatchers.IO) {
        val bad=listOf(zip(mapOf("../sessions.csv" to "x")),zip(mapOf("manifest.txt" to "OpenMOBI backup 7\n","sessions/1.csv" to Exchange.sessions(emptyList()))),"schema,session_id,start_utc\n\"unfinished".toByteArray(),byteArrayOf(0xff.toByte()))
        bad.forEach { bytes -> try { BackupTransfer.stage(context,repo,bytes.inputStream()).close(); fail("must reject corrupt input") } catch(_: Exception) { } }
        assertEquals(0L,repo.db.records().sessionCount()); assertEquals(0L,repo.db.records().sampleCount())
    }

    @Test fun injectedStorageExhaustionRollsBackRestoreAndExportDoesNotChangeSource()=runBlocking(Dispatchers.IO) {
        val existing=Session(status="stopped",elapsedMs=1000,device="Preserve this record")
        repo.save(existing)
        val incoming=Session(status="stopped",elapsedMs=3000)
        val archive=Archive(listOf(incoming),listOf(Sample(incoming.id,1000,Metrics(resistance=4.0)),Sample(incoming.id,2000,Metrics(resistance=8.0))))
        val bytes=ByteArrayOutputStream(); Files.backup(bytes,archive)
        // Deterministic storage-failure injection; this does not fill a real disk.
        repo.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER test_disk_full BEFORE INSERT ON samples WHEN NEW.elapsedMs = 2000 BEGIN SELECT RAISE(ABORT, 'database or disk is full (ENOSPC)'); END")
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { staged ->
            try { BackupTransfer.restore(repo,staged,emptySet()); fail("storage fault must abort restore") }
            catch(e: android.database.sqlite.SQLiteException) { assertTrue(e.message.orEmpty().contains("ENOSPC")) }
        }
        assertEquals(listOf(existing),repo.archive().sessions)
        assertEquals(0L,repo.db.records().sampleCount())
        assertNull(repo.db.records().pendingPreferences())
        val full=object: OutputStream() { override fun write(b: Int) { throw IOException("ENOSPC: destination full") } }
        try { BackupTransfer.export(repo,full,null); fail("full destination must fail") }
        catch(e: IOException) { assertTrue(e.message.orEmpty().contains("ENOSPC")) }
        assertEquals(listOf(existing),repo.archive().sessions)
    }
    @Test fun sampleMergeCrossesPagesPreservesOtherSessionsAndRechecksConflicts()=runBlocking(Dispatchers.IO) {
        val first=Session(id="00000000-0000-0000-0000-000000000001",status="stopped",elapsedMs=3001000)
        val unrelated=first.copy(id="00000000-0000-0000-0000-000000000002")
        val last=first.copy(id="00000000-0000-0000-0000-000000000003")
        val incoming=listOf(first,last).flatMap { s -> (0..3001).map { Sample(s.id,it*1000L,Metrics(resistance=(it%24+1).toDouble(),jumpCount=it)) } }
        repo.save(first); repo.save(unrelated)
        val retained=Sample(unrelated.id,1000,Metrics(resistance=7.0))
        repo.db.records().sample(retained.row())
        repo.db.records().samples(incoming.filter { it.sessionId==first.id && it.elapsedMs%2000==0L }.map { it.row() })
        val bytes=ByteArrayOutputStream(); Files.backup(bytes,Archive(listOf(first,last),incoming))
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { staged ->
            assertEquals(0L,staged.preview.conflicts)
            BackupTransfer.restore(repo,staged,emptySet())
        }
        assertEquals((incoming+retained).toSet(),repo.archive().samples.toSet())
        BackupTransfer.stage(context,repo,bytes.toByteArray().inputStream()).use { staged ->
            // A conflict introduced after preview must roll back, including
            // rows that a bulk operation has already written in the transaction.
            repo.db.openHelper.writableDatabase.execSQL("UPDATE samples SET resistance=24 WHERE sessionId=? AND elapsedMs=3000000",arrayOf(last.id))
            try { BackupTransfer.restore(repo,staged,emptySet()); fail("changed existing sample must conflict") }
            catch(_: IllegalArgumentException) { }
        }
        assertEquals(incoming.size+1L,repo.db.records().sampleCount())
        assertEquals(24.0,repo.db.records().sampleAt(last.id,3000000).resistance!!,0.0)
        assertEquals(retained,repo.db.records().sampleAt(unrelated.id,1000).model())
    }
}
