package org.openmobifitness.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmobifitness.core.*
import java.util.UUID

data class FoundDevice(val address: String, val name: String, val rssi: Int, val heart: Boolean = false)
data class LinkState(
    val name: String = "", val address: String = "", val phase: String = "disconnected", val protocol: Protocol = Protocol.UNKNOWN,
    val machine: Machine = Machine.UNKNOWN, val range: ResistanceRange? = null, val metrics: Metrics = Metrics(),
    val motionAt: Long = 0, val resistanceAt: Long = 0, val heartAt: Long = 0, val requested: Double? = null,
    val busy: Boolean = false, val writable: Boolean = false, val diagnostic: List<String> = emptyList()
)
@SuppressLint("MissingPermission") // Entry points are gated by runtime permission checks; revoked permissions are caught.
class BleClient(private val context: Context, private val scope: CoroutineScope) {
    val state = MutableStateFlow(LinkState())
    val found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var connectionJob: Job? = null
    private var scanJob: Job? = null
    private val operations = Mutex()
    private var pending: CompletableDeferred<Boolean>? = null
    private var pendingKey: String? = null
    private var indication: CompletableDeferred<Boolean>? = null
    private var expectedOpcode = -1
    private var template: ByteArray? = null
    private var templateAt = 0L
    private var magnets: Int? = null
    private var ftmsFeature = false
    private var granted = false
    private var unlocked = false
    private var lastWrite = 0L
    private var legacyProfile: String? = null
    private val cccd = uuid("2902")
    private fun log(value: String) { org.openmobifitness.app.data.AppLog.event("ble",value); state.value=state.value.copy(diagnostic=(state.value.diagnostic+value).takeLast(160)) }
    fun available() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
    fun scan() {
        stopScan(); found.value=emptyList()
        runCatching {
            check(available())
            requireNotNull(adapter!!.bluetoothLeScanner).startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanner)
            scanning.value=true
            scanJob=scope.launch { delay(12_000); stopScan() }
        }.onFailure { log("scan_unavailable"); scanning.value=false }
    }
    fun stopScan() { scanJob?.cancel(); runCatching { adapter?.bluetoothLeScanner?.stopScan(scanner) }; scanning.value=false }
    private val scanner=object: ScanCallback() {
        override fun onScanResult(callbackType: Int,result: ScanResult) { scope.launch {
            val name=runCatching { result.scanRecord?.deviceName ?: result.device.name ?: "" }.getOrDefault("")
            val services=result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            if(name.startsWith("MB-",true) || name.startsWith("MOBI",true) || services.any { it in listOf(uuid("8800"),uuid("ffe0"),uuid("1826"),uuid("180d")) }) {
                val item=FoundDevice(result.device.address,name.take(120),result.rssi,uuid("180d") in services && uuid("1826") !in services)
                found.value=(found.value.filterNot { it.address==item.address }+item).sortedByDescending { it.rssi }.take(80)
            }
        } }
        override fun onScanFailed(errorCode: Int) { scope.launch { scanning.value=false; log("scan_error:$errorCode") } }
    }
    fun connect(device: FoundDevice) {
        disconnect(); stopScan()
        state.value=LinkState(name=device.name,address=device.address,phase="connecting",machine=if(device.heart) Machine.HEART else Protocols.machine(device.name))
        try {
            val remote=adapter?.getRemoteDevice(device.address) ?: error("bluetooth_unavailable")
            gatt=remote.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
            connectionJob=scope.launch { delay(20_000); if(state.value.phase!="ready") fail("connection_timeout") }
        } catch(e: Exception) { fail("connection_failed") }
    }
    fun disconnect() {
        if(gatt!=null) org.openmobifitness.app.data.AppLog.event("disconnect",state.value.protocol.name)
        connectionJob?.cancel(); pending?.complete(false); indication?.complete(false)
        val old=gatt; gatt=null; runCatching { old?.disconnect(); old?.close() }
        template=null; magnets=null; ftmsFeature=false; granted=false; unlocked=false; legacyProfile=null
        state.value=state.value.copy(phase="disconnected",range=null,metrics=Metrics(),motionAt=0,resistanceAt=0,requested=null,busy=false,writable=false)
    }
    private fun fail(reason: String) { log(reason); disconnect() }
    private fun complete(key: String,status: Int) { if(pendingKey==key) pending?.complete(status==BluetoothGatt.GATT_SUCCESS) }
    private val callback=object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt,status: Int,newState: Int) { scope.launch {
            if(g !== gatt) return@launch
            if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED) { fail("disconnected:$status"); return@launch }
            if(newState==BluetoothProfile.STATE_CONNECTED) { state.value=state.value.copy(phase="discovering"); if(!runCatching { g.discoverServices() }.getOrDefault(false)) fail("discovery_failed") }
        } }
        override fun onServicesDiscovered(g: BluetoothGatt,status: Int) { scope.launch {
            if(g !== gatt) return@launch
            if(status!=0) { fail("discovery_failed:$status"); return@launch }
            try { initialize(g) } catch(e: SecurityException) { fail("permission_revoked") }
        } }
        override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,status: Int) = receivedRead(g,c,value,status)
        @Deprecated("Legacy callback") override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) {
            if(Build.VERSION.SDK_INT<33) receivedRead(g,c,c.value ?: byteArrayOf(),status)
        }
        private fun receivedRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,status: Int) { scope.launch {
            if(g!==gatt) return@launch
            if(status==0) receive(c.uuid,value)
            complete("read:${c.uuid}",status)
        } }
        override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray) = changed(g,c,value)
        @Deprecated("Legacy callback") override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic) { if(Build.VERSION.SDK_INT<33) changed(g,c,c.value ?: byteArrayOf()) }
        private fun changed(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray) { scope.launch { if(g===gatt) receive(c.uuid,value) } }
        override fun onCharacteristicWrite(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) { scope.launch { if(g===gatt) complete("write:${c.uuid}",status) } }
        override fun onDescriptorWrite(g: BluetoothGatt,d: BluetoothGattDescriptor,status: Int) { scope.launch { if(g===gatt) complete("notify:${d.characteristic.uuid}",status) } }
    }
    private suspend fun operation(key: String,start: (BluetoothGatt)->Boolean): Boolean = operations.withLock {
        val current=gatt ?: return@withLock false
        val result=CompletableDeferred<Boolean>(); pending=result; pendingKey=key
        try {
            if(!start(current)) { log("gatt_rejected:$key"); return@withLock false }
            withTimeout(6000) { result.await() }.also { if(!it) log("gatt_failed:$key") }
        } catch(e: TimeoutCancellationException) { fail("gatt_timeout:$key"); false }
        catch(e: SecurityException) { fail("permission_revoked"); false }
        finally { if(pending===result) { pending=null; pendingKey=null } }
    }
    private fun characteristic(service: String,which: String) = gatt?.getService(uuid(service))?.getCharacteristic(uuid(which))
    private suspend fun read(c: BluetoothGattCharacteristic) = operation("read:${c.uuid}") { it.readCharacteristic(c) }
    private suspend fun subscribe(c: BluetoothGattCharacteristic): Boolean {
        val d=c.getDescriptor(cccd) ?: return false
        val bytes=if(c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return operation("notify:${c.uuid}") { g ->
            if(!g.setCharacteristicNotification(c,true)) false
            else if(Build.VERSION.SDK_INT>=33) g.writeDescriptor(d,bytes)==BluetoothStatusCodes.SUCCESS
            else { d.value=bytes; g.writeDescriptor(d) }
        }
    }
    private suspend fun write(service: String,which: String,bytes: ByteArray): Boolean {
        val c=characteristic(service,which) ?: return false
        org.openmobifitness.app.data.AppLog.packet("tx",which,bytes)
        val type=if(c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return operation("write:${c.uuid}") { g ->
            if(Build.VERSION.SDK_INT>=33) g.writeCharacteristic(c,bytes,type)==BluetoothStatusCodes.SUCCESS
            else { c.writeType=type; c.value=bytes; g.writeCharacteristic(c) }
        }
    }
    private suspend fun initialize(g: BluetoothGatt) {
        g.services.forEach { s -> log("service:${s.uuid}"); s.characteristics.forEach { log("  characteristic:${it.uuid} properties:${it.properties}") } }
        val services=g.services.map { it.uuid }
        val protocol=when {
            uuid("8800") in services -> Protocol.V2
            uuid("ffe0") in services -> Protocol.V1
            uuid("1826") in services -> Protocol.FTMS
            uuid("fff0") in services && state.value.name.startsWith("MOBI-") -> Protocol.HUANTONG
            else -> Protocol.UNKNOWN
        }
        state.value=state.value.copy(protocol=protocol)
        if(protocol==Protocol.FTMS) {
            val chars=g.getService(uuid("1826"))?.characteristics?.map { it.uuid.short() } ?: emptyList()
            state.value=state.value.copy(machine=when { "2ace" in chars -> Machine.ELLIPTICAL; "2ad2" in chars -> Machine.BIKE; "2ad1" in chars -> Machine.ROWER; "2acd" in chars -> Machine.TREADMILL; else -> Machine.UNKNOWN })
        }
        val allowedNotify=setOf("ffe1","ffe4","8811","8812","8813","880e","2ad9","2ada","2ad1","2ad2","2ace","2acd","2a37","fff1")
        var controlNotify=false
        for(s in g.services) for(c in s.characteristics) {
            if(g!==gatt) return
            val short=c.uuid.short()
            if(short in allowedNotify && c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0) {
                val success=subscribe(c)
                if(short=="2ad9") controlNotify=success
            }
        }
        for(s in g.services) for(c in s.characteristics) {
            if(g!==gatt) return
            if(c.uuid.short() in setOf("8802","8805","8806","8812","2acc","2ad6","2a24","2a26") && c.properties and BluetoothGattCharacteristic.PROPERTY_READ!=0) read(c)
        }
        if(g!==gatt) return
        val writable=when(protocol) {
            Protocol.V2 -> state.value.range!=null && characteristic("8800","880f")!=null && characteristic("8800","88ff")!=null
            Protocol.V1 -> state.value.range!=null && characteristic("ffe0","ffe3")!=null
            Protocol.FTMS -> state.value.range!=null && ftmsFeature && controlNotify && characteristic("1826","2ad9")!=null
            else -> false
        }
        connectionJob?.cancel(); state.value=state.value.copy(phase="ready",writable=writable && state.value.machine in setOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER))
        if(protocol==Protocol.HUANTONG) log("huantong_read_only_pending_profile")
    }
    private fun receive(id: UUID,data: ByteArray) {
        val short=id.short(); val now=SystemClock.elapsedRealtime()
        if(short in setOf("ffe1","ffe4","8811","8812","8813","8802","8805","8806","2ad1","2ad2","2ace","2acd","2ad9","2ada","fff1"))
            org.openmobifitness.app.data.AppLog.packet("rx",short,data)
        // Diagnostics intentionally omit names, addresses, serial number and raw workout samples.
        if(short in setOf("8802","8805","8806","2acc","2ad6")) log("capability:$short:${data.joinToString("") { "%02x".format(it) }}")
        var metrics: Metrics?=null; var motion=false
        when(short) {
            "8802" -> if(data.size>=2) {
                val machine=when(data[0].toInt() and 255) { 9 -> Machine.ROWER; 10 -> Machine.BIKE; 11 -> Machine.ELLIPTICAL; 12 -> Machine.TREADMILL; else -> state.value.machine }
                state.value=state.value.copy(machine=machine)
            }
            "8805" -> magnets=data.firstOrNull()?.let { it.toInt() and 255 }?.takeIf { it>0 }
            "8806" -> state.value=state.value.copy(range=Protocols.v2Range(data))
            "8812" -> if(data.isNotEmpty()) metrics=Metrics(resistance=(data[0].toInt() and 255).toDouble())
            "8813" -> {
                metrics=Protocols.v2Metrics(data)
                // Legacy treadmill variants use different distance scales; withhold unverified values.
                if(state.value.machine==Machine.TREADMILL) metrics=metrics?.copy(distanceM=null,caloriesKcal=null,powerW=null,strokes=null,stepRate=metrics?.cadence,strideM=metrics?.cadence?.takeIf { it>0 }?.let { rate -> metrics?.speedMps?.times(60)?.div(rate) })
                motion=true
            }
            "8811" -> if(state.value.machine in listOf(Machine.ELLIPTICAL,Machine.BIKE)) { metrics=Metrics(cadence=magnets?.let { Protocols.intervalCadence(data,it) }); motion=true }
            "ffe1","ffe4" -> {
                if(data.size>=11 && (data[0].toInt() and 255)==0xac && data[1].toInt()==4) {
                    val kind=data[3].toInt() and 255
                    val subtype=data[4].toInt() and 255
                    val range=Protocols.v1Range(data)
                    val profile="legacy_profile:kind=$kind subtype=$subtype range=${range?.let { "${it.min}..${it.max}" } ?: "unknown"}"
                    if(profile!=legacyProfile) { legacyProfile=profile; log(profile) }
                    val machine=when(kind) { 9 -> Machine.ROWER; 10 -> Machine.BIKE; 11 -> Machine.ELLIPTICAL; 12 -> Machine.TREADMILL; else -> state.value.machine }
                    val writable=range!=null && machine in setOf(Machine.ELLIPTICAL,Machine.BIKE) && characteristic("ffe0","ffe3")!=null
                    state.value=state.value.copy(machine=machine,range=range,writable=writable)
                }
                val parsed=Protocols.v1Metrics(data)
                if(parsed!=null) { template=data.copyOf(); templateAt=now; metrics=parsed; motion=true; state.value=state.value.copy(range=Protocols.v1Range(data)) }
            }
            "2acc" -> ftmsFeature=data.size>=8 && (data[4].toInt() and 4)!=0
            "2ad6" -> state.value=state.value.copy(range=Protocols.ftmsRange(data))
            "2ad1","2ad2","2ace","2acd" -> {
                metrics=Protocols.ftmsData(short,data); motion=true
                if(metrics!=null) state.value=state.value.copy(machine=when(short) { "2ad1" -> Machine.ROWER; "2ad2" -> Machine.BIKE; "2acd" -> Machine.TREADMILL; else -> Machine.ELLIPTICAL })
            }
            "2a37" -> metrics=Metrics(heartBpm=Protocols.heart(data))
            "2ad9" -> if(data.size>=3 && (data[0].toInt() and 255)==0x80 && (data[1].toInt() and 255)==expectedOpcode) {
                indication?.complete(data[2].toInt()==1); if(data[2].toInt()!=1) log("control_result:${data[1]}:${data[2]}")
            }
            "2ada" -> if(data.firstOrNull()?.let { it.toInt() and 255 }==0xff) { granted=false; state.value=state.value.copy(writable=false); log("control_lost") }
            "2a24","2a26" -> log("$short:${data.toString(Charsets.UTF_8).filter { !it.isISOControl() }.take(80)}")
        }
        metrics?.let { next -> state.value=state.value.copy(metrics=state.value.metrics.merge(next),motionAt=if(motion) now else state.value.motionAt,
            resistanceAt=if(next.resistance!=null) now else state.value.resistanceAt,
            heartAt=if(next.heartBpm!=null) now else state.value.heartAt,
            requested=if(next.resistance!=null && state.value.requested?.let { kotlin.math.abs(it-requireNotNull(next.resistance))<0.05 }==true) null else state.value.requested) }
    }
    private suspend fun ftms(bytes: ByteArray): Boolean {
        expectedOpcode=bytes[0].toInt() and 255; val response=CompletableDeferred<Boolean>(); indication=response
        return try { write("1826","2ad9",bytes) && withTimeout(5000) { response.await() } }
        catch(e: TimeoutCancellationException) { fail("control_timeout"); false }
        finally { if(indication===response) indication=null }
    }
    suspend fun resistance(value: Double): Boolean {
        val s=state.value; val range=s.range ?: return false
        if(s.phase!="ready" || !s.writable || s.busy || s.machine==Machine.TREADMILL || !range.contains(value) || SystemClock.elapsedRealtime()-lastWrite<700) return false
        state.value=s.copy(busy=true,requested=value); lastWrite=SystemClock.elapsedRealtime()
        return try {
            val ok=when(s.protocol) {
                Protocol.V2 -> {
                    if(!unlocked) unlocked=write("8800","88ff",byteArrayOf(0x11,0x82.toByte(),7))
                    unlocked && value%1.0==0.0 && write("8800","880f",Protocols.v2Resistance(value.toInt()))
                }
                Protocol.V1 -> template?.takeIf { SystemClock.elapsedRealtime()-templateAt<5000 }?.let { write("ffe0","ffe3",Protocols.v1Resistance(it,value.toInt())) } ?: false
                Protocol.FTMS -> { if(!granted) granted=ftms(byteArrayOf(0)); granted && ftms(Protocols.ftmsResistance(value)) }
                else -> false
            }
            if(!ok) { log("control_failed"); state.value=state.value.copy(requested=null) }; ok
        } finally { state.value=state.value.copy(busy=false) }
    }
    companion object { fun uuid(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb") }
}
private fun UUID.short() = toString().substring(4,8)
