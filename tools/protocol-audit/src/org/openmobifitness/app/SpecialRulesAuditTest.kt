package org.openmobifitness.app

import android.app.Application
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.ble.BleClient
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers

/** Characterization of v0.3.3, not desired behavior or a hardware capability claim.
 * A passing A13/A14 test reproduces an unresolved gap. No real GATT writes occur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class SpecialRulesAuditTest {
    private lateinit var scope: CoroutineScope
    private lateinit var ble: BleClient

    @Before fun setup() {
        scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        ble=BleClient(RuntimeEnvironment.getApplication(),scope)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }
    @After fun cleanup() { scope.cancel(); ble.disconnect() }
    private fun receive(chr: String, data: ByteArray) {
        BleClient::class.java.getDeclaredMethod("receive",UUID::class.java,ByteArray::class.java)
            .apply { isAccessible=true }.invoke(ble,BleClient.uuid(chr),data)
    }

    @Test fun declaredZeroMinimumStillDropsFeedbackAndCannotConfirmZeroRequest() {
        for ((kind,machine) in listOf(9 to Machine.ROWER,10 to Machine.BIKE,11 to Machine.ELLIPTICAL)) {
            val session=MobiSession(Protocol.V2,"Audit")
            ReflectionHelpers.setField(ble,"mobi",session)
            ble.state.value=LinkState(name="Audit",phase="ready",protocol=Protocol.V2,machine=machine)
            receive("8802",byteArrayOf(kind.toByte(),18))
            receive("8806",byteArrayOf(5,0,24))
            assertTrue(session.canReadResistance)
            assertTrue(session.canWriteResistance)
            assertTrue(session.range!!.contains(0.0))
            assertArrayEquals(byteArrayOf(2,1,0),session.resistanceCommand(0))
            receive("8812",byteArrayOf(12))
            val before=ble.state.value
            // Seed pending intent only; this test does not pretend a physical write succeeded.
            ble.state.value=before.copy(requested=0.0)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            receive("8812",byteArrayOf(0))
            assertEquals(12.0,ble.state.value.metrics.resistance!!,0.0)
            assertEquals(before.resistanceAt,ble.state.value.resistanceAt)
            assertEquals(0.0,ble.state.value.requested!!,0.0)
            // A positive feedback value follows the ordinary confirmation path.
            ble.state.value=ble.state.value.copy(requested=1.0)
            receive("8812",byteArrayOf(1))
            assertEquals(1.0,ble.state.value.metrics.resistance!!,0.0)
            assertNull(ble.state.value.requested)
            println("AUDIT A13: $machine declares 0..24; zero command is encodable, zero feedback leaves 12 and pending 0; positive control confirms")
        }
    }

    @Test fun mobiFtmsReportsZeroButKeepsTheOldCalculationResistance() {
        for ((chr,full) in listOf(
            "2ad2" to byteArrayOf(36,0,16,14,120,0,12,0),
            "2ace" to byteArrayOf(136.toByte(),0,0,16,14,120,0,0,0,120,0)
        )) {
            val session=MobiFtmsSession("MOBI-Audit")
            session.range=ResistanceRange(0.0,24.0)
            assertEquals(12.0,session.receive(chr,full)!!.resistance!!,0.0)
            session.activeSecond()
            val previous=session.energyPower!!
            val zero=full.copyOf().apply { this[size-2]=0; this[size-1]=0 }
            assertEquals(0.0,session.receive(chr,zero)!!.resistance!!,0.0)
            repeat(12) { session.activeSecond() }
            assertEquals(previous,session.energyPower!!,0.0)
            // A valid positive change proves the same path does update its calculation input.
            val one=zero.copyOf().apply { this[size-2]=if(chr=="2ace") 10 else 1 }
            session.receive(chr,one)
            repeat(12) { session.activeSecond() }
            assertTrue(session.energyPower!! < previous)
            println("AUDIT A14: $chr reports 12 -> 0, but energy power stays $previous W for 12 ticks; 1 decreases it")
        }
    }

    @Test fun genericFtmsZeroFeedbackDoesConfirmAZeroRequest() {
        ble.state.value=LinkState(name="Generic",phase="ready",protocol=Protocol.FTMS,machine=Machine.BIKE)
        receive("2ad6",byteArrayOf(0,0,240.toByte(),0,10,0))
        receive("2ad2",byteArrayOf(36,0,16,14,120,0,12,0))
        ble.state.value=ble.state.value.copy(requested=0.0)
        receive("2ad2",byteArrayOf(36,0,16,14,120,0,0,0))
        assertEquals(0.0,ble.state.value.metrics.resistance!!,0.0)
        assertNull(ble.state.value.requested)
        println("AUDIT CONTROL-4: generic FTMS preserves zero feedback and confirms a pending zero target")
    }
}
