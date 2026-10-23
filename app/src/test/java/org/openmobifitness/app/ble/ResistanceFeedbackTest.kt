package org.openmobifitness.app.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.UUID

/** Issue #2 timing with sanitized frames, real parsers and virtual GATT callbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[ResistanceGattFixture.Gatt::class])
class ResistanceFeedbackTest {
    private lateinit var client: BleClient
    private lateinit var fixture: ResistanceGattFixture
    private lateinit var scope: CoroutineScope
    private fun setup(scheduler: TestCoroutineScheduler,protocol: Protocol=Protocol.V1,name: String="MB-EP-test") {
        ResistanceGattFixture.Gatt.reset()
        scope=CoroutineScope(SupervisorJob()+UnconfinedTestDispatcher(scheduler))
        client=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0,clock={ scheduler.currentTime+10_000 })
        fixture=ResistanceGattFixture(client).apply { install(protocol,name) }
        when(protocol) {
            Protocol.V1 -> if(name.startsWith("AT-R")) fixture.receive(ResistanceGattFixture.atrFrame()) else fixture.v1(0)
            Protocol.FTMS -> fixture.ftms(3)
            Protocol.HUANTONG -> fixture.receive(ResistanceGattFixture.huantongFrame())
            else -> Unit
        }
        assertEquals("ready",client.state.value.phase); assertTrue(client.state.value.writable)
    }
    @After fun cleanup() {
        if(::client.isInitialized) client.disconnect()
        if(::scope.isInitialized) scope.cancel()
        ResistanceGattFixture.Gatt.reset()
    }
    private fun phase()=client.state.value.resistanceControl?.phase
    private fun rawReceive(id: String,bytes: ByteArray) {
        BleClient::class.java.getDeclaredMethod("receive",UUID::class.java,ByteArray::class.java)
            .apply { isAccessible=true }.invoke(client,BleClient.uuid(id),bytes)
    }
    @Test fun target18MatchesAt3942ms()=runTest {
        setup(testScheduler)
        assertTrue(client.resistance(18.0)); assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,18,1),ResistanceGattFixture.Gatt.writes.last().second)
        advanceTimeBy(3942); runCurrent()
        fixture.receive(ResistanceGattFixture.v1Frame(18),legacy=true)
        assertEquals(18.0,client.state.value.metrics.resistance!!,0.0)
        assertEquals(ResistancePhase.MATCHED,phase()); assertNull(client.state.value.requested)
        advanceTimeBy(20_000); runCurrent(); assertFalse(client.state.value.controlTimedOut)
    }
    @Test fun target7RecoversAt10701msAndRepeated15352msFeedbackStaysMatched()=runTest {
        setup(testScheduler)
        assertTrue(client.resistance(7.0))
        advanceTimeBy(5000); runCurrent()
        assertEquals(ResistancePhase.UNCONFIRMED,phase()); assertNull(client.state.value.requested)
        assertEquals(7.0,client.state.value.resistanceControl!!.target,0.0)
        fixture.v1(0); assertNull(client.state.value.metrics.resistance)
        advanceTimeBy(5701); runCurrent(); fixture.v1(7)
        assertEquals(ResistancePhase.MATCHED,phase()); assertFalse(client.state.value.controlTimedOut)
        assertEquals(7.0,client.state.value.metrics.resistance!!,0.0)
        advanceTimeBy(4651); runCurrent(); fixture.v1(7)
        assertEquals(ResistancePhase.MATCHED,phase()); assertFalse(client.state.value.controlTimedOut)
    }
    @Test fun zeroMissingAndStaleMergedResistanceCannotConfirmButNewManualTargetIsAccepted()=runTest {
        setup(testScheduler); fixture.v1(7)
        val revision=client.state.value.resistanceRevision
        assertTrue(client.resistance(7.0)) // Cached matching value is not new feedback.
        repeat(217) { fixture.v1(0) }
        rawReceive("2a37",byteArrayOf(0,90)) // Valid heart field; merged old resistance remains 7.
        fixture.receive(ResistanceGattFixture.v1Frame(7).copyOf(13)) // Truncated/missing field.
        advanceTimeBy(60_000); runCurrent()
        assertEquals(ResistancePhase.UNCONFIRMED,phase()); assertEquals(revision,client.state.value.resistanceRevision)
        assertEquals("ready",client.state.value.phase); assertEquals(0L,client.connectionId)
        assertEquals(1,ResistanceGattFixture.Gatt.writes.size)
        assertTrue(client.resistance(8.0)); assertEquals(8.0,client.state.value.requested!!,0.0)
        assertFalse(client.state.value.controlTimedOut)
    }
    @Test fun latestTargetReplacesAAndSameValueRequestsOwnSeparateTimers()=runTest {
        setup(testScheduler); assertTrue(client.resistance(18.0))
        advanceTimeBy(4000); runCurrent(); assertTrue(client.resistance(7.0))
        val first7=client.state.value.resistanceControl!!.id
        fixture.v1(18); assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
        advanceTimeBy(1000); runCurrent(); assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
        assertTrue(client.resistance(7.0)); assertTrue(client.state.value.resistanceControl!!.id>first7)
        advanceTimeBy(4000); runCurrent() // Previous 7's timer must not expire this one.
        assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
        advanceTimeBy(1000); runCurrent(); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.v1(7); assertEquals(ResistancePhase.MATCHED,phase())
    }
    @Test fun disconnectAndInvalidationPreventOldCallbacksAndTimersFromRestoringIntent()=runTest {
        setup(testScheduler); assertTrue(client.resistance(7.0))
        val oldGatt=fixture.gatt; val oldNotify=fixture.notify; val cb=fixture.callback
        advanceTimeBy(3000); runCurrent(); client.disconnect()
        fixture.install(); fixture.v1(0); assertTrue(client.resistance(7.0))
        cb.onCharacteristicChanged(oldGatt,oldNotify,ResistanceGattFixture.v1Frame(7))
        cb.onCharacteristicWrite(oldGatt,oldNotify,BluetoothGatt.GATT_FAILURE)
        advanceTimeBy(2000); runCurrent()
        assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase()); assertNull(client.state.value.metrics.resistance)
        client.invalidateControls(); fixture.v1(7)
        advanceTimeBy(20_000); runCurrent()
        assertNull(client.state.value.resistanceControl); assertFalse(client.state.value.controlTimedOut)
        assertEquals(7.0,client.state.value.metrics.resistance!!,0.0)
    }
    @Test fun feedbackBeforeWriteCompletionIsRememberedButDoesNotHideSending()=runTest {
        setup(testScheduler); ResistanceGattFixture.Gatt.holdWrites=true
        val result=async { client.resistance(7.0) }; runCurrent()
        assertEquals(ResistancePhase.SENDING,phase()); assertTrue(client.state.value.busy)
        fixture.v1(7); assertEquals(ResistancePhase.SENDING,phase()); assertFalse(result.isCompleted)
        fixture.ack(); runCurrent(); assertTrue(result.await())
        assertEquals(ResistancePhase.MATCHED,phase()); assertFalse(client.state.value.busy)
        advanceTimeBy(6000); runCurrent(); assertFalse(client.state.value.controlTimedOut)
    }
    @Test fun failedWriteWinsOverEarlyMatchingFeedbackAndLateReadingsDoNotEraseFailure()=runTest {
        setup(testScheduler); ResistanceGattFixture.Gatt.holdWrites=true
        val result=async { client.resistance(7.0) }; runCurrent(); fixture.v1(7)
        fixture.ack(BluetoothGatt.GATT_FAILURE); runCurrent()
        assertFalse(result.await()); assertEquals(ResistancePhase.FAILED,phase())
        fixture.v1(7); advanceTimeBy(10_000); runCurrent()
        assertEquals(ResistancePhase.FAILED,phase()); assertNull(client.state.value.requested)
        assertFalse(client.state.value.controlTimedOut); assertFalse(client.state.value.busy)
    }
    @Test fun feedbackWindowStartsAfterWriteCompletionAndTracksTheLatestValidReading()=runTest {
        setup(testScheduler); ResistanceGattFixture.Gatt.holdWrites=true
        val result=async { client.resistance(7.0) }; runCurrent()
        fixture.v1(7); fixture.v1(3)
        advanceTimeBy(4000); runCurrent(); fixture.ack(); runCurrent(); assertTrue(result.await())
        assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
        advanceTimeBy(1000); runCurrent(); assertFalse(client.state.value.controlTimedOut)
        advanceTimeBy(4000); runCurrent(); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.v1(7); assertEquals(ResistancePhase.MATCHED,phase())
        fixture.v1(3); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.v1(7); assertEquals(ResistancePhase.MATCHED,phase())
    }
    @Test fun invalidationDuringWriteAllowsItToFinishWithoutRestoringItsTarget()=runTest {
        setup(testScheduler); ResistanceGattFixture.Gatt.holdWrites=true
        val result=async { client.resistance(7.0) }; runCurrent()
        client.invalidateControls(); fixture.v1(7); fixture.ack(); runCurrent()
        assertFalse(result.await()); assertNull(client.state.value.resistanceControl)
        assertFalse(client.state.value.busy)
        advanceTimeBy(10_000); runCurrent(); assertNull(client.state.value.resistanceControl)
        ResistanceGattFixture.Gatt.holdWrites=false
        assertTrue(client.resistance(7.0)); assertEquals(ResistancePhase.AWAITING_FEEDBACK,phase())
    }
    @Test fun gattOperationTimeoutStillFailsAndClosesConnection()=runTest {
        setup(testScheduler); ResistanceGattFixture.Gatt.holdWrites=true
        val result=async { client.resistance(7.0) }; runCurrent(); fixture.v1(7)
        advanceTimeBy(6000); runCurrent(); assertFalse(result.await())
        assertEquals("disconnected",client.state.value.phase)
        assertTrue(client.state.value.failure!!.startsWith("gatt_timeout:"))
        assertNull(client.state.value.resistanceControl); assertFalse(client.state.value.controlTimedOut)
    }
    @Test fun v2LateFeedbackUsesDedicatedResistanceFieldNotMotionOrCachedReading()=runTest {
        setup(testScheduler,Protocol.V2,"MOBI-test"); assertTrue(client.resistance(7.0))
        assertArrayEquals(byteArrayOf(2,1,7),ResistanceGattFixture.Gatt.writes.last().second)
        advanceTimeBy(5000); runCurrent()
        rawReceive("8813",byteArrayOf(30,0,60,0,30,0,100,0,40,0,100))
        assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.receive(byteArrayOf(0)); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.receive(byteArrayOf(7)); assertEquals(ResistancePhase.MATCHED,phase())
    }
    @Test fun genericFtmsResponseTimeoutRemainsProtocolFailureEvenWithMatchingReading()=runTest {
        setup(testScheduler,Protocol.FTMS,"Generic test")
        val result=async { client.resistance(7.0) }; runCurrent()
        fixture.receive(byteArrayOf(0x80.toByte(),0,1),fixture.write); runCurrent()
        fixture.ftms(7)
        advanceTimeBy(5000); runCurrent(); assertFalse(result.await())
        assertEquals("disconnected",client.state.value.phase); assertEquals("control_timeout",client.state.value.failure)
        assertFalse(client.state.value.controlTimedOut); assertNull(client.state.value.resistanceControl)
    }
    @Test fun genericFtmsRejectionIsFailureButAcknowledgedWriteCanWaitForLateTelemetry()=runTest {
        setup(testScheduler,Protocol.FTMS,"Generic test")
        val failed=async { client.resistance(7.0) }; runCurrent()
        fixture.receive(byteArrayOf(0x80.toByte(),0,1),fixture.write); runCurrent()
        fixture.receive(byteArrayOf(0x80.toByte(),4,4),fixture.write); runCurrent()
        assertFalse(failed.await()); assertEquals(ResistancePhase.FAILED,phase())
        advanceTimeBy(1000); runCurrent()
        val ok=async { client.resistance(7.0) }; runCurrent()
        fixture.receive(byteArrayOf(0x80.toByte(),4,1),fixture.write); runCurrent(); assertTrue(ok.await())
        advanceTimeBy(5000); runCurrent(); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.ftms(0); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.ftms(7); assertEquals(ResistancePhase.MATCHED,phase())
        rawReceive("2ada",byteArrayOf(0xff.toByte())); fixture.ftms(7)
        assertNull(client.state.value.resistanceControl)
    }
    @Test fun officialFtmsDoesNotGainAGenericIndicationRequirement()=runTest {
        setup(testScheduler,Protocol.FTMS,"MB-E1010"); assertTrue(client.resistance(7.0))
        assertArrayEquals(byteArrayOf(4,70),ResistanceGattFixture.Gatt.writes.last().second)
        advanceTimeBy(6000); runCurrent(); assertEquals(ResistancePhase.UNCONFIRMED,phase())
        fixture.ftms(7); assertEquals(ResistancePhase.MATCHED,phase())
    }
    @Test fun atrWriteOnlyControlDoesNotInventFeedbackOrTimeOut()=runTest {
        setup(testScheduler,Protocol.V1,"AT-R-test"); assertTrue(client.resistance(7.0))
        assertEquals(ResistancePhase.SENT,phase()); assertNull(client.state.value.requested)
        assertNull(client.state.value.metrics.resistance); assertEquals(7.0,client.state.value.commandedResistance!!,0.0)
        advanceTimeBy(30_000); runCurrent(); fixture.receive(ResistanceGattFixture.atrFrame())
        assertFalse(client.state.value.controlTimedOut); assertNull(client.state.value.metrics.resistance)
    }
    @Test fun huantongRequiredPeriodicWritesContinueWithoutInventingReadings()=runTest {
        setup(testScheduler,Protocol.HUANTONG,"MOBI-test"); assertTrue(client.resistance(7.0))
        assertEquals(ResistancePhase.SENT,phase()); assertNull(client.state.value.metrics.resistance)
        val before=ResistanceGattFixture.Gatt.writes.size
        advanceTimeBy(6000); runCurrent()
        assertEquals(before+6,ResistanceGattFixture.Gatt.writes.size)
        assertTrue(ResistanceGattFixture.Gatt.writes.takeLast(6).all { it.second.contentEquals(MobiCommands.huantongResistance(7)) })
        assertFalse(client.state.value.controlTimedOut); assertNull(client.state.value.metrics.resistance)
        client.disconnect(); val count=ResistanceGattFixture.Gatt.writes.size
        advanceTimeBy(6000); runCurrent(); assertEquals(count,ResistanceGattFixture.Gatt.writes.size)
    }
}
