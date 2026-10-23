package org.openmobifitness.app.service

import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.core.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Real foreground-service lifecycle with synthetic link states; no radio claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=OpenMobiApp::class)
class BackgroundConnectionTest {
    private lateinit var owner: ServiceController<WorkoutService>
    private lateinit var service: WorkoutService
    private val c get()=(RuntimeEnvironment.getApplication() as OpenMobiApp).controller
    private val wake get()=ReflectionHelpers.getField<PowerManager.WakeLock>(service,"wakeLock")
    private var destroyed=false
    @Before fun setup() { owner=Robolectric.buildService(WorkoutService::class.java).create();service=owner.get() }
    private fun start(action: String?=null) {
        service.onStartCommand(Intent(service,WorkoutService::class.java).setAction(action),0,1)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun cleanup() {
        if(!destroyed) owner.destroy()
        c.scope.cancel();shadowOf(Looper.getMainLooper()).idle();c.repo.db.close()
    }
    @Test fun everyEquipmentFamilyKeepsSetupAndIdleTelemetryAwake() {
        for(machine in listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL,Machine.JUMP_ROPE,Machine.DUMBBELL)) {
            for(phase in listOf("searching","connecting","discovering","initializing","awaiting_data","ready")) {
                c.ble.state.value=LinkState(machine=machine,phase=phase)
                start()
                assertTrue("$machine $phase",wake.isHeld)
                assertTrue(c.serviceStarted)
            }
        }
        c.ble.disconnect();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(wake.isHeld)
    }
    @Test fun heartOnlyConnectionPreventsIdleShutdownAndReleasesWakeWhenDisconnected() {
        c.heart.state.value=LinkState(machine=Machine.HEART,phase="ready")
        start(WorkoutService.STOP_IF_IDLE)
        assertTrue(wake.isHeld);assertFalse(shadowOf(service).isStoppedBySelf)
        c.heart.disconnect();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(wake.isHeld)
        start(WorkoutService.STOP_IF_IDLE);assertTrue(shadowOf(service).isStoppedBySelf)
    }
    @Test fun finishingARecordDoesNotStopServiceWhileEquipmentRemainsConnected() {
        c.ble.state.value=LinkState(machine=Machine.BIKE,phase="ready")
        c.state.value=ExerciseState() // Controller.finish() deliberately retains the links.
        start(WorkoutService.STOP_IF_IDLE)
        assertTrue(wake.isHeld);assertFalse(shadowOf(service).isStoppedBySelf)
    }
    @Test fun screenOffAndPowerSaveEventsRetainConnectionOwnership() {
        c.ble.state.value=LinkState(machine=Machine.ROWER,phase="ready")
        start()
        shadowOf(service.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        service.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(wake.isHeld);assertTrue(c.serviceStarted)
        assertTrue(c.connectionServiceStatus.contains("foreground=true"))
    }
    @Test fun unexpectedServiceDestructionPausesRecordAndClosesBothLinks() {
        c.ble.state.value=LinkState(machine=Machine.TREADMILL,phase="ready")
        c.heart.state.value=LinkState(machine=Machine.HEART,phase="ready")
        c.state.value=ExerciseState(session=Session(machine=Machine.TREADMILL))
        start();owner.destroy();destroyed=true
        assertFalse(wake.isHeld);assertFalse(c.serviceStarted)
        assertEquals("disconnected",c.ble.state.value.phase)
        assertEquals("disconnected",c.heart.state.value.phase)
        assertTrue(c.state.value.paused);assertNotNull(c.state.value.session)
    }
    @Test fun foregroundServiceRejectionClosesLinksAndExplainsTheFailure() {
        c.ble.state.value=LinkState(machine=Machine.DUMBBELL,phase="initializing")
        c.heart.state.value=LinkState(machine=Machine.HEART,phase="searching")
        shadowOf(service).setThrowInStartForeground(SecurityException("test rejection"))
        start()
        assertFalse(wake.isHeld);assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals("disconnected",c.ble.state.value.phase)
        assertEquals("disconnected",c.heart.state.value.phase)
        assertEquals(R.string.connection_background_unavailable,c.state.value.error)
    }
    @Test fun demoWithoutRealLinksDoesNotAcquireAWakeLock() {
        c.state.value=ExerciseState(demo=true)
        start();assertFalse(wake.isHeld)
    }
}
