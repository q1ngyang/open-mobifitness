package org.openmobifitness.app

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.ble.*
import org.openmobifitness.app.data.saveUser
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Real controller, parser and Room path; virtual time and synthetic GATT only.
 * Advance the BLE scheduler explicitly: waiting for real Room I/O must not auto-skip its timers. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[ResistanceGattFixture.Gatt::class])
class AutomaticResistanceTest {
    private lateinit var c: Controller
    private lateinit var fixture: ResistanceGattFixture
    private lateinit var scheduler: TestCoroutineScheduler
    private suspend fun setup(testScheduler: TestCoroutineScheduler,protocol: Protocol=Protocol.V1,name: String="MB-EP-test") {
        scheduler=testScheduler; ResistanceGattFixture.Gatt.reset()
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("openmobi.db"); app.getSharedPreferences("preferences",0).edit().clear().commit()
        c=Controller(app,CoroutineScope(SupervisorJob()+UnconfinedTestDispatcher(scheduler)),{ scheduler.currentTime+10_000 },false)
        c.state.first { it.ready }
        val user=UserProfile(name="Resistance QA")
        c.repo.saveUser(user); c.switchUser(user.id).join(); c.setIdentityPolicy(IdentityPolicy.REMEMBER)
        ReflectionHelpers.setField(c.ble,"operationGapMs",0L)
        fixture=ResistanceGattFixture(c.ble).apply { install(protocol,name) }
        if(name.startsWith("AT-R")) fixture.receive(ResistanceGattFixture.atrFrame()) else fixture.v1(3)
        c.tick()
    }
    @After fun cleanup() {
        if(::c.isInitialized) { c.ble.disconnect(); c.heart.disconnect(); c.scope.cancel(); c.repo.db.close() }
        ResistanceGattFixture.Gatt.reset()
    }
    private suspend fun begin(vararg steps: Step) {
        c.select(Workout(title="Synthetic plan",machine=c.displayMachine(),ownerUserId=c.currentUser.value!!.id,steps=steps.toList()))
        c.start().join(); assertNotNull(c.state.value.session); assertTrue(c.state.value.automatic)
    }
    private fun advance(ms: Long) { scheduler.advanceTimeBy(ms); scheduler.runCurrent() }
    private suspend fun tick(ms: Long=1000) { advance(ms); c.tick() }
    private fun targets()=ResistanceGattFixture.Gatt.writes.filter { it.first=="ffe3" }.map { it.second[5].toInt() and 255 }

    @Test fun silenceAndZeroFramesDoNotRepeatAnAutomaticStepAndLateMatchContinuesOneLevelAtATime()=runBlocking {
        setup(TestCoroutineScheduler()); begin(Step(target=300.0,resistancePercent=100)); tick()
        assertEquals(listOf(4),targets())
        repeat(20) { fixture.v1(0); tick() }
        assertEquals(listOf(4),targets()); assertTrue(c.ble.state.value.controlTimedOut)
        assertEquals(3.0,c.controlResistance()!!,0.0)
        fixture.v1(4); tick()
        assertEquals(listOf(4,5),targets()); assertEquals(4.0,c.controlResistance()!!,0.0)
        fixture.v1(5); tick(); assertEquals(listOf(4,5),targets()) // Keep the 2 s pacing.
        tick(); assertEquals(listOf(4,5,6),targets())
    }
    @Test fun freshNonmatchingFeedbackPermitsOneNewAttemptButSilenceDoesNotKeepRetrying()=runBlocking {
        setup(TestCoroutineScheduler()); begin(Step(target=300.0,resistancePercent=100)); tick(); tick(6000)
        assertEquals(listOf(4),targets())
        fixture.v1(3); tick(); assertEquals(listOf(4,4),targets())
        repeat(20) { tick() }; assertEquals(listOf(4,4),targets())
        fixture.v1(4); tick(); assertEquals(listOf(4,4,5),targets())
    }
    @Test fun changedStageReplacesPendingGoalAndDoesNotWaitForOrReplayOldStage()=runBlocking {
        setup(TestCoroutineScheduler()); begin(Step(target=3.0,resistancePercent=100),Step(target=300.0,resistancePercent=0))
        tick(); val first=c.ble.state.value.resistanceControl!!.id
        tick(2000)
        assertEquals(1,c.state.value.stage); assertEquals(listOf(4,2),targets())
        assertTrue(c.ble.state.value.resistanceControl!!.id>first)
        tick(3000) // Old stage's five-second timer must not mark the new target unconfirmed.
        assertFalse(c.ble.state.value.controlTimedOut)
        fixture.v1(2); tick(); assertEquals(listOf(4,2,1),targets())
    }
    @Test fun manualAdjustmentAfterTimeoutUsesLatestTargetAndStopsAutomaticControl()=runBlocking {
        setup(TestCoroutineScheduler()); begin(Step(target=300.0,resistancePercent=100)); tick(); tick(6000)
        c.adjust(1); scheduler.runCurrent()
        assertEquals(listOf(4,5),targets()); assertFalse(c.state.value.automatic)
        assertEquals(3.0,c.controlResistance()!!,0.0) // Requested 5 is never a measurement.
        repeat(8) { tick() }; assertEquals(listOf(4,5),targets())
        fixture.v1(5); c.resumeAutomatic(); tick()
        assertEquals(listOf(4,5,6),targets())
    }
    @Test fun rapidManualReplacementsAndExitCancelQueuedSameValueRequests()=runBlocking {
        setup(TestCoroutineScheduler()); c.enterControl().join()
        c.adjustTo(4.0); scheduler.runCurrent(); assertEquals(listOf(4),targets())
        c.adjustTo(7.0); advance(300); c.adjustTo(7.0); advance(400)
        assertEquals(listOf(4,7),targets())
        assertNull(c.state.value.pendingResistance)
        advance(6000); assertTrue(c.ble.state.value.controlTimedOut)
        c.adjustTo(8.0); scheduler.runCurrent(); assertEquals(listOf(4,7,8),targets())
        c.adjustTo(9.0); c.exitControl(); advance(10_000)
        assertEquals(listOf(4,7,8),targets()); assertNull(c.ble.state.value.resistanceControl)
    }
    @Test fun writeOnlyAtrAutomaticStepsUseCommandedLevelWithoutFabricatingMeasuredResistance()=runBlocking {
        setup(TestCoroutineScheduler(),name="AT-R-test"); begin(Step(target=300.0,resistancePercent=100))
        tick(); tick(2000); tick(2000)
        assertEquals(listOf(2,3,4),targets())
        assertNull(c.ble.state.value.metrics.resistance); assertNull(c.ble.state.value.requested)
        assertEquals(4.0,c.ble.state.value.commandedResistance!!,0.0)
        assertFalse(c.ble.state.value.controlTimedOut)
    }
    @Test fun stageTransitionDoesNotCancelAManualOverride()=runBlocking {
        setup(TestCoroutineScheduler()); begin(Step(target=3.0,resistancePercent=100),Step(target=300.0,resistancePercent=0))
        tick(); c.adjustTo(7.0); advance(700)
        val manual=c.ble.state.value.resistanceControl!!.id
        tick(1300)
        assertEquals(1,c.state.value.stage); assertFalse(c.state.value.automatic)
        assertEquals(listOf(4,7),targets()); assertEquals(manual,c.ble.state.value.resistanceControl!!.id)
        tick(6000); assertTrue(c.ble.state.value.controlTimedOut)
        fixture.v1(7); tick(); assertFalse(c.ble.state.value.controlTimedOut)
        assertEquals(listOf(4,7),targets())
    }
}
