package org.openmobifitness.app

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.ble.FoundDevice
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Controller lifecycle with synthetic sensor inputs; radio/DEX evidence is separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ControlOnlyTest {
    private lateinit var c: Controller
    private var now=10_000L
    @Before fun setup()=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("openmobi.db")
        app.getSharedPreferences("preferences",0).edit().clear().commit()
        c=Controller(app,CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),{ now },false)
        withTimeout(5000) { c.state.first { it.ready } }
        c.ble.state.value=LinkState(name="MB-EP",address="00:11:22:33:44:55",phase="ready",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,range=ResistanceRange(1.0,24.0),writable=true)
        sensor(1000.0)
        c.tick()
    }
    private fun sensor(distance: Double) {
        c.ble.state.value=c.ble.state.value.copy(metrics=Metrics(cadence=60.0,resistance=12.0,distanceM=distance,heartBpm=128),motionAt=now,heartAt=now)
    }
    private suspend fun tick(distance: Double,delta: Long=1000) { now+=delta; sensor(distance); c.tick() }
    @After fun cleanup() { if(::c.isInitialized) { c.scope.cancel(); c.ble.disconnect(); c.heart.disconnect(); c.repo.db.close() } }

    @Test fun controlHasLiveReadingsWithoutSessionSamplesOrHistory()=runBlocking {
        val connection=c.ble.connectionId
        c.enterControl().join()
        repeat(20) { tick(1100.0+it*25) }
        assertEquals(UseMode.CONTROL_ONLY,c.state.value.mode)
        assertNull(c.state.value.session)
        assertEquals(60.0,c.state.value.metrics.cadence!!,0.0)
        assertEquals(12.0,c.controlResistance()!!,0.0)
        assertEquals(connection,c.ble.connectionId)
        assertTrue(c.repo.archive().sessions.isEmpty())
        assertTrue(c.repo.archive().samples.isEmpty())
        assertEquals(c.app.getString(R.string.not_recording),c.app.reading(MetricId.TIME,c).value)
        assertEquals("—",c.app.reading(MetricId.DISTANCE,c).value)
    }
    @Test fun recordingStartsAtNewCounterBaselineAndFinishesBackInControl()=runBlocking {
        c.enterControl().join(); tick(1100.0)
        val connection=c.ble.connectionId
        c.start().join(); tick(1125.0)
        assertEquals(25.0,c.state.value.session!!.distanceM!!,0.0)
        c.pauseResume().join(); tick(1500.0,5000)
        c.pauseResume().join(); tick(1505.0); tick(1520.0)
        assertEquals(40.0,c.state.value.session!!.distanceM!!,0.0)
        c.finish().join()
        assertEquals(UseMode.CONTROL_ONLY,c.state.value.mode)
        assertNull(c.state.value.selected)
        tick(1600.0)
        val archive=c.repo.archive()
        assertEquals(1,archive.sessions.size)
        assertEquals(3,archive.samples.size)
        assertEquals(3000L,archive.sessions.single().elapsedMs)
        assertEquals(40.0,archive.sessions.single().distanceM!!,0.0)
        assertEquals(12.0,c.controlResistance()!!,0.0)
        assertEquals(connection,c.ble.connectionId)
    }
    @Test fun reconnectDoesNotResumeOrChargeOfflineCounters()=runBlocking {
        c.enterControl().join(); c.start().join(); tick(1010.0)
        c.disconnectEquipment(); now+=30_000; c.tick()
        assertTrue(c.state.value.paused)
        val elapsed=c.state.value.session!!.elapsedMs
        c.ble.state.value=c.ble.state.value.copy(phase="ready",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,range=ResistanceRange(1.0,24.0),writable=true)
        tick(4000.0)
        assertTrue(c.state.value.paused)
        assertEquals(elapsed,c.state.value.session!!.elapsedMs)
        assertNull(c.state.value.error)
        c.pauseResume().join(); tick(4020.0); tick(4030.0)
        assertEquals(20.0,c.state.value.session!!.distanceM!!,0.0)
    }
    @Test fun differentPrimaryCannotEnterAnExistingRecordEvenThroughController()=runBlocking {
        c.start().join()
        val connection=c.ble.connectionId
        c.connect(FoundDevice("00:11:22:33:44:66","MOBI-R",0))
        assertEquals(connection,c.ble.connectionId)
        assertEquals("00:11:22:33:44:55",c.ble.state.value.address)
        assertEquals(R.string.workout_machine_changed,c.state.value.error)
    }
    @Test fun optionalHeartSensorKeepsPrimaryControlAndRecordingIndependent()=runBlocking {
        c.enterControl().join()
        val primary=c.ble.connectionId
        c.heart.state.value=LinkState(name="HR accessory",address="00:11:22:33:44:66",phase="ready",machine=Machine.HEART,metrics=Metrics(heartBpm=151),heartAt=now)
        tick(1100.0)
        assertEquals(151,c.state.value.metrics.heartBpm)
        assertEquals(Machine.ELLIPTICAL,c.displayMachine())
        assertEquals(primary,c.ble.connectionId)
        assertTrue(c.local.devices.value.any { it.heart && it.machine==Machine.HEART })
        assertTrue(c.repo.archive().samples.isEmpty())
        c.start().join(); tick(1110.0)
        val id=c.state.value.session!!.id
        assertEquals(151,c.repo.archive().samples.single().metrics.heartBpm)
        c.heart.disconnect(); tick(1120.0)
        assertEquals(128,c.state.value.metrics.heartBpm)
        assertEquals(id,c.state.value.session!!.id)
        assertFalse(c.state.value.paused)
        assertEquals(primary,c.ble.connectionId)
        c.heart.state.value=LinkState(phase="ready",machine=Machine.HEART,metrics=Metrics(heartBpm=151),heartAt=now)
        c.disconnectEquipment(); now+=1000; c.tick()
        assertTrue(c.state.value.paused)
        assertEquals("ready",c.heart.state.value.phase)
        assertEquals(2,c.repo.archive().samples.size)
    }
    @Test fun hintsRequireExplicitUseAndPauseOrMuteStopsNotifications()=runBlocking {
        c.local.saveHints(HintPreferences(frequency=mapOf(Machine.ELLIPTICAL to PersonalRange(true,70.0,80.0))))
        repeat(7) { tick(1000.0) }
        assertEquals(0L,c.state.value.hintEvent)
        assertEquals(RangePosition.UNAVAILABLE,c.state.value.frequencyHint)
        c.enterControl().join()
        repeat(7) { tick(1000.0) }
        assertEquals(RangePosition.BELOW,c.state.value.frequencyHint)
        val notified=c.state.value.hintEvent
        assertTrue(notified>0)
        c.muteHints()
        repeat(35) { tick(1000.0) }
        assertEquals(notified,c.state.value.hintEvent)
        assertEquals(RangePosition.BELOW,c.state.value.frequencyHint)
        c.start().join(); c.pauseResume().join(); tick(1000.0)
        assertEquals(RangePosition.UNAVAILABLE,c.state.value.frequencyHint)
        assertEquals(notified,c.state.value.hintEvent)
    }
    @Test fun exitInvalidatesControlWithoutCreatingARecord()=runBlocking {
        c.enterControl().join()
        val generation=c.ble.controlGeneration
        c.adjustTo(16.0)
        c.exitControl()
        assertEquals(UseMode.IDLE,c.state.value.mode)
        assertNull(c.state.value.pendingResistance)
        assertTrue(c.ble.controlGeneration>generation)
        delay(750)
        assertNull(c.ble.state.value.requested)
        assertTrue(c.repo.archive().sessions.isEmpty())
    }
    @Test fun serviceLossOnlyPausesTheMatchingRecordAndNeverResumesIt()=runBlocking {
        c.start().join()
        val first=c.state.value.session!!.id
        c.pauseForServiceLoss(first).join()
        assertTrue(c.state.value.paused)
        c.pauseForServiceLoss(first).join()
        assertTrue(c.state.value.paused)
        c.finish().join(); c.start().join()
        assertNotEquals(first,c.state.value.session!!.id)
        c.pauseForServiceLoss(first).join()
        assertFalse(c.state.value.paused)
        c.pauseForServiceLoss(c.state.value.session!!.id).join()
        assertTrue(c.state.value.paused)
    }
}
