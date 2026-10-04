package org.openmobifitness.app.data

import android.app.Application
import kotlinx.coroutines.runBlocking
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
class RepositoryTest {
    private lateinit var repo: Repository
    @Before fun setup() { repo=Repository(RuntimeEnvironment.getApplication()) }
    @After fun close() { repo.db.close() }
    @Test fun backupRoundTripAndRepeatedImportAreLossless() = runBlocking {
        val s=Session(elapsedMs=2000,demo=true,status="completed",caloriesKcal=2.5,caloriesEstimated=true,weightKg=70.0,met=5.0,energyModel="legacy-v1")
        val sample=Sample(s.id,1000,Metrics(cadence=62.5,resistance=3.0,caloriesKcal=2.5,inclinePercent=4.0,forceN=10.0,strideM=0.7,stepRate=123.0,powerW=148.0,powerEstimated=true,jumpCount=50,continuousJumps=20,jumpInterruptions=2,repetitions=10,loadKg=12.5,deviceDurationSec=300,dumbbellFewActions=3,dumbbellActionNumber=10))
        repo.save(s,sample)
        val user=UserProfile(name="Test user"); repo.saveUser(user); repo.profilePreferences.select(user.id)
        val workout=Workout(machine=Machine.ELLIPTICAL,title="间歇 / Intervall",steps=listOf(Step(target=30.0,resistancePercent=20)))
        repo.saveWorkout(workout)
        val original=repo.archive()
        val out=ByteArrayOutputStream(); Files.backup(out,original)
        val restored=Files.read(out.toByteArray().inputStream())
        assertEquals(original,restored)
        assertEquals(0,repo.importArchive(restored))
        assertEquals(original,repo.archive())
    }
    @Test fun oldBrandBackupManifestStillImports() {
        for(version in 1..2) {
            val out=ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip ->
                mapOf("manifest.txt" to "OpenMobi backup $version\n","sessions.csv" to Exchange.sessions(emptyList()),"samples.csv" to Exchange.samples(emptyList()),"workouts.csv" to Exchange.workouts(emptyList())).forEach { (name,text) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            assertEquals(Archive(),Files.read(out.toByteArray().inputStream()))
        }
    }
    @Test fun conflictingImportRollsBackEarlierInserts() = runBlocking {
        val existing=Session(elapsedMs=1000,status="stopped")
        repo.save(existing)
        val new=Session(elapsedMs=2000,status="stopped")
        try { repo.importArchive(Archive(sessions=listOf(new,existing.copy(elapsedMs=3000)))); fail("Conflict must abort") }
        catch(expected: IllegalArgumentException) { }
        assertEquals(listOf(existing),repo.archive().sessions)
    }
    @Test fun processRestartRecoversWithoutResuming() = runBlocking {
        val active=Session(elapsedMs=4000)
        repo.save(active,Sample(active.id,4000,Metrics(resistance=4.0)))
        repo.load()
        assertEquals("interrupted",repo.sessions.value.single().status)
        assertEquals(4000L,repo.sessions.value.single().elapsedMs)
        assertEquals(1,repo.archive().samples.size)
    }
    @Test fun databaseUpgradeKeepsVersionsOneAndTwoHistoryAndSamples() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        for(version in 1..7) {
        repo.db.close()
        val context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("openmobi.db")
        val input=javaClass.classLoader!!.getResourceAsStream("org.openmobifitness.app.data.MobiDatabase/$version.json")!!
        val schema=org.json.JSONObject(input.bufferedReader().use { it.readText() }).getJSONObject("database")
        val id=java.util.UUID.randomUUID().toString()
        val db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("openmobi.db"),null)
        val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()) {
            val e=entities.getJSONObject(i)
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            e.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName"))) }
        }
        val queries=schema.getJSONArray("setupQueries"); for(i in 0 until queries.length()) db.execSQL(queries.getString(i))
        db.execSQL("INSERT INTO sessions (id,start,end,zone,device,machine,protocol,status,elapsedMs,distanceM,demo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",arrayOf<Any>(id,"2026-09-30T10:00:00Z","","UTC","legacy","ELLIPTICAL","DEMO","stopped",9000,25.0,1))
        db.execSQL("INSERT INTO samples (sessionId,elapsedMs,cadence) VALUES (?, ?, ?)",arrayOf<Any>(id,9000,62.0))
        if(version==6) db.execSQL("UPDATE samples SET repetitions=42,loadKg=12.5,jumpCount=800,continuousJumps=77,jumpInterruptions=3,deviceDurationSec=9,dumbbellFewActions=4,dumbbellActionNumber=5")
        db.version=version; db.close()
        repo=Repository(context)
        val restored=repo.archive()
        assertEquals(id,restored.sessions.single().id)
        assertEquals(25.0,restored.sessions.single().distanceM!!,0.0)
        assertEquals(62.0,restored.samples.single().metrics.cadence!!,0.0)
        assertNull(restored.sessions.single().caloriesKcal)
        assertFalse(restored.sessions.single().caloriesEstimated)
        assertEquals(8,repo.db.openHelper.readableDatabase.version)
        assertNull(restored.sessions.single().ownerUserId)
        assertEquals(LEGACY_USER_ID,repo.db.records().users().single().id)
        if(version==6) assertEquals(Metrics(cadence=62.0,repetitions=42,loadKg=12.5,jumpCount=800,continuousJumps=77,jumpInterruptions=3,deviceDurationSec=9,dumbbellFewActions=4,dumbbellActionNumber=5),restored.samples.single().metrics)
        else { assertNull(restored.samples.single().metrics.repetitions); assertNull(restored.samples.single().metrics.loadKg) }
        assertFalse(restored.samples.single().metrics.powerEstimated)
        }
    }

}
