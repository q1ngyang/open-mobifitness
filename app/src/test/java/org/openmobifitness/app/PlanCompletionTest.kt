package org.openmobifitness.app

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.data.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class PlanCompletionTest {
    private lateinit var c: Controller
    private var now=10000L
    private val app get()=RuntimeEnvironment.getApplication()
    private fun controller()=Controller(app,CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),{now},false)
    @Before fun setup()=runBlocking {
        app.deleteDatabase("openmobi.db");app.getSharedPreferences("preferences",0).edit().clear().commit()
        c=controller();withTimeout(5000) { c.state.first { it.ready } }
        val user=UserProfile(name="Completion QA");c.repo.saveUser(user);c.switchUser(user.id).join();c.setIdentityPolicy(IdentityPolicy.REMEMBER)
        c.ble.state.value=LinkState(name="Test bike",phase="ready",machine=Machine.BIKE,protocol=Protocol.V1)
    }
    @After fun cleanup() { c.scope.cancel();c.ble.disconnect();c.repo.db.close() }
    private suspend fun begin(vararg steps: Step) {
        c.select(Workout(title="Test plan",machine=Machine.BIKE,ownerUserId=c.currentUser.value!!.id,steps=steps.toList()))
        c.start().join();assertNotNull(c.state.value.session)
    }
    private suspend fun tick(ms: Long) { now+=ms;c.tick() }
    @Test fun deadlinePausesOnceAndWaitingTimeIsNotRecorded()=runBlocking {
        begin(Step(target=1.5),Step(target=2.0))
        tick(9000)
        val session=c.state.value.session!!
        assertEquals(3500L,session.elapsedMs);assertEquals(3500L,session.planCompletedMs)
        assertTrue(session.completionPending);assertTrue(c.state.value.paused);assertFalse(c.state.value.automatic)
        assertEquals(3500L,c.repo.archive().samples.single().elapsedMs)
        tick(30000);c.pauseResume().join();tick(10000)
        assertEquals(session,c.state.value.session)
        c.finish().join()
        val saved=c.repo.archive().sessions.single()
        assertEquals("completed",saved.status);assertFalse(saved.completionPending);assertEquals(3500L,saved.elapsedMs)
        assertEquals(saved.id,c.finishedSession.value)
    }
    @Test fun continuingKeepsOneSessionAndRebasesPausedCounterMovement()=runBlocking {
        fun sensor(distance: Double) { c.ble.state.value=c.ble.state.value.copy(metrics=Metrics(distanceM=distance),motionAt=now) }
        sensor(100.0);c.tick();begin(Step(target=2.0))
        sensor(120.0);tick(2000)
        val id=c.state.value.session!!.id
        assertEquals(20.0,c.state.value.session!!.distanceM!!,0.0)
        sensor(500.0);tick(30000);c.continueManually().join()
        assertFalse(c.state.value.completionPending);assertTrue(c.state.value.continuingManually);assertFalse(c.state.value.automatic)
        sensor(505.0);tick(1000);sensor(515.0);tick(1000)
        assertEquals(id,c.state.value.session!!.id);assertEquals(4000L,c.state.value.session!!.elapsedMs)
        assertEquals(30.0,c.state.value.session!!.distanceM!!,0.0)
        c.continueManually().join();tick(1000);assertFalse(c.state.value.completionPending)
        c.finish().join();assertEquals(1,c.repo.archive().sessions.size)
        assertEquals(2000L,c.repo.archive().sessions.single().planCompletedMs)
    }
    @Test fun pendingChoiceSurvivesControllerRestartWithoutRecordingBackgroundTime()=runBlocking {
        begin(Step(target=1.0));tick(1000)
        val id=c.state.value.session!!.id
        c.scope.cancel();c.repo.db.close();now+=60000
        c=controller();withTimeout(5000) { c.state.first { it.ready } }
        assertEquals(id,c.state.value.session!!.id);assertTrue(c.state.value.completionPending)
        assertTrue(c.state.value.paused);assertEquals(1000L,c.state.value.session!!.elapsedMs)
        assertFalse(c.canStart());c.continueManually().join();assertTrue(c.state.value.completionPending)
        c.finish().join();assertEquals(id,c.finishedSession.value)
        assertEquals(1000L,c.repo.archive().sessions.single().elapsedMs)
    }
    @Test fun saveFailureLeavesTheChoiceAndSummaryAvailableForRetry()=runBlocking {
        begin(Step(target=1.0));tick(1000)
        val session=c.state.value.session
        c.repo.db.close();c.finish().join()
        assertTrue(c.state.value.completionPending);assertTrue(c.state.value.paused)
        assertFalse(c.state.value.finishing);assertEquals(session,c.state.value.session)
        assertNull(c.finishedSession.value);assertEquals(R.string.storage_failed,c.state.value.error)
    }
    @Test fun sensorStagesFinishOnlyOnMeasuredTargetAndFreeExerciseHasNoCompletionChoice()=runBlocking {
        c.ble.state.value=c.ble.state.value.copy(metrics=Metrics(distanceM=0.0));c.tick()
        begin(Step(Condition.DISTANCE,10.0));tick(30000);assertFalse(c.state.value.completionPending)
        c.ble.state.value=c.ble.state.value.copy(metrics=Metrics(distanceM=10.0));tick(1000)
        assertTrue(c.state.value.completionPending);c.finish().join();c.start().join();tick(100000)
        assertFalse(c.state.value.completionPending);assertFalse(c.state.value.done)
    }
}
