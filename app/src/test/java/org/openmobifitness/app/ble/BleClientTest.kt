package org.openmobifitness.app.ble

import android.app.Application
import android.bluetooth.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothGatt
import org.robolectric.util.ReflectionHelpers

/** Reported FFE3/FFE4 service topology, with synthetic payloads (not a hardware capture). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class BleClientTest {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    private lateinit var client: BleClient
    private lateinit var gatt: BluetoothGatt
    private lateinit var callback: BluetoothGattCallback
    private lateinit var notify: BluetoothGattCharacteristic
    private fun setupGatt(descriptor: Boolean=true,allow: Boolean=true) {
        client=BleClient(RuntimeEnvironment.getApplication(),scope)
        callback=ReflectionHelpers.getField(client,"callback")
        gatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        ReflectionHelpers.setField(client,"gatt",gatt)
        client.state.value=LinkState(phase="discovering",machine=Machine.ELLIPTICAL)
        val service=BluetoothGattService(BleClient.uuid("ffe0"),BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(BleClient.uuid("ffe3"),10,BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE))
        notify=BluetoothGattCharacteristic(BleClient.uuid("ffe4"),16,0)
        if(descriptor) notify.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),BluetoothGattDescriptor.PERMISSION_WRITE))
        service.addCharacteristic(notify)
        shadowOf(gatt).apply { setGattCallback(callback); addDiscoverableService(service); if(allow) allowCharacteristicNotification(notify) }
        assertTrue(gatt.discoverServices())
    }
    private fun frame(resistance: Int=3)=byteArrayOf(0xab.toByte(),4,0,11,17,80,9,0,0,1,0xf4.toByte(),0,0,resistance.toByte(),125)
    @After fun cleanup() { if(::client.isInitialized) client.disconnect(); scope.cancel() }

    @Test fun subscribedButSilentEquipmentIsNotReadyForTraining() {
        setupGatt()
        assertEquals("awaiting_data",client.state.value.phase)
        assertEquals(1,client.state.value.subscriptions)
        assertFalse(client.state.value.dataReceived)
        assertFalse(client.state.value.writable)
        assertTrue(client.state.value.diagnostic.any { it=="notify_enabled:ffe4" })
    }
    @Suppress("DEPRECATION")
    @Test fun legacyNotificationOnModernAndroidParsesAndAllowsFeedbackConfirmedControl()=runBlocking {
        setupGatt()
        notify.value=frame()
        callback.onCharacteristicChanged(gatt,notify)
        val ready=client.state.value
        assertEquals("ready",ready.phase); assertEquals(24.0,ready.range!!.max,0.0)
        assertEquals(60.0,ready.metrics.cadence!!,0.0); assertEquals(3.0,ready.metrics.resistance!!,0.0)
        assertTrue(ready.writable); assertEquals(1,ready.parsedPackets)
        // Keep the observed status until a matching device response arrives.
        ReflectionHelpers.setField(client,"lastWrite",-1000L)
        assertTrue(client.resistance(4.0))
        assertEquals(3.0,client.state.value.metrics.resistance!!,0.0)
        assertEquals(4.0,client.state.value.requested!!,0.0)
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,4,9),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,notify,frame(4))
        assertEquals(4.0,client.state.value.metrics.resistance!!,0.0)
        assertNull(client.state.value.requested)
    }
    @Test fun modernValueCallbackDoesNotReadStaleCharacteristicValue() {
        setupGatt()
        callback.onCharacteristicChanged(gatt,notify,frame(5))
        assertEquals(5.0,client.state.value.metrics.resistance!!,0.0)
        assertEquals(1,client.state.value.receivedPackets)
        assertTrue(client.state.value.diagnostic.contains("callback:notify_value"))
    }
    @Test fun missingCccdIsVisibleAndCannotPretendToBeConnectedWithData() {
        setupGatt(descriptor=false)
        assertEquals("subscription_failed",client.state.value.phase)
        assertTrue(client.state.value.diagnostic.contains("notify_missing_cccd:ffe4"))
        assertFalse(client.state.value.writable)
    }
    @Test fun rejectedNotificationAndMalformedPayloadAreDiagnosable() {
        setupGatt(allow=false)
        assertEquals("subscription_failed",client.state.value.phase)
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(1,2,3))
        assertEquals(1,client.state.value.receivedPackets)
        assertEquals(0,client.state.value.parsedPackets)
        assertFalse(client.state.value.dataReceived)
        assertTrue(client.state.value.diagnostic.any { it.startsWith("unparsed_v1:") })
    }
    @Test fun disconnectedGattCallbacksCannotRestoreMetricsOrControl() {
        setupGatt()
        client.disconnect()
        callback.onCharacteristicChanged(gatt,notify,frame())
        assertEquals("disconnected",client.state.value.phase)
        assertNull(client.state.value.metrics.resistance)
        assertFalse(client.state.value.writable)
    }
}
