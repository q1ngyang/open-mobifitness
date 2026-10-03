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
    val busy: Boolean = false, val writable: Boolean = false, val diagnostic: List<String> = emptyList(),
    val dataReceived: Boolean = false, val receivedPackets: Int = 0, val parsedPackets: Int = 0,
    val subscriptions: Int = 0, val lastReceiveAt: Long = 0, val controlTimedOut: Boolean = false,
    val resistanceFeedback: Boolean = true, val commandedResistance: Double? = null,
    val model: String = "", val deviceDetails: MobiDeviceDetails = MobiDeviceDetails()
)
@SuppressLint("MissingPermission") // Entry points are gated by runtime permission checks; revoked permissions are caught.
class BleClient(private val context: Context, private val scope: CoroutineScope,private val operationGapMs: Long=GattPlan.OPERATION_GAP_MS) {
    val state = MutableStateFlow(LinkState())
    val found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var connectionJob: Job? = null
    private var dataWatchdog: Job? = null
    private var feedbackJob: Job? = null
    private var periodicJob: Job? = null
    private var initializationJob: Job? = null
    var connectionId: Long = 0; private set
    var controlGeneration: Long = 0; private set
    private var scanJob: Job? = null
    private val operations = Mutex()
    private val controls = Mutex()
    private var pending: CompletableDeferred<Boolean>? = null
    private var pendingKey: String? = null
    private var indication: CompletableDeferred<Boolean>? = null
    private var expectedOpcode = -1
    private var mobi: MobiSession? = null
    private var mobiFtms: MobiFtmsSession? = null
    var userWeightKg = 70
    private var huantongLevel = 1
    private var huantongInitialized = false
    private var controlNotify = false
    private var officialFtms = false
    private var lastTrainingStatus: Int? = null
    private var initialized = false
    private var foldStateQueried = false
    private var operationEndedAt: Long? = null
    private var ftmsFeature = false
    private var granted = false
    private var unlocked = false
    private var lastWrite = 0L
    private var legacyProfile: String? = null
    private val callbackKinds=mutableSetOf<String>()
    private val rejectedFrames=mutableSetOf<String>()
    private val cccd = uuid("2902")
    private fun log(value: String) { org.openmobifitness.app.data.AppLog.event("ble",value); state.value=state.value.copy(diagnostic=(state.value.diagnostic+value).takeLast(160)) }
    // Vendor energy uses the per-second calculation level and unrounded model watts.
    fun legacyEnergyPower(): Double? = mobi?.energyPower ?: mobiFtms?.energyPower
    fun legacyEnergyRate(): Double? = mobi?.energyRate ?: mobiFtms?.energyRate
    fun resetTrainingCalculations() { mobi?.resetTrainingCalculations(); mobiFtms?.resetTrainingCalculations() }
    fun activeSecond() { mobi?.activeSecond(); mobiFtms?.activeSecond() }
    /** Cancel intent, not the current GATT operation: aborting an in-flight write tears down GATT. */
    fun invalidateControls() {
        controlGeneration++
        feedbackJob?.cancel()
        state.value=state.value.copy(requested=null,controlTimedOut=false)
    }
    /** Start required live reporting once. App recording/pause does not own this lifecycle. */
    suspend fun beginLiveUse(): Boolean = when {
        state.value.machine==Machine.TREADMILL -> true
        state.value.protocol==Protocol.V2 && mobi?.uploadMode in 2..3 -> trainingStatus(1)
        state.value.protocol==Protocol.FTMS && state.value.writable -> trainingStatus(1)
        else -> true
    }
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
            val services=result.scanRecord?.serviceUuids?.map { it.uuid.short() }?.toSet() ?: emptySet()
            if(name.startsWith("MB-",true) || name.startsWith("MOBI",true) || services.any { it in MobiProfiles.scanServices }) {
                val item=FoundDevice(result.device.address,name.take(120),result.rssi,"180d" in services && MobiProfiles.select(name,services)==Protocol.UNKNOWN && !name.startsWith("MB-") && !name.startsWith("MOBI"))
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
        invalidateControls()
        if(gatt!=null) org.openmobifitness.app.data.AppLog.event("disconnect",state.value.protocol.name)
        connectionId++; connectionJob?.cancel(); dataWatchdog?.cancel(); feedbackJob?.cancel(); periodicJob?.cancel(); initializationJob?.cancel(); pending?.complete(false); indication?.complete(false)
        val old=gatt; gatt=null; runCatching { old?.disconnect(); old?.close() }
        mobi=null; mobiFtms=null; ftmsFeature=false; granted=false; unlocked=false; legacyProfile=null; lastWrite=0
        huantongInitialized=false; huantongLevel=1; controlNotify=false; officialFtms=false
        lastTrainingStatus=null
        initialized=false; foldStateQueried=false
        operationEndedAt=null
        callbackKinds.clear(); rejectedFrames.clear()
        state.value=state.value.copy(phase="disconnected",range=null,metrics=Metrics(),motionAt=0,resistanceAt=0,heartAt=0,requested=null,busy=false,writable=false,dataReceived=false,controlTimedOut=false,commandedResistance=null,deviceDetails=MobiDeviceDetails())
    }
    private fun fail(reason: String) { log(reason); disconnect() }
    private fun complete(key: String,status: Int) {
        if(status!=BluetoothGatt.GATT_SUCCESS) log("gatt_status:$key:$status")
        if(pendingKey==key) pending?.complete(status==BluetoothGatt.GATT_SUCCESS)
    }
    private val callback=object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt,status: Int,newState: Int) { scope.launch {
            if(g !== gatt) return@launch
            if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED) { fail("disconnected:$status"); return@launch }
            if(newState==BluetoothProfile.STATE_CONNECTED) { state.value=state.value.copy(phase="discovering"); if(!runCatching { g.discoverServices() }.getOrDefault(false)) fail("discovery_failed") }
        } }
        override fun onServicesDiscovered(g: BluetoothGatt,status: Int) { scope.launch {
            if(g !== gatt) return@launch
            if(status!=0) { fail("discovery_failed:$status"); return@launch }
            // Discovery has finished. Each queued operation now has its own timeout;
            // a large V2 topology must not inherit the original connection deadline.
            connectionJob?.cancel()
            initializationJob?.cancel()
            initializationJob=scope.launch { try { initialize(g) } catch(e: SecurityException) { fail("permission_revoked") } }
        } }
        override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,status: Int) = receivedRead(g,c,value.copyOf(),status,"read_value")
        @Deprecated("Legacy callback") override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) {
            receivedRead(g,c,c.value?.copyOf() ?: byteArrayOf(),status,"read_legacy")
        }
        private fun receivedRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,status: Int,source: String) { scope.launch {
            if(g!==gatt) return@launch
            if(callbackKinds.add(source)) log("callback:$source")
            if(status==0) receive(c.uuid,value)
            complete("read:${c.uuid}",status)
        } }
        override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray) = changed(g,c,value.copyOf(),"notify_value")
        // Accept the callback actually delivered by the stack, not an SDK-based guess.
        // Snapshot characteristic.value before leaving the Bluetooth callback thread.
        @Deprecated("Legacy callback") override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic) { changed(g,c,c.value?.copyOf() ?: byteArrayOf(),"notify_legacy") }
        private fun changed(g: BluetoothGatt,c: BluetoothGattCharacteristic,value: ByteArray,source: String) { scope.launch {
            if(g!==gatt) return@launch
            if(callbackKinds.add(source)) log("callback:$source")
            receive(c.uuid,value)
        } }
        override fun onCharacteristicWrite(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) { scope.launch { if(g===gatt) complete("write:${c.uuid}",status) } }
        override fun onDescriptorWrite(g: BluetoothGatt,d: BluetoothGattDescriptor,status: Int) { scope.launch { if(g===gatt) complete("notify:${d.characteristic.uuid}",status) } }
        override fun onMtuChanged(g: BluetoothGatt,mtu: Int,status: Int) { scope.launch { if(g===gatt) complete("mtu",status) } }
    }
    private suspend fun operation(key: String,optional: Boolean=false,start: (BluetoothGatt)->Boolean): Boolean = operations.withLock {
        val current=gatt ?: return@withLock false
        operationEndedAt?.let { delay((it+operationGapMs-SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
        if(current!==gatt) return@withLock false
        val result=CompletableDeferred<Boolean>(); pending=result; pendingKey=key
        try {
            if(!start(current)) { log("gatt_rejected:$key"); return@withLock false }
            withTimeout(6000) { result.await() }.also { if(!it) log("gatt_failed:$key") }
        } catch(e: TimeoutCancellationException) { if(optional) log("gatt_timeout:$key") else fail("gatt_timeout:$key"); false }
        catch(e: CancellationException) { if(gatt===current) fail("gatt_cancelled:$key"); throw e }
        catch(e: SecurityException) { fail("permission_revoked"); false }
        finally {
            if(current===gatt) operationEndedAt=SystemClock.elapsedRealtime()
            if(pending===result) { pending=null; pendingKey=null }
        }
    }
    private fun characteristic(service: String,which: String) = gatt?.getService(uuid(service))?.getCharacteristic(uuid(which))
    private suspend fun read(c: BluetoothGattCharacteristic) = operation("read:${c.uuid}") { it.readCharacteristic(c) }
    private suspend fun subscribe(c: BluetoothGattCharacteristic): Boolean {
        val d=c.getDescriptor(cccd) ?: run { log("notify_missing_cccd:${c.uuid.short()}"); return false }
        val bytes=if(c.uuid==uuid("2ad9") || c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY==0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        log("notify_start:${c.uuid.short()}")
        val success=operation("notify:${c.uuid}") { g ->
            if(!g.setCharacteristicNotification(c,true)) false
            else if(Build.VERSION.SDK_INT>=33) g.writeDescriptor(d,bytes)==BluetoothStatusCodes.SUCCESS
            else { d.value=bytes; g.writeDescriptor(d) }
        }
        log("notify_${if(success) "enabled" else "failed"}:${c.uuid.short()}")
        return success
    }
    private suspend fun write(service: String,which: String,bytes: ByteArray,valid: () -> Boolean = { true }): Boolean {
        val c=characteristic(service,which) ?: return false
        val type=if(c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return operation("write:${c.uuid}") { g ->
            if(!valid()) false
            else {
                org.openmobifitness.app.data.AppLog.packet("tx",which,bytes)
                if(Build.VERSION.SDK_INT>=33) g.writeCharacteristic(c,bytes,type)==BluetoothStatusCodes.SUCCESS
                else { c.writeType=type; c.value=bytes; g.writeCharacteristic(c) }
            }
        }
    }
    private suspend fun initialize(g: BluetoothGatt) {
        g.services.forEach { s -> log("service:${s.uuid}"); s.characteristics.forEach { c ->
            log("  characteristic:${c.uuid} properties:${c.properties}")
            c.descriptors.forEach { log("    descriptor:${it.uuid}") }
        } }
        val endpoints=g.services.flatMap { service -> service.characteristics.map {
            GattEndpoint(service.uuid.short(),it.uuid.short(),it.properties)
        } }
        val protocol=MobiProfiles.select(state.value.name,endpoints.map { it.service }.toSet())
        mobi=if(protocol in setOf(Protocol.V1,Protocol.V2,Protocol.HUANTONG)) MobiSession(protocol,state.value.name) else null
        state.value=state.value.copy(protocol=protocol)
        officialFtms=protocol==Protocol.FTMS && MobiProfiles.officialFtms(state.value.name)
        mobiFtms=if(officialFtms) MobiFtmsSession(state.value.name) else null
        if(protocol==Protocol.FTMS) {
            val chars=endpoints.filter { it.service=="1826" }.map { it.characteristic }
            state.value=state.value.copy(machine=when { "2ace" in chars -> Machine.ELLIPTICAL; "2ad2" in chars -> Machine.BIKE; "2ad1" in chars -> Machine.ROWER; "2acd" in chars -> Machine.TREADMILL; else -> Machine.UNKNOWN })
        }
        var subscriptions=0
        var huantongSubscribed=false
        val delayed=mutableListOf<GattStep>()
        suspend fun execute(step: GattStep) {
            if(g!==gatt) return
            if(step.action==GattAction.MTU) { operation("mtu",optional=true) { it.requestMtu(128) }; return }
            val endpoint=step.endpoint ?: return
            val c=g.getService(uuid(endpoint.service))?.getCharacteristic(uuid(endpoint.characteristic)) ?: return
            when(step.action) {
                GattAction.REQUEST_CONTROL -> granted=write("1826","2ad9",byteArrayOf(0))
                GattAction.READ -> read(c)
                GattAction.NOTIFY -> {
                    val success=subscribe(c)
                    if(success) subscriptions++
                    if(endpoint.service=="1826" && endpoint.characteristic=="2ad9") controlNotify=success
                    if(protocol==Protocol.HUANTONG && endpoint.service=="fff0" && endpoint.characteristic=="fff1" && success && g===gatt) {
                        huantongSubscribed=true
                    }
                }
                else -> Unit
            }
        }
        val initializedAt=SystemClock.elapsedRealtime()
        for(step in GattPlan.initialization(protocol,state.value.name,endpoints)) {
            if(step.delayMs>0) delayed+=step else execute(step)
            if(g!==gatt) return
        }
        // MB-HW delays only FFE4, not the other characteristic operations.
        for(step in delayed) {
            delay((initializedAt+step.delayMs-SystemClock.elapsedRealtime()).coerceAtLeast(0))
            execute(step)
            if(g!==gatt) return
        }
        if(huantongSubscribed) {
            // The original success callback appends the weight write behind the already queued subscriptions.
            huantongInitialized=write("fff0","fff2",MobiCommands.huantongWeight(userWeightKg))
            if(g!==gatt) return
            if(huantongInitialized) periodicJob=scope.launch {
                var next=SystemClock.elapsedRealtime()+1000
                while(isActive && g===gatt) {
                    delay((next-SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    if(g!==gatt) break
                    if(!write("fff0","fff2",MobiCommands.huantongResistance(huantongLevel))) log("huantong_periodic_failed")
                    next+=1000
                    // Do not replay missed setpoints after a delayed transport callback.
                    if(next<=SystemClock.elapsedRealtime()) next=SystemClock.elapsedRealtime()+1000
                }
            }
        }
        updateCapabilities()
        initialized=true
        queryFoldState()
        // Aggregate-upload devices may start reporting only after the explicit training-start command.
        val canStartReporting=protocol==Protocol.V2 && mobi?.machine!=Machine.UNKNOWN && mobi?.uploadMode in 2..3 && subscriptions>0 && characteristic("8800","880f")!=null && characteristic("8800","88ff")!=null
        connectionJob?.cancel(); state.value=state.value.copy(
            phase=if(state.value.dataReceived || canStartReporting) "ready" else if(subscriptions>0) "awaiting_data" else "subscription_failed",
            subscriptions=subscriptions)
        log("initialization:subscriptions=$subscriptions data=${state.value.dataReceived} phase=${state.value.phase}")
        dataWatchdog=scope.launch {
            delay(10_000)
            if(g===gatt && !state.value.dataReceived) log("no_sensor_data:received=${state.value.receivedPackets} parsed=${state.value.parsedPackets}")
        }
    }
    private fun updateCapabilities() {
        val m=mobi
        if(m!=null) state.value=state.value.copy(machine=m.machine,range=m.range,resistanceFeedback=m.canReadResistance,
            model=m.profile.name,deviceDetails=m.details,commandedResistance=if(m.protocol==Protocol.HUANTONG && huantongInitialized) huantongLevel.toDouble() else state.value.commandedResistance)
        val writable=when(state.value.protocol) {
            Protocol.V2 -> m?.canWriteResistance==true && characteristic("8800","880f")!=null && characteristic("8800","88ff")!=null
            Protocol.V1 -> m?.canWriteResistance==true && characteristic("ffe0","ffe3")!=null
            Protocol.HUANTONG -> huantongInitialized && characteristic("fff0","fff2")!=null
            Protocol.FTMS -> state.value.range!=null && ftmsFeature && (controlNotify || officialFtms) && characteristic("1826","2ad9")!=null
            else -> false
        }
        state.value=state.value.copy(writable=writable && state.value.machine in setOf(Machine.BIKE,Machine.ELLIPTICAL,Machine.ROWER))
    }
    private fun queryFoldState() {
        val m=mobi ?: return
        if(!initialized || foldStateQueried || m.protocol!=Protocol.V1 || !m.details.supportFold) return
        foldStateQueried=true
        val connection=connectionId
        scope.launch { controls.withLock {
            if(connection==connectionId) write("ffe0","ffed",MobiCommands.v1Treadmill(0,0))
        } }
    }
    private fun receive(id: UUID,data: ByteArray) {
        val short=id.short(); val now=SystemClock.elapsedRealtime()
        if(short in setOf("ffe1","ffe4","ffea","ffeb","fff4","8811","8812","8813","8802","8805","8806","2ad1","2ad2","2ace","2acd","2ad9","2ada","fff1"))
            org.openmobifitness.app.data.AppLog.packet("rx",short,data)
        val sensor=short in setOf("ffe1","ffe4","ffea","ffeb","fff4","8811","8812","8813","8814","2ad1","2ad2","2ace","2acd","2a37","fff1")
        if(sensor) {
            state.value=state.value.copy(receivedPackets=state.value.receivedPackets+1,lastReceiveAt=now)
            if(state.value.receivedPackets==1) log("first_sensor_packet:$short length=${data.size}")
        }
        // Diagnostics intentionally omit names, addresses, serial number and raw workout samples.
        if(short in setOf("8802","8805","8806","2acc","2ad6")) log("capability:$short:${data.joinToString("") { "%02x".format(it) }}")
        var metrics: Metrics?=null; var motion=false
        when(short) {
            "8801","8802","8803","8804","8805","8806","8807","8808","880e","8811","8812","8813","8814","8901","8902",
            "ffe1","ffe4","ffea","ffeb","fff4","fff1" -> {
                val previousDetails=mobi?.details
                metrics=mobi?.receive(short,data)
                if(short=="880e" && data.isNotEmpty()) lastTrainingStatus=null
                motion=mobi?.lastMotion==true
                updateCapabilities()
                queryFoldState()
                if(previousDetails!=mobi?.details) log("device_status:${mobi?.details}")
                val model=mobi?.profile
                val profile="legacy_profile:kind=${model?.kind} subtype=${model?.subtype} mode=${mobi?.resistanceMode} range=${mobi?.range}"
                if(profile!=legacyProfile) { legacyProfile=profile; log(profile) }
                if(state.value.protocol==Protocol.V1 && metrics==null && rejectedFrames.size<8) {
                    val shape="unparsed_v1:$short length=${data.size} header=${data.firstOrNull()?.let { it.toInt() and 255 }} format=${data.getOrNull(1)?.let { it.toInt() and 255 }}"
                    if(rejectedFrames.add(shape)) log(shape)
                }
            }
            // These callbacks only log bytes in both vendor APKs; no invented parser.
            "88e1","8a01" -> org.openmobifitness.app.data.AppLog.packet("rx",short,data)
            "2acc" -> {
                ftmsFeature=data.size>=8 && (data[4].toInt() and 4)!=0
                mobiFtms?.metadata(short,data)
            }
            "2ad4","2ad5" -> mobiFtms?.metadata(short,data)
            "2ad6" -> {
                state.value=state.value.copy(range=if(officialFtms) Protocols.mobiFtmsRange(data) else Protocols.ftmsRange(data))
                mobiFtms?.range=state.value.range
            }
            "2ad1","2ad2","2ace","2acd" -> {
                metrics=if(officialFtms) mobiFtms?.receive(short,data) else Protocols.ftmsData(short,data)
                motion=metrics?.let { it.cadence!=null || it.speedMps!=null || it.stepRate!=null || it.strokes!=null }==true
                if(metrics!=null) state.value=state.value.copy(machine=when(short) { "2ad1" -> Machine.ROWER; "2ad2" -> Machine.BIKE; "2acd" -> Machine.TREADMILL; else -> Machine.ELLIPTICAL })
            }
            "2a37" -> metrics=Metrics(heartBpm=Protocols.heart(data))
            "2ad9" -> if(data.size>=3 && (data[0].toInt() and 255)==0x80 && (data[1].toInt() and 255)==expectedOpcode) {
                indication?.complete(data[2].toInt()==1); if(data[2].toInt()!=1) log("control_result:${data[1]}:${data[2]}")
            }
            "2ada" -> {
                mobiFtms?.metadata(short,data)
                if(data.firstOrNull()?.let { it.toInt() and 255 } in setOf(2,3,4)) lastTrainingStatus=null
                if(data.firstOrNull()?.let { it.toInt() and 255 }==0xff) { granted=false; state.value=state.value.copy(writable=false); log("control_lost") }
            }
            "2ad3","2ad7","2ad8" -> org.openmobifitness.app.data.AppLog.packet("rx",short,data)
            "2a24","2a26" -> log("$short:${data.toString(Charsets.UTF_8).filter { !it.isISOControl() }.take(80)}")
        }
        mobiFtms?.let { state.value=state.value.copy(deviceDetails=it.details) }
        if(short in setOf("2acc","2ad6")) updateCapabilities()
        metrics?.let { next ->
            val valid=next.copy(powerEstimated=false)!=Metrics()
            val confirmed=next.resistance?.let { measured -> state.value.requested?.let { kotlin.math.abs(it-measured)<0.05 } }==true
            if(confirmed) { feedbackJob?.cancel(); log("control_feedback:${next.resistance}") }
            state.value=state.value.copy(metrics=state.value.metrics.merge(next),motionAt=if(motion && valid) now else state.value.motionAt,
            resistanceAt=if(next.resistance!=null) now else state.value.resistanceAt,
            heartAt=if(next.heartBpm!=null) now else state.value.heartAt,
            dataReceived=state.value.dataReceived || valid,
            parsedPackets=state.value.parsedPackets+if(sensor && valid) 1 else 0,
            phase=if(valid && state.value.phase in setOf("awaiting_data","subscription_failed")) "ready" else state.value.phase,
            requested=if(confirmed) null else state.value.requested,controlTimedOut=if(confirmed) false else state.value.controlTimedOut)
        }
    }
    private suspend fun ftms(bytes: ByteArray,valid: () -> Boolean = { true }): Boolean {
        if(officialFtms) return write("1826","2ad9",bytes,valid)
        expectedOpcode=bytes[0].toInt() and 255; val response=CompletableDeferred<Boolean>(); indication=response
        return try { write("1826","2ad9",bytes,valid) && withTimeout(5000) { response.await() } }
        catch(e: TimeoutCancellationException) { fail("control_timeout"); false }
        finally { if(indication===response) indication=null }
    }
    suspend fun resistance(value: Double,generation: Long=controlGeneration): Boolean {
        val connection=connectionId
        return controls.withLock { if(connection==connectionId && generation==controlGeneration) controlResistance(value,generation) else false }
    }
    private suspend fun controlResistance(value: Double,generation: Long): Boolean {
        val s=state.value; val range=s.range ?: return false
        if(s.phase!="ready" || !s.writable || s.busy || s.machine==Machine.TREADMILL || !range.contains(value) || SystemClock.elapsedRealtime()-lastWrite<700) return false
        val connection=gatt
        val valid={ gatt===connection && generation==controlGeneration }
        feedbackJob?.cancel()
        state.value=s.copy(busy=true,requested=value,controlTimedOut=false); lastWrite=SystemClock.elapsedRealtime()
        return try {
            val ok=when(s.protocol) {
                Protocol.V2 -> {
                    if(!unlocked) {
                        val ok=write("8800","88ff",MobiCommands.unlock,valid)
                        if(gatt!==connection) return false
                        unlocked=ok
                    }
                    valid() && unlocked && value%1.0==0.0 && (mobi?.resistanceCommand(value.toInt())?.let { write("8800","880f",it,valid) } ?: false)
                }
                // These are device configuration bytes, not motion readings. Retain within
                // this connection so a stationary rider can adjust; disconnect clears them.
                Protocol.V1 -> value%1.0==0.0 && (mobi?.resistanceCommand(value.toInt())?.let { write("ffe0","ffe3",it,valid) } ?: false)
                Protocol.HUANTONG -> if(valid() && value%1.0==0.0 && mobi?.resistanceCommand(value.toInt())!=null) { huantongLevel=value.toInt(); mobi?.commandedResistance(huantongLevel); true } else false
                Protocol.FTMS -> {
                    if(officialFtms && value>25.5) false else {
                        if(!granted) {
                            val ok=ftms(byteArrayOf(0),valid)
                            if(gatt!==connection) return false
                            granted=ok
                        }
                        valid() && granted && ftms(if(officialFtms) MobiCommands.ftmsResistance(value) else Protocols.ftmsResistance(value),valid)
                    }
                }
                else -> false
            }
            if(!valid()) return false
            if(!ok) { log("control_failed"); state.value=state.value.copy(requested=null) }
            else if(!state.value.resistanceFeedback) state.value=state.value.copy(requested=null,commandedResistance=value)
            else if(state.value.requested!=null) feedbackJob=scope.launch {
                delay(5000)
                if(valid() && state.value.requested==value) {
                    log("control_feedback_timeout:requested=$value")
                    state.value=state.value.copy(requested=null,controlTimedOut=true)
                }
            }
            ok
        } finally { if(gatt===connection) state.value=state.value.copy(busy=false) }
    }
    fun resistanceDelayMs()=(700-(SystemClock.elapsedRealtime()-lastWrite)).coerceAtLeast(0)
    suspend fun trainingStatus(status: Int): Boolean {
        val connection=connectionId
        return controls.withLock { if(connection==connectionId) controlTrainingStatus(status) else false }
    }
    private suspend fun controlTrainingStatus(status: Int): Boolean {
        // Starting a recording is not an instruction to start a treadmill motor.
        // Motor/speed/incline encoders are verified in core, but require a dedicated control UI.
        if(state.value.machine==Machine.TREADMILL) return true
        if(state.value.protocol==Protocol.FTMS) {
            if((lastTrainingStatus ?: mobiFtms?.machineStatus ?: 0)==status || characteristic("1826","2ad9")==null) return true
            val connection=gatt ?: return false
            if(!granted) {
                val ok=ftms(byteArrayOf(0))
                if(gatt!==connection) return false
                granted=ok
            }
            val ok=granted && gatt===connection && ftms(MobiCommands.ftmsStatus(status))
            if(gatt!==connection) return false
            if(ok) lastTrainingStatus=status
            return ok
        }
        val m=mobi ?: return true
        if(m.protocol!=Protocol.V2 || m.uploadMode !in 2..3 || (lastTrainingStatus ?: m.machineStatus)==status) return true
        if(characteristic("8800","880f")==null || characteristic("8800","88ff")==null) return state.value.dataReceived
        val connection=gatt ?: return false
        if(!unlocked) {
            val ok=write("8800","88ff",MobiCommands.unlock)
            if(gatt!==connection) return false
            unlocked=ok
        }
        val ok=unlocked && write("8800","880f",MobiCommands.v2Status(status))
        if(gatt!==connection) return false
        if(ok) lastTrainingStatus=status
        return ok
    }
    companion object { fun uuid(short: String): UUID = UUID.fromString(if(short.length==4) "0000$short-0000-1000-8000-00805f9b34fb" else short) }
}
private fun UUID.short(): String {
    val value=toString()
    return if(value.startsWith("0000") && value.substring(8)=="-0000-1000-8000-00805f9b34fb") value.substring(4,8) else value
}
