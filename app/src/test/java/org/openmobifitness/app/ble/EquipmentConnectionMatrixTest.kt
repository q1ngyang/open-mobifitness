package org.openmobifitness.app.ble

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowBluetoothGatt
import org.robolectric.util.ReflectionHelpers

/** Synthetic GATT/packet matrix through the production scanner, initializer and parser. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[EquipmentConnectionMatrixTest.ReadGatt::class])
class EquipmentConnectionMatrixTest(private val fixture: Fixture) {
    data class Fixture(val label: String,val name: String,val protocol: Protocol,val machine: Machine,
        val service: String,val characteristic: String,val packet: ByteArray,val kind: Int=0,val subtype: Int=0) {
        override fun toString()=label
    }
    @Implements(BluetoothGatt::class)
    class ReadGatt: ShadowBluetoothGatt() {
        @Implementation fun readCharacteristic(c: BluetoothGattCharacteristic)=readIncomingCharacteristic(c)
        @Implementation override fun requestMtu(mtu: Int)=false
    }
    private val scheduler=TestCoroutineScheduler()
    private val dispatcher=UnconfinedTestDispatcher(scheduler)
    private val scope=CoroutineScope(SupervisorJob()+dispatcher)
    private val adapter=BluetoothAdapter.getDefaultAdapter()
    private val remote=adapter.getRemoteDevice("00:11:22:33:44:55")
    private val target=FoundDevice(remote.address,fixture.name,-50,fixture.machine==Machine.HEART)
    private val client=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0,
        connectionDispatcher=dispatcher,clock={scheduler.currentTime+1})
    @Before fun setup() { shadowOf(adapter).setState(BluetoothAdapter.STATE_ON) }
    @After fun cleanup() { client.stopScan();client.disconnect();scope.cancel() }
    private fun advance(ms: Long) { scheduler.advanceTimeBy(ms);scheduler.runCurrent() }
    @Suppress("DEPRECATION")
    private fun open(allowMeasurement: Boolean=true): Pair<BluetoothGatt,BluetoothGattCharacteristic> {
        client.connect(target)
        val cb=ReflectionHelpers.getField<ScanCallback>(client,"scanCallback")
        cb.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,ScanResult(remote,null,-50,1))
        scheduler.runCurrent()
        val g=ReflectionHelpers.getField<BluetoothGatt>(client,"gatt")
        val services=linkedMapOf<String,BluetoothGattService>()
        fun endpoint(service: String,id: String,properties: Int,bytes: ByteArray=byteArrayOf(),allow: Boolean=true): BluetoothGattCharacteristic {
            val c=BluetoothGattCharacteristic(BleClient.uuid(id),properties,17).apply { value=bytes }
            if(properties and 48!=0) {
                c.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),16))
                if(allow) shadowOf(g).allowCharacteristicNotification(c)
            }
            services.getOrPut(service) { BluetoothGattService(BleClient.uuid(service),0) }.addCharacteristic(c)
            return c
        }
        if(fixture.protocol==Protocol.V2) {
            endpoint("8800","8801",2,byteArrayOf(2))
            endpoint("8800","8802",2,byteArrayOf(fixture.kind.toByte(),fixture.subtype.toByte()))
            endpoint("8800","8812",16)
        }
        // These successful accessory subscriptions must not conceal measurement failure.
        endpoint("180f","2a19",18,byteArrayOf(75))
        if(fixture.protocol==Protocol.FTMS) endpoint("1826","2ad9",40)
        if(fixture.protocol==Protocol.HUANTONG) endpoint("fff0","fff2",8)
        val measurement=endpoint(fixture.service,fixture.characteristic,16,allow=allowMeasurement)
        services.values.forEach { shadowOf(g).addDiscoverableService(it) }
        shadowOf(g).gattCallback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED)
        scheduler.runCurrent();advance(2000) // Includes MB-HW's specified delayed subscription.
        return g to measurement
    }
    private fun report(g: BluetoothGatt,c: BluetoothGattCharacteristic) {
        shadowOf(g).gattCallback.onCharacteristicChanged(g,c,fixture.packet);scheduler.runCurrent()
    }
    @Test fun freshConnectionAndThreePowerCyclesRestoreOnlyTheNewTransport() {
        var (g,c)=open()
        report(g,c)
        assertEquals("ready",client.state.value.phase)
        assertEquals(fixture.machine,client.state.value.machine)
        assertEquals(fixture.protocol,client.state.value.protocol)
        repeat(3) {
            val old=g;val oldCharacteristic=c
            shadowOf(old).gattCallback.onConnectionStateChange(old,8,BluetoothProfile.STATE_DISCONNECTED)
            scheduler.runCurrent();assertTrue(shadowOf(old).isClosed)
            assertEquals("disconnected",client.state.value.phase)
            assertEquals(listOf(target),nearbyDevices(listOf(target),client.state.value,LinkState()))
            advance(30_000)
            val next=open();g=next.first;c=next.second
            assertNotSame(old,g)
            report(old,oldCharacteristic)
            shadowOf(old).gattCallback.onConnectionStateChange(old,8,BluetoothProfile.STATE_DISCONNECTED)
            scheduler.runCurrent();assertFalse(shadowOf(g).isClosed)
            report(g,c)
            assertEquals("ready",client.state.value.phase)
            assertEquals(fixture.machine,client.state.value.machine)
        }
    }
    @Test fun failedMeasurementSubscriptionClosesEvenWhenOtherSubscriptionsSucceed() {
        val (g,c)=open(allowMeasurement=false)
        assertEquals("disconnected",client.state.value.phase)
        assertEquals("subscription_failed",client.state.value.failure)
        assertTrue(shadowOf(g).isClosed)
        report(g,c)
        assertFalse(client.state.value.dataReceived)
        assertFalse(client.state.value.writable)
    }
    @Test fun aQuietButSubscribedDeviceIsNotForcedToReconnectOrStartMoving() {
        val (g,_)=open()
        assertEquals("awaiting_data",client.state.value.phase)
        advance(120_000)
        assertFalse(shadowOf(g).isClosed)
        assertEquals("awaiting_data",client.state.value.phase)
        assertFalse(client.state.value.diagnostic.any { it.startsWith("connection_recovery:") })
        if(fixture.machine==Machine.TREADMILL) assertFalse(client.state.value.diagnostic.any { it.startsWith("gatt_operation:begin key=write:") })
    }
    companion object {
        private fun v1(kind: Int,subtype: Int)=byteArrayOf(0xab.toByte(),4,0,kind.toByte(),subtype.toByte(),80,9,0,0,1,0xf4.toByte(),0,0,3,125)
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}") fun fixtures(): List<Array<Any>> {
            val cases=mutableListOf(
                Fixture("V1 elliptical","MB-EP",Protocol.V1,Machine.ELLIPTICAL,"ffe0","ffe4",v1(11,17)),
                Fixture("V1 AT-R bike","AT-R66146",Protocol.V1,Machine.BIKE,"ffe0","ffe4",v1(10,1)),
                Fixture("V1 rower delayed notify","MB-HW",Protocol.V1,Machine.ROWER,"ffe0","ffe4",byteArrayOf(0xab.toByte(),3,0,0,0,0,32,0,0,0,0,0,20)),
                Fixture("V1 treadmill","MB-T",Protocol.V1,Machine.TREADMILL,"ffe0","ffe4",v1(12,1)),
                Fixture("FTMS bike","Generic bike",Protocol.FTMS,Machine.BIKE,"1826","2ad2",byteArrayOf(4,0,16,14,120,0)),
                Fixture("FTMS elliptical","Generic elliptical",Protocol.FTMS,Machine.ELLIPTICAL,"1826","2ace",byteArrayOf(8,0,0,16,14,120,0,0,0)),
                Fixture("FTMS rower","Generic rower",Protocol.FTMS,Machine.ROWER,"1826","2ad1",byteArrayOf(8,0,60,12,0,100,0)),
                Fixture("FTMS treadmill","Generic treadmill",Protocol.FTMS,Machine.TREADMILL,"1826","2acd",byteArrayOf(0,0,16,14)),
                Fixture("Huantong elliptical","MOBI-E",Protocol.HUANTONG,Machine.ELLIPTICAL,"fff0","fff1",byteArrayOf(0,1,0,0x60,0,0,0,0,0,0,0,0)),
                Fixture("Huantong bike","MOBI-B",Protocol.HUANTONG,Machine.BIKE,"fff0","fff1",byteArrayOf(0,1,0,0x60,0,0,0,0,0,0,0,0)),
                Fixture("Heart-rate strap","Heart",Protocol.UNKNOWN,Machine.HEART,"180d","2a37",byteArrayOf(0,120))
            )
            for((machine,kind,subtype) in listOf(Triple(Machine.ELLIPTICAL,11,17),Triple(Machine.BIKE,10,18),Triple(Machine.ROWER,9,3),Triple(Machine.TREADMILL,12,1),Triple(Machine.JUMP_ROPE,16,1),Triple(Machine.DUMBBELL,16,2))) {
                val bytes=when(machine) {
                    Machine.JUMP_ROPE -> byteArrayOf(0,0,60,0,10,0,10,0,20,0,0,0,1,0,8)
                    Machine.DUMBBELL -> byteArrayOf(1,2,0,0,10,0,8)
                    else -> byteArrayOf(30,0,60,0,30,0,100,0,40,0,100)
                }
                cases+=Fixture("V2 $machine","MB-TEST",Protocol.V2,machine,"8800","8813",bytes,kind,subtype)
            }
            return cases.map { arrayOf<Any>(it) }
        }
    }
}
