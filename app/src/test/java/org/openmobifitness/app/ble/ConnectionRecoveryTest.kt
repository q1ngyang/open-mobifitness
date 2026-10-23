package org.openmobifitness.app.ble

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowBluetoothGatt
import org.robolectric.util.ReflectionHelpers

/** Virtual-time Android callback regressions; these do not simulate a handset radio/firmware. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,shadows=[ConnectionRecoveryTest.RecoveryGatt::class])
class ConnectionRecoveryTest {
    @Implements(BluetoothGatt::class)
    class RecoveryGatt: ShadowBluetoothGatt() {
        @Implementation override fun discoverServices(): Boolean = if(silentDiscovery) true else super.discoverServices()
        @Implementation override fun requestMtu(mtu: Int): Boolean = silentMtu
        @Implementation override fun disconnect() { if(throwDisconnect) throw SecurityException("revoked");super.disconnect() }
        companion object { var silentDiscovery=false;var silentMtu=false;var throwDisconnect=false }
    }
    private val scheduler=TestCoroutineScheduler()
    private val dispatcher=UnconfinedTestDispatcher(scheduler)
    private val scope=CoroutineScope(SupervisorJob()+dispatcher)
    private lateinit var client: BleClient
    private lateinit var adapter: BluetoothAdapter
    private lateinit var remote: BluetoothDevice
    private val target=FoundDevice("00:11:22:33:44:55","MB-EP-test",-45)
    @Before fun setup() {
        RecoveryGatt.silentDiscovery=false;RecoveryGatt.silentMtu=false;RecoveryGatt.throwDisconnect=false
        adapter=BluetoothAdapter.getDefaultAdapter();shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        remote=adapter.getRemoteDevice(target.address)
        client=newClient()
    }
    private fun newClient(budgets: ConnectionTimeouts=ConnectionTimeouts(),io: CoroutineDispatcher=dispatcher)=
        BleClient(RuntimeEnvironment.getApplication(),scope,operationGapMs=0,timeouts=budgets,connectionDispatcher=io,clock={ scheduler.currentTime+1 })
    @After fun cleanup() { RecoveryGatt.throwDisconnect=false;client.stopScan();client.disconnect();scope.cancel() }
    private fun advance(ms: Long) { scheduler.advanceTimeBy(ms);scheduler.runCurrent() }
    private fun gatt(): BluetoothGatt?=ReflectionHelpers.getField(client,"gatt")
    private fun scanCallback(): ScanCallback=ReflectionHelpers.getField(client,"scanCallback")
    private fun advertisement(name: String=target.name,device: BluetoothDevice=remote): ScanResult {
        val bytes=name.toByteArray(Charsets.UTF_8)
        val raw=byteArrayOf((bytes.size+1).toByte(),9)+bytes+byteArrayOf(0)
        val record=ReflectionHelpers.callStaticMethod<ScanRecord>(ScanRecord::class.java,"parseFromBytes",ReflectionHelpers.ClassParameter.from(ByteArray::class.java,raw))
        return ScanResult(device,record,-45,1000000)
    }
    private fun advertise(cb: ScanCallback=scanCallback(),name: String=target.name,device: BluetoothDevice=remote) {
        cb.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,advertisement(name,device));scheduler.runCurrent()
    }
    private fun freshConnect(): BluetoothGatt { client.scan();advertise();client.connect(target);scheduler.runCurrent();return requireNotNull(gatt()) }
    private fun configure(g: BluetoothGatt): BluetoothGattCharacteristic {
        val measurement=BluetoothGattCharacteristic(BleClient.uuid("ffe4"),BluetoothGattCharacteristic.PROPERTY_NOTIFY,0)
        measurement.addDescriptor(BluetoothGattDescriptor(BleClient.uuid("2902"),BluetoothGattDescriptor.PERMISSION_WRITE))
        val service=BluetoothGattService(BleClient.uuid("ffe0"),BluetoothGattService.SERVICE_TYPE_PRIMARY).apply { addCharacteristic(measurement) }
        shadowOf(g).apply { addDiscoverableService(service);allowCharacteristicNotification(measurement) }
        return measurement
    }
    private fun ready(): BluetoothGatt {
        val g=freshConnect();val measurement=configure(g)
        shadowOf(g).gattCallback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED);scheduler.runCurrent()
        assertEquals("awaiting_data",client.state.value.phase)
        shadowOf(g).gattCallback.onCharacteristicChanged(g,measurement,byteArrayOf(0xab.toByte(),4,17,11,17,0,1,0,0,0,0,0,0,0,0));scheduler.runCurrent()
        assertEquals("ready",client.state.value.phase)
        return g
    }
    @Test fun disconnectedEquipmentAndHeartRemainVisibleButActiveAddressesAreHidden() {
        val hr=FoundDevice("00:11:22:33:44:66","Heart",-50,true)
        val all=listOf(target,hr)
        val disconnected=LinkState(address=target.address)
        val heart=LinkState(address=hr.address)
        assertEquals(all,nearbyDevices(all,disconnected,heart))
        for(phase in listOf("searching","connecting","discovering","initializing","awaiting_data","ready")) {
            assertEquals(listOf(hr),nearbyDevices(all,disconnected.copy(phase=phase),heart))
            assertEquals(listOf(target),nearbyDevices(all,disconnected,heart.copy(phase=phase)))
        }
    }
    @Test fun firstScanConnectionStopsScannerAndUsesAdvertisedDevice() {
        val g=freshConnect()
        assertEquals("connecting",client.state.value.phase);assertFalse(client.scanning.value)
        assertEquals(listOf(g),shadowOf(remote).bluetoothGatts)
        assertTrue(shadowOf(adapter.bluetoothLeScanner).activeScans.isEmpty())
    }
    @Test fun threePowerCyclesCloseOldGattAndDiscoverSameDeviceAgain() {
        var old=ready()
        repeat(3) {
            advance(1)
            shadowOf(old).gattCallback.onConnectionStateChange(old,8,BluetoothProfile.STATE_DISCONNECTED)
            assertTrue(shadowOf(old).isClosed);assertEquals("disconnected",client.state.value.phase)
            assertEquals(target.address,client.state.value.address)
            // Advance beyond the app/platform scan quota between realistic power cycles.
            advance(30_000)
            client.connect(target);assertEquals("searching",client.state.value.phase)
            val oldCb=shadowOf(old).gattCallback
            advertise(name="") // Remembered address remains recognizable without a name AD field.
            val next=requireNotNull(gatt());assertNotSame(old,next);assertEquals(target.name,client.state.value.name)
            oldCb.onConnectionStateChange(old,8,BluetoothProfile.STATE_DISCONNECTED)
            assertSame(next,gatt());assertEquals("connecting",client.state.value.phase)
            val characteristic=configure(next)
            shadowOf(next).gattCallback.onConnectionStateChange(next,0,BluetoothProfile.STATE_CONNECTED)
            shadowOf(next).gattCallback.onCharacteristicChanged(next,characteristic,byteArrayOf(0xab.toByte(),4,17,11,17,0,1,0,0,0,0,0,0,0,0))
            scheduler.runCurrent();assertEquals("ready",client.state.value.phase)
            old=next
        }
    }
    @Test fun poweredOffTargetFallsBackOncePerAttemptAndCannotWaitForever() {
        client.connect(target);assertNull(gatt());advance(10_000)
        val first=requireNotNull(gatt());assertEquals("connecting",client.state.value.phase)
        advance(20_000);assertTrue(shadowOf(first).isClosed);assertEquals("searching",client.state.value.phase)
        advance(11_000);val second=requireNotNull(gatt());assertNotSame(first,second)
        advance(20_000)
        assertEquals("disconnected",client.state.value.phase);assertEquals("connecting_timeout",client.state.value.failure)
        assertTrue(shadowOf(second).isClosed)
        val id=client.connectionId;advance(300_000);assertEquals(id,client.connectionId);assertNull(gatt())
        assertEquals(1,client.state.value.diagnostic.count { it.startsWith("connection_recovery:") })
    }
    @Test fun scanFailureIsVisibleAndExistingSystemConnectionCanUseDirectFallback() {
        client.connect(target);scanCallback().onScanFailed(6)
        assertEquals("scan_error:6",client.scanError.value);assertFalse(client.scanning.value)
        assertNotNull(gatt());assertEquals("connecting",client.state.value.phase)
        assertEquals("error:6",client.scanTrace.outcome)
    }
    @Test fun cancelledSearchIgnoresItsLateResultsAndFailure() {
        client.connect(target);val stopped=scanCallback();client.stopScan()
        advertise(stopped);stopped.onScanFailed(6);advance(100_000)
        assertNull(gatt());assertFalse(client.scanning.value);assertNull(client.scanError.value)
        assertEquals("connection_cancelled",client.state.value.failure)
    }
    @Test fun obsoleteScanCallbacksCannotStopANewScan() {
        client.scan();val first=scanCallback();client.stopScan();client.scan()
        first.onScanFailed(6);advertise(first)
        assertTrue(client.scanning.value);assertTrue(client.found.value.isEmpty());assertNull(client.scanError.value)
        advertise();assertEquals(listOf(target),client.found.value)
    }
    @Test fun repeatedReconnectPressDoesNotRestartOrExtendAnAttempt() {
        client.connect(target);val firstScan=scanCallback();val id=client.connectionId
        advance(5000);client.connect(target)
        assertSame(firstScan,scanCallback());assertEquals(id,client.connectionId)
        advance(5000);assertEquals("connecting",client.state.value.phase)
        val first=gatt();client.connect(target);assertSame(first,gatt())
    }
    @Test fun cancelDuringRecoveryPreventsDelayedRestart() {
        val g=freshConnect()
        shadowOf(g).gattCallback.onConnectionStateChange(g,133,BluetoothProfile.STATE_DISCONNECTED)
        assertEquals("searching",client.state.value.phase);client.disconnect();advance(100_000)
        assertEquals("disconnected",client.state.value.phase);assertNull(gatt());assertFalse(client.scanning.value)
    }
    @Test fun overallDeadlineIncludesSearchAndRecovery() {
        client=newClient(ConnectionTimeouts(totalMs=8000))
        client.connect(target);advance(8000)
        assertEquals("connection_total_timeout",client.state.value.failure);assertFalse(client.scanning.value);assertNull(gatt())
        advance(100_000);assertNull(gatt())
    }
    @Test fun discoveryDeadlineRecoversOnlyOnce() {
        RecoveryGatt.silentDiscovery=true
        val first=freshConnect();shadowOf(first).gattCallback.onConnectionStateChange(first,0,BluetoothProfile.STATE_CONNECTED)
        assertEquals("discovering",client.state.value.phase);advance(15_000)
        assertTrue(shadowOf(first).isClosed);advance(1000);advertise()
        val next=requireNotNull(gatt());shadowOf(next).gattCallback.onConnectionStateChange(next,0,BluetoothProfile.STATE_CONNECTED)
        advance(15_000);assertEquals("discovering_timeout",client.state.value.failure);assertTrue(shadowOf(next).isClosed)
    }
    @Test fun initializationHasItsOwnDeadlineAndDoesNotLoopRetries() {
        client=newClient(ConnectionTimeouts(initializationMs=2000))
        RecoveryGatt.silentMtu=true
        val g=freshConnect();configure(g);shadowOf(g).gattCallback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED)
        assertEquals("initializing",client.state.value.phase);advance(2000)
        assertEquals("initializing_timeout",client.state.value.failure);assertTrue(shadowOf(g).isClosed)
        assertFalse(client.state.value.diagnostic.any { it.startsWith("connection_recovery:") })
    }
    @Test fun waitingForSensorDataIsNotAnUnfinishedConnection() {
        val g=freshConnect();configure(g);shadowOf(g).gattCallback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED)
        assertEquals("awaiting_data",client.state.value.phase);advance(120_000)
        assertSame(g,gatt());assertFalse(shadowOf(g).isClosed);assertEquals("awaiting_data",client.state.value.phase)
    }
    @Test fun earlyConnectedCallbackCanFinishInitializationBeforeConnectGattReturns() {
        client=newClient(io=Dispatchers.IO)
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val created=java.util.concurrent.atomic.AtomicReference<BluetoothGatt>()
        shadowOf(remote).setGattConnectionInterceptor { g ->
            created.set(g);entered.countDown()
            check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))
        }
        try {
            client.scan();advertise();client.connect(target)
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val g=created.get();assertNull(gatt())
            configure(g);shadowOf(g).gattCallback.onConnectionStateChange(g,0,BluetoothProfile.STATE_CONNECTED)
            scheduler.runCurrent()
            assertEquals("awaiting_data",client.state.value.phase);assertSame(g,gatt())
            release.countDown()
            runBlocking { withTimeout(5000) { while(!client.state.value.diagnostic.any { it.startsWith("connect_call:return") }) delay(10) } }
            assertFalse(shadowOf(g).isClosed)
            assertFalse(client.state.value.diagnostic.any { it.startsWith("connect_call:late_result") })
        } finally { release.countDown() }
    }
    @Test fun delayedConnectResultAfterCancellationIsClosed() {
        // A separate scheduler lets the OS connect call complete after the UI cancels it.
        val worker=TestCoroutineScheduler()
        client=newClient(io=StandardTestDispatcher(worker))
        client.scan();advertise();client.connect(target)
        assertEquals("connecting",client.state.value.phase);assertNull(gatt())
        client.disconnect();worker.runCurrent();scheduler.runCurrent();worker.runCurrent()
        val late=shadowOf(remote).bluetoothGatts.single()
        assertTrue(shadowOf(late).isClosed);assertNull(gatt());assertEquals("disconnected",client.state.value.phase)
    }
    @Test fun newerConnectionSurvivesOldCallbackAndLateOldReturn() {
        val worker=TestCoroutineScheduler()
        client=newClient(io=StandardTestDispatcher(worker))
        client.scan();advertise();client.connect(target);client.disconnect()
        val other=adapter.getRemoteDevice("00:11:22:33:44:66")
        client.connect(target.copy(address=other.address));advertise(device=other)
        worker.runCurrent();scheduler.runCurrent();worker.runCurrent()
        val old=shadowOf(remote).bluetoothGatts.single();val new=requireNotNull(gatt())
        assertTrue(shadowOf(old).isClosed);assertSame(other,new.device)
        shadowOf(old).gattCallback.onConnectionStateChange(old,0,BluetoothProfile.STATE_CONNECTED)
        assertSame(new,gatt());assertFalse(shadowOf(new).isClosed)
    }
    @Test fun disconnectFailureStillClosesGatt() {
        val g=freshConnect();RecoveryGatt.throwDisconnect=true;client.disconnect()
        assertTrue(shadowOf(g).isClosed);assertNull(gatt());assertEquals("disconnected",client.state.value.phase)
    }
    @Test fun bluetoothOffFailsImmediately() {
        shadowOf(adapter).setState(BluetoothAdapter.STATE_OFF);client.connect(target)
        assertEquals("bluetooth_unavailable",client.state.value.failure);assertFalse(client.scanning.value);assertNull(gatt())
    }
    @Test fun revokedPermissionDoesNotTriggerRepeatedConnections() {
        client.scan();advertise();shadowOf(remote).setShouldThrowSecurityExceptions(true);client.connect(target)
        assertEquals("permission_revoked",client.state.value.failure);assertNull(gatt());advance(100_000)
        assertFalse(client.state.value.diagnostic.any { it.startsWith("connection_recovery:") })
    }
    @Test fun scanQuotaDisplaysAnErrorAndRecoversAfterCooldown() {
        repeat(4) { client.scan();client.stopScan() }
        client.scan();assertFalse(client.scanning.value);assertEquals("scan_rate_limit",client.scanError.value)
        advance(30_000);client.scan();assertTrue(client.scanning.value);assertNull(client.scanError.value)
    }
    @Test fun scanDiagnosticsDistinguishNoRadioReportsFromFilteredReports() {
        client.scan();advertise(name="Unrelated",device=adapter.getRemoteDevice("00:11:22:33:44:77"))
        assertEquals(1,client.scanTrace.reports);assertEquals(0,client.scanTrace.accepted);assertTrue(client.found.value.isEmpty())
        advertise();assertEquals(2,client.scanTrace.reports);assertEquals(1,client.scanTrace.accepted);assertEquals(2,client.scanTrace.uniqueDevices)
    }
    @Test fun reconnectUsesAnAddressFilterEvenWithoutANameOrServiceAdvertisement() {
        client.connect(target)
        val scan=shadowOf(adapter.bluetoothLeScanner).activeScans.single()
        assertEquals(target.address,scan.scanFilters().single().deviceAddress)
        advertise(name="")
        assertEquals("connecting",client.state.value.phase)
        assertEquals(target.name,client.state.value.name)
    }
    @Test fun equipmentAndHeartShareOneScanQuotaAndPlatformCooldown() {
        val budget=ScanBudget()
        val equipment=BleClient(RuntimeEnvironment.getApplication(),scope,scanBudget=budget,clock={scheduler.currentTime+1})
        val heart=BleClient(RuntimeEnvironment.getApplication(),scope,role="heart",scanBudget=budget,clock={scheduler.currentTime+1})
        try {
            repeat(2) { equipment.scan();equipment.stopScan();heart.scan();heart.stopScan() }
            heart.scan();assertEquals("scan_rate_limit",heart.scanError.value);assertFalse(heart.scanning.value)
            advance(30_000);equipment.scan()
            ReflectionHelpers.getField<ScanCallback>(equipment,"scanCallback").onScanFailed(6)
            heart.scan();assertEquals("scan_rate_limit",heart.scanError.value)
            advance(30_000);heart.scan();assertTrue(heart.scanning.value)
        } finally { equipment.stopScan();heart.stopScan();equipment.disconnect();heart.disconnect() }
    }
}
