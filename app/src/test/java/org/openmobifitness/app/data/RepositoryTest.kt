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
        val s=Session(elapsedMs=2000,demo=true,status="completed")
        val sample=Sample(s.id,1000,Metrics(cadence=62.5,resistance=3.0))
        repo.save(s,sample)
        val workout=Workout(title="间歇 / Intervall",steps=listOf(Step(target=30.0,resistancePercent=20)))
        repo.saveWorkout(workout)
        val original=repo.archive()
        val out=ByteArrayOutputStream(); Files.backup(out,original)
        val restored=Files.read(out.toByteArray().inputStream())
        assertEquals(original,restored)
        assertEquals(0,repo.importArchive(restored))
        assertEquals(original,repo.archive())
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
}
