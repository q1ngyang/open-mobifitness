package org.openmobifitness.app.ble

import android.bluetooth.*
import org.junit.Assert.*
import org.openmobifitness.core.Protocol
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothGatt
import org.robolectric.util.ReflectionHelpers

/** Synthetic identities and minimum frames only; no issue log or captured device identity. */
@Suppress("DEPRECATION")
class ResistanceGattFixture(val client: BleClient) {
    @Implements(BluetoothGatt::class)
    class Gatt : ShadowBluetoothGatt() {
        @Implementation fun readCharacteristic(c: BluetoothGattCharacteristic): Boolean = readIncomingCharacteristic(c)
        @Implementation override fun writeCharacteristic(c: BluetoothGattCharacteristic, bytes: ByteArray, type: Int): Int {
            writes += c.uuid.toString().substring(4,8) to bytes.copyOf()
            return if(holdWrites) BluetoothStatusCodes.SUCCESS else super.writeCharacteristic(c,bytes,type)
        }
        companion object {
            var holdWrites=false
            val writes=mutableListOf<Pair<String,ByteArray>>()
            fun reset() { holdWrites=false; writes.clear() }
        }
    }
    lateinit var gatt: BluetoothGatt; private set
    lateinit var callback: BluetoothGattCallback; private set
    lateinit var notify: BluetoothGattCharacteristic; private set
    lateinit var write: BluetoothGattCharacteristic; private set

    fun install(protocol: Protocol=Protocol.V1,name: String="MB-EP-test") {
        callback=ReflectionHelpers.getField(client,"callback")
        gatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        ReflectionHelpers.setField(client,"gatt",gatt)
        client.state.value=LinkState(name=name,phase="discovering")
        shadowOf(gatt).setGattCallback(callback)
        val service=BluetoothGattService(BleClient.uuid(when(protocol) {
            Protocol.V2 -> "8800"; Protocol.FTMS -> "1826"; Protocol.HUANTONG -> "fff0"; else -> "ffe0"
        }),0)
        fun characteristic(id: String, properties: Int, vararg data: Int): BluetoothGattCharacteristic {
            val c=BluetoothGattCharacteristic(BleClient.uuid(id),properties,17).apply { value=data.map { it.toByte() }.toByteArray() }
            if(properties and 48!=0) {
                c.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),16))
                shadowOf(gatt).allowCharacteristicNotification(c)
            }
            service.addCharacteristic(c); return c
        }
        when(protocol) {
            Protocol.V1 -> { write=characteristic("ffe3",10); notify=characteristic("ffe4",16) }
            Protocol.V2 -> {
                characteristic("8801",2,2); characteristic("8802",2,11,17); characteristic("8806",2,5,1,24)
                notify=characteristic("8812",18,3); characteristic("8813",16)
                characteristic("88ff",8); write=characteristic("880f",8)
            }
            Protocol.FTMS -> {
                characteristic("2acc",2,0,0,0,0,4,0,0,0)
                characteristic("2ad6",2,10,0,240,0,10,0)
                notify=characteristic("2ace",16); write=characteristic("2ad9",40)
            }
            Protocol.HUANTONG -> { notify=characteristic("fff1",16); write=characteristic("fff2",8) }
            else -> error("Unsupported test fixture")
        }
        shadowOf(gatt).addDiscoverableService(service)
        assertTrue(gatt.discoverServices())
    }
    fun receive(bytes: ByteArray, characteristic: BluetoothGattCharacteristic=notify, legacy: Boolean=false) {
        if(legacy) { characteristic.value=bytes; callback.onCharacteristicChanged(gatt,characteristic) }
        else callback.onCharacteristicChanged(gatt,characteristic,bytes)
    }
    fun ack(status: Int=BluetoothGatt.GATT_SUCCESS) = callback.onCharacteristicWrite(gatt,write,status)
    fun v1(level: Int) = receive(v1Frame(level))
    fun ftms(level: Int) = receive(byteArrayOf(0x81.toByte(),0,0,(level*10).toByte(),((level*10) ushr 8).toByte()))
    companion object {
        // MB-EP kind 0B/subtype 11, read/write mode 1, no motion/counters/identity.
        fun v1Frame(level: Int)=byteArrayOf(0xab.toByte(),4,0,11,17,0,1,0,0,0,0,0,0,level.toByte(),0)
        fun atrFrame()=byteArrayOf(0xab.toByte(),4,0,10,1,0,1,0,0,0,0,0,0)
        fun huantongFrame()=byteArrayOf(0,1,0,0x60,0,0,0,0,0,0,0,0)
    }
}
