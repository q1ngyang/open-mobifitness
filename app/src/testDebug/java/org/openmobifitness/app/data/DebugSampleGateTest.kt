package org.openmobifitness.app.data

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.app.*
import org.openmobifitness.core.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class DebugSampleGateTest {
    @Test fun successfulLoadReportsPercentInsteadOfRecordIndex()=runBlocking {
        val app=RuntimeEnvironment.getApplication(); app.deleteDatabase("openmobi.db")
        app.getSharedPreferences("preferences",0).edit().clear().commit(); app.getSharedPreferences("debug_sample_dataset",0).edit().clear().commit()
        val c=Controller(app,CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),tickAutomatically=false)
        try {
            withTimeout(10000) { c.state.first { it.ready } }
            val samples=DebugSamples(c); val observed=mutableListOf<Int>()
            val collection=launch(Dispatchers.Unconfined) { samples.progress.collect { it?.let(observed::add) } }
            try { assertEquals(780,samples.load(listOf("Demo A","Demo B","Demo C","Demo D")).sessions) }
            finally { collection.cancel() }
            assertTrue(observed.isNotEmpty()); assertTrue(observed.all { it in 0..100 }); assertEquals(100,observed.last())
            assertNull(samples.progress.value); assertFalse(c.repo.maintenance.value)
        } finally { c.scope.cancel(); c.repo.db.close() }
    }
    @Test fun pendingConfirmationPausedSessionAndRestoreGateExcludeLoads()=runBlocking {
        val app=RuntimeEnvironment.getApplication(); app.deleteDatabase("openmobi.db"); app.getSharedPreferences("preferences",0).edit().clear().commit()
        val c=Controller(app,CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),tickAutomatically=false)
        try {
            withTimeout(10000) { c.state.first { it.ready } }
            val samples=DebugSamples(c); val names=listOf("Demo A","Demo B","Demo C","Demo D")
            suspend fun rejected() { try { samples.load(names); fail() } catch(e: IllegalStateException) { assertEquals("identity_locked",e.message) }; assertEquals(0,samples.info().sessions) }
            c.pendingStart.value=StartRequest(generation=0,connection=0,machine=Machine.BIKE,workoutId=null); rejected(); c.pendingStart.value=null
            c.state.value=c.state.value.copy(session=Session(),paused=true); rejected(); c.state.value=c.state.value.copy(session=null,paused=false)
            c.repo.maintenance.value=true; rejected(); c.repo.maintenance.value=false
        } finally { c.scope.cancel(); c.repo.db.close() }
    }
}
