package org.openmobifitness.app.ble

import android.app.Application
import android.bluetooth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.openmobifitness.app.data.saveUser
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothGatt
import org.robolectric.util.ReflectionHelpers

/** Reported FFE3/FFE4 service topology, with synthetic payloads (not a hardware capture). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[BleClientTest.ReadableGatt::class])
class BleClientTest {
    // Robolectric supplies readIncomingCharacteristic but doesn't implement the Android read entry point.
    @Implements(BluetoothGatt::class)
    class ReadableGatt : ShadowBluetoothGatt() {
        @Implementation fun readCharacteristic(c: BluetoothGattCharacteristic)=readIncomingCharacteristic(c)
        @Implementation override fun requestMtu(mtu: Int): Boolean = acceptMtu && super.requestMtu(mtu)
        companion object { var acceptMtu=true }
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    private lateinit var client: BleClient
    private lateinit var gatt: BluetoothGatt
    private lateinit var callback: BluetoothGattCallback
    private lateinit var notify: BluetoothGattCharacteristic
    private fun setupGatt(descriptor: Boolean=true,allow: Boolean=true,discoveryDeadline: Job?=null,treadmill: Boolean=false,foldTimeout: Long=15000) {
        client=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0,foldFeedbackTimeoutMs=foldTimeout)
        callback=ReflectionHelpers.getField(client,"callback")
        gatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        ReflectionHelpers.setField(client,"gatt",gatt)
        ReflectionHelpers.setField(client,"connectionJob",discoveryDeadline)
        client.state.value=LinkState(phase="discovering",machine=Machine.ELLIPTICAL)
        val service=BluetoothGattService(BleClient.uuid("ffe0"),BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(BleClient.uuid("ffe3"),10,BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE))
        if(treadmill) service.addCharacteristic(BluetoothGattCharacteristic(BleClient.uuid("ffed"),8,BluetoothGattCharacteristic.PERMISSION_WRITE))
        notify=BluetoothGattCharacteristic(BleClient.uuid("ffe4"),16,0)
        if(descriptor) notify.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),BluetoothGattDescriptor.PERMISSION_WRITE))
        service.addCharacteristic(notify)
        shadowOf(gatt).apply { setGattCallback(callback); addDiscoverableService(service); if(allow) allowCharacteristicNotification(notify) }
        assertTrue(gatt.discoverServices())
    }
    private fun frame(resistance: Int=3)=byteArrayOf(0xab.toByte(),4,0,11,17,80,9,0,0,1,0xf4.toByte(),0,0,resistance.toByte(),125)
    @After fun cleanup() { if(::client.isInitialized) client.disconnect(); scope.cancel(); ReadableGatt.acceptMtu=true }

    @Test fun v011EllipticalWorksWhenMtuNegotiationIsRejectedAndDiscoveryDeadlineIsReleased()=runBlocking {
        ReadableGatt.acceptMtu=false
        val deadline=Job()
        setupGatt(discoveryDeadline=deadline)
        assertTrue(deadline.isCancelled)
        assertEquals("awaiting_data",client.state.value.phase)
        callback.onCharacteristicChanged(gatt,notify,frame(1))
        assertEquals("ready",client.state.value.phase)
        assertArrayEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,shadowOf(gatt).latestWrittenBytes)
        for(level in 1..24) {
            ReflectionHelpers.setField(client,"lastWrite",-1000L)
            assertTrue(client.resistance(level.toDouble()))
            assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,level.toByte(),9),shadowOf(gatt).latestWrittenBytes)
            callback.onCharacteristicChanged(gatt,notify,frame(level))
            assertNull(client.state.value.requested)
        }
        val last=shadowOf(gatt).latestWrittenBytes.copyOf()
        for(status in listOf(1,2,1,0)) assertTrue(client.trainingStatus(status))
        assertArrayEquals(last,shadowOf(gatt).latestWrittenBytes)
    }
    @Test fun foldingV1TreadmillQueriesItsStateWithoutStartingItsMotor()=runBlocking {
        setupGatt(treadmill=true)
        callback.onCharacteristicChanged(gatt,notify,frame().apply { this[3]=12; this[4]=18 })
        assertArrayEquals(MobiCommands.v1Treadmill(0,0),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(0xae.toByte(),1,0,1))
        assertEquals(1,client.state.value.deviceDetails.foldedState)
        assertTrue(client.trainingStatus(1))
        assertArrayEquals(MobiCommands.v1Treadmill(0,0),shadowOf(gatt).latestWrittenBytes)
    }

    @Test fun foldRequiresMatchingFeedbackAndNeverRepeatsOnTimeout()=runBlocking {
        setupGatt(treadmill=true,foldTimeout=30)
        callback.onCharacteristicChanged(gatt,notify,frame().apply { this[3]=12; this[4]=18 })
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(0xae.toByte(),1,0,1))
        assertTrue(client.fold(1,client.connectionId) { true })
        assertEquals("waiting",client.state.value.foldPhase)
        assertArrayEquals(MobiCommands.v1Treadmill(7,0),shadowOf(gatt).latestWrittenBytes)
        assertFalse(client.fold(1,client.connectionId) { true })
        delay(60)
        assertEquals("timeout",client.state.value.foldPhase)
        assertArrayEquals(MobiCommands.v1Treadmill(7,0),shadowOf(gatt).latestWrittenBytes)
        assertFalse(client.fold(1,client.connectionId) { true })
        client.refreshFoldState()
        assertArrayEquals(MobiCommands.v1Treadmill(0,0),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(0xae.toByte(),1,0,0))
        assertEquals("idle",client.state.value.foldPhase)
        assertFalse(client.fold(1,client.connectionId) { true })
        assertFalse(client.fold(0,client.connectionId) { false })
    }
    @Test fun v2FoldingUsesInteractionServiceAndRejectsObsoleteConfirmation()=runBlocking {
        setupModern(Protocol.V2,fold=true)
        assertTrue(client.state.value.deviceDetails.supportFold)
        assertFalse(client.fold(2,client.connectionId) { true })
        val mutex=ReflectionHelpers.getField<kotlinx.coroutines.sync.Mutex>(client,"controls")
        mutex.lock()
        var allowed=true
        val queued=async(start=CoroutineStart.UNDISPATCHED) { client.fold(1,client.connectionId) { allowed } }
        allowed=false;mutex.unlock();assertFalse(queued.await())
        assertTrue(client.fold(1,client.connectionId) { true })
        assertArrayEquals(byteArrayOf(3,1,0),shadowOf(gatt).latestWrittenBytes)
        assertEquals(1,client.state.value.deviceDetails.foldedState)
        val report=gatt.getService(BleClient.uuid("8900")).getCharacteristic(BleClient.uuid("8902"))
        callback.onCharacteristicChanged(gatt,report,byteArrayOf(3,1,0))
        assertEquals("idle",client.state.value.foldPhase);assertNull(client.state.value.foldTarget)
        client.disconnect();callback.onCharacteristicChanged(gatt,report,byteArrayOf(3,1,1))
        assertNull(client.state.value.deviceDetails.foldedState)
    }
    @Test fun distancePlanStartsReportingBeforeWaitingForItsFirstCounter()=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("openmobi.db");app.getSharedPreferences("preferences",0).edit().clear().commit()
        setupModern(Protocol.V2)
        val c=org.openmobifitness.app.Controller(app,CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),tickAutomatically=false)
        try {
            withTimeout(5000) { c.state.first { it.ready } }
            ReflectionHelpers.setField(c,"ble",client)
            val user=UserProfile(name="Reporting QA");c.repo.saveUser(user);c.switchUser(user.id).join();c.setIdentityPolicy(IdentityPolicy.REMEMBER)
            c.select(Workout(title="Distance",machine=Machine.ELLIPTICAL,ownerUserId=user.id,steps=listOf(Step(Condition.DISTANCE,100.0))))
            val starting=c.start()
            withTimeout(5000) { c.state.first { it.starting } }
            assertArrayEquals(byteArrayOf(4),shadowOf(gatt).latestWrittenBytes)
            assertNull(c.state.value.session)
            callback.onCharacteristicChanged(gatt,notify,byteArrayOf(30,0,60,0,30,0,100,0,40,0,100))
            withTimeout(5000) { starting.join() }
            assertNotNull(c.state.value.session);assertNull(c.state.value.error)
        } finally { c.scope.cancel();c.repo.db.close() }
    }

    @Test fun subscribedButSilentEquipmentIsNotReadyForTraining() {
        setupGatt()
        assertEquals("awaiting_data",client.state.value.phase)
        assertEquals(1,client.state.value.subscriptions)
        assertFalse(client.state.value.dataReceived)
        assertFalse(client.state.value.writable)
        assertTrue(client.state.value.diagnostic.any { it=="notify_enabled:ffe4" })
    }
    @Test fun primaryAndHeartGattNotificationsAndDisconnectionStayIndependent()=runBlocking {
        setupGatt()
        callback.onCharacteristicChanged(gatt,notify,frame(3))
        val accessory=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0)
        try {
            val heartCallback=ReflectionHelpers.getField<BluetoothGattCallback>(accessory,"callback")
            val heartGatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:66"))
            ReflectionHelpers.setField(accessory,"gatt",heartGatt)
            accessory.state.value=LinkState(phase="discovering",machine=Machine.HEART)
            val measurement=BluetoothGattCharacteristic(BleClient.uuid("2a37"),BluetoothGattCharacteristic.PROPERTY_NOTIFY,0)
            measurement.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),BluetoothGattDescriptor.PERMISSION_WRITE))
            val service=BluetoothGattService(BleClient.uuid("180d"),BluetoothGattService.SERVICE_TYPE_PRIMARY).apply { addCharacteristic(measurement) }
            shadowOf(heartGatt).apply { setGattCallback(heartCallback); addDiscoverableService(service); allowCharacteristicNotification(measurement) }
            assertTrue(heartGatt.discoverServices())
            heartCallback.onCharacteristicChanged(heartGatt,measurement,byteArrayOf(0,151.toByte()))
            assertEquals("ready",accessory.state.value.phase)
            assertEquals(151,accessory.state.value.metrics.heartBpm)
            assertEquals("ready",client.state.value.phase)
            assertEquals(3.0,client.state.value.metrics.resistance!!,0.0)
            assertFalse(accessory.state.value.writable)
            accessory.disconnect()
            heartCallback.onCharacteristicChanged(heartGatt,measurement,byteArrayOf(0,160.toByte()))
            assertNull(accessory.state.value.metrics.heartBpm)
            callback.onCharacteristicChanged(gatt,notify,frame(4))
            assertEquals(4.0,client.state.value.metrics.resistance!!,0.0)
            ReflectionHelpers.setField(client,"lastWrite",-1000L)
            assertTrue(client.resistance(12.0))
            assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,12,9),shadowOf(gatt).latestWrittenBytes)
        } finally { accessory.disconnect() }
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
    @Test fun queuedControlFromPreviousUseGenerationNeverReachesGatt()=runBlocking {
        setupGatt()
        callback.onCharacteristicChanged(gatt,notify,frame(3))
        ReflectionHelpers.setField(client,"lastWrite",-1000L)
        val written=shadowOf(gatt).latestWrittenBytes.copyOf()
        val lock=ReflectionHelpers.getField<kotlinx.coroutines.sync.Mutex>(client,"controls")
        lock.lock()
        val request=async(start=CoroutineStart.UNDISPATCHED) { client.resistance(16.0) }
        client.invalidateControls()
        lock.unlock()
        assertFalse(request.await())
        assertArrayEquals(written,shadowOf(gatt).latestWrittenBytes)
        assertNull(client.state.value.requested)
        assertEquals(3.0,client.state.value.metrics.resistance!!,0.0)
    }
    @Test fun legacyReadOnlyModelReportsResistanceWithoutOfferingControl()=runBlocking {
        setupGatt()
        callback.onCharacteristicChanged(gatt,notify,frame().apply { this[4]=18 })
        assertEquals(8.0,client.state.value.range!!.max,0.0)
        assertEquals(3.0,client.state.value.metrics.resistance!!,0.0)
        assertFalse(client.state.value.writable)
        assertFalse(client.resistance(4.0))
    }
    @Suppress("DEPRECATION")
    private fun setupModern(protocol: Protocol,fold: Boolean=false) {
        client=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0)
        callback=ReflectionHelpers.getField(client,"callback")
        gatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        ReflectionHelpers.setField(client,"gatt",gatt)
        client.state.value=LinkState(name="MOBI-E",phase="discovering")
        val service=BluetoothGattService(BleClient.uuid(if(protocol==Protocol.V2) "8800" else "fff0"),0)
        val chars=if(protocol==Protocol.V2) listOf(
            Triple("8801",2,byteArrayOf(2)),Triple("8802",2,byteArrayOf(11,17)),Triple("8806",2,byteArrayOf(5,1,24)),
            Triple("8812",18,byteArrayOf(3)),Triple("8813",16,byteArrayOf()),Triple("88ff",8,byteArrayOf()),Triple("880f",8,byteArrayOf())
        ) else listOf(Triple("fff1",16,byteArrayOf()),Triple("fff2",8,byteArrayOf()))
        shadowOf(gatt).setGattCallback(callback)
        for((id,properties,data) in chars) {
            val c=BluetoothGattCharacteristic(BleClient.uuid(id),properties,17).apply { value=data }
            if(properties and 16!=0) {
                c.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),16))
                shadowOf(gatt).allowCharacteristicNotification(c)
                if(id in setOf("8813","fff1")) notify=c
            }
            service.addCharacteristic(c)
        }
        shadowOf(gatt).addDiscoverableService(service)
        if(fold) {
            val extra=BluetoothGattService(BleClient.uuid("8900"),0)
            listOf(Triple("8901",2,byteArrayOf(4)),Triple("8902",18,byteArrayOf(3,1,1)),Triple("8903",8,byteArrayOf())).forEach { (id,props,bytes) ->
                val characteristic=BluetoothGattCharacteristic(BleClient.uuid(id),props,17).apply { value=bytes }
                if(props and 16!=0) {
                    characteristic.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),16))
                    shadowOf(gatt).allowCharacteristicNotification(characteristic)
                }
                extra.addCharacteristic(characteristic)
            }
            shadowOf(gatt).addDiscoverableService(extra)
        }
        assertTrue(gatt.discoverServices())
    }
    @Test fun aggregateDeviceCanStartReportingAfterInitializationAndExplicitTrainingStart()=runBlocking {
        setupModern(Protocol.V2)
        assertEquals(client.state.value.diagnostic.joinToString("\n"),"ready",client.state.value.phase)
        assertTrue(client.state.value.writable)
        assertTrue(client.trainingStatus(1))
        assertArrayEquals(byteArrayOf(4),shadowOf(gatt).latestWrittenBytes)
        assertTrue(client.trainingStatus(2))
        assertArrayEquals(byteArrayOf(5),shadowOf(gatt).latestWrittenBytes)
        assertTrue(client.trainingStatus(0))
        assertArrayEquals(byteArrayOf(3),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(30,0,60,0,30,0,100,0,40,0,200.toByte()))
        assertEquals(3.0,client.state.value.metrics.speedMps!!,0.0)
        assertEquals(200.0,client.state.value.metrics.powerW!!,0.0)
    }
    @Test fun huantongInitializesWeightThenSendsRecurringCommandsAndCancelsOnDisconnect()=runBlocking {
        setupModern(Protocol.HUANTONG)
        assertArrayEquals(MobiCommands.huantongWeight(70),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(0,1,0,0x60,0,0,0,0,0,0,0,0))
        assertEquals("ready",client.state.value.phase)
        assertEquals(60.0,client.state.value.metrics.cadence!!,0.0)
        assertFalse(client.state.value.resistanceFeedback)
        assertNull(client.state.value.metrics.resistance)
        ReflectionHelpers.setField(client,"lastWrite",-1000L)
        assertTrue(client.resistance(24.0))
        assertEquals(24.0,client.state.value.commandedResistance!!,0.0)
        assertNull(client.state.value.requested)
        delay(1150)
        assertArrayEquals(MobiCommands.huantongResistance(24),shadowOf(gatt).latestWrittenBytes)
        val periodic=ReflectionHelpers.getField<Job>(client,"periodicJob")
        client.disconnect()
        assertTrue(periodic.isCancelled)
        assertFalse(client.state.value.writable)
        assertNull(client.state.value.commandedResistance)
    }
    @Suppress("DEPRECATION")
    private fun setupFtms(name: String): BluetoothGattCharacteristic {
        client=BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0)
        callback=ReflectionHelpers.getField(client,"callback")
        gatt=ShadowBluetoothGatt.newInstance(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        ReflectionHelpers.setField(client,"gatt",gatt)
        client.state.value=LinkState(name=name,phase="discovering")
        val service=BluetoothGattService(BleClient.uuid("1826"),0)
        val control=BluetoothGattCharacteristic(BleClient.uuid("2ad9"),40,17)
        shadowOf(gatt).setGattCallback(callback)
        val feature=BluetoothGattCharacteristic(BleClient.uuid("2acc"),2,1).apply { value=byteArrayOf(0,0,0,0,4,0,0,0) }
        val range=BluetoothGattCharacteristic(BleClient.uuid("2ad6"),2,1).apply { value=byteArrayOf(10,0,240.toByte(),0,10,0) }
        notify=BluetoothGattCharacteristic(BleClient.uuid("2ace"),16,1)
        for(c in listOf(feature,range,control,notify)) {
            if(c.properties and 48!=0) { c.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),16)); shadowOf(gatt).allowCharacteristicNotification(c) }
            service.addCharacteristic(c)
        }
        shadowOf(gatt).addDiscoverableService(service); assertTrue(gatt.discoverServices())
        callback.onCharacteristicChanged(gatt,notify,byteArrayOf(0x81.toByte(),0,0,30,0))
        assertEquals("ready",client.state.value.phase); assertTrue(client.state.value.writable)
        return control
    }
    @Test fun officialFtmsInitializesControlAndUsesVendorTwoByteTargetWithoutIndicationAck()=runBlocking {
        setupFtms("MB-E1010")
        assertTrue(ReflectionHelpers.getField<Boolean>(client,"granted"))
        assertFalse(client.state.value.diagnostic.contains("notify_enabled:2ad9"))
        ReflectionHelpers.setField(client,"lastWrite",-1000L)
        assertTrue(client.resistance(12.0))
        assertArrayEquals(byteArrayOf(4,120),shadowOf(gatt).latestWrittenBytes)
        assertTrue(client.trainingStatus(1)); assertArrayEquals(byteArrayOf(7),shadowOf(gatt).latestWrittenBytes)
        assertTrue(client.trainingStatus(2)); assertArrayEquals(byteArrayOf(8,2),shadowOf(gatt).latestWrittenBytes)
    }
    @Test fun genericFtmsKeepsStandardControlResponseAndSixteenBitResistance()=runBlocking {
        val control=setupFtms("Generic cross trainer")
        ReflectionHelpers.setField(client,"lastWrite",-1000L)
        val request=async(Dispatchers.Unconfined) { client.resistance(12.0) }
        val start=async(Dispatchers.Unconfined) { client.trainingStatus(1) }
        assertArrayEquals(byteArrayOf(0),shadowOf(gatt).latestWrittenBytes)
        assertFalse(request.isCompleted)
        assertFalse(start.isCompleted)
        callback.onCharacteristicChanged(gatt,control,byteArrayOf(0x80.toByte(),0,1))
        assertArrayEquals(byteArrayOf(4,120,0),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,control,byteArrayOf(0x80.toByte(),4,1))
        assertTrue(request.await())
        assertArrayEquals(byteArrayOf(7),shadowOf(gatt).latestWrittenBytes)
        callback.onCharacteristicChanged(gatt,control,byteArrayOf(0x80.toByte(),7,1))
        assertTrue(start.await())
    }
    @Test fun treadmillLiveUseDoesNotRequestFtmsControlOrStartTheMotor()=runBlocking {
        setupFtms("Generic cross trainer")
        client.state.value=client.state.value.copy(machine=Machine.TREADMILL)
        val before=shadowOf(gatt).latestWrittenBytes.copyOf()
        repeat(3) { assertTrue(client.beginLiveUse()) }
        assertArrayEquals(before,shadowOf(gatt).latestWrittenBytes)
        assertFalse(ReflectionHelpers.getField<Boolean>(client,"granted"))
    }
}
