package org.openmobifitness.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmobifitness.app.ble.*
import org.openmobifitness.app.data.*
import org.json.JSONObject
import java.util.UUID
import org.openmobifitness.core.*
import android.os.SystemClock
import java.time.Instant
import java.util.Locale

class OpenMobiApp : Application() {
    lateinit var controller: Controller; private set
    override fun onCreate() { super.onCreate(); org.openmobifitness.app.data.AppLog.initialize(this); controller=Controller(this) }
}
fun Context.localized(): Context {
    if(android.os.Build.VERSION.SDK_INT>=33) return this // Framework owns per-app locale configuration.
    val tag=getSharedPreferences("preferences",Context.MODE_PRIVATE).getString("language","") ?: ""
    if(tag.isEmpty()) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
}
data class StartRequest(val id: String=UUID.randomUUID().toString(),val generation: Long,val connection: Long,val machine: Machine,val workoutId: String?)

enum class UseMode { IDLE, CONTROL_ONLY, RECORDING, PAUSED }
data class ExerciseState(
    val session: Session? = null, val paused: Boolean = false, val selected: Workout? = null,
    val stage: Int = 0, val progress: Float = 0f, val automatic: Boolean = true, val done: Boolean = false,
    val pendingResistance: Double? = null, val strokeCount: Int? = null, val remainingMs: Long? = null, val demoMachine: Machine = Machine.ELLIPTICAL,
    val demo: Boolean = false, val metrics: Metrics = Metrics(), val error: Int? = null, val errorRow: Int? = null, val ready: Boolean = false,
    val jumpCount: Int? = null, val repetitions: Int? = null, val jumpInterruptions: Int? = null,
    val controlOnly: Boolean = false, val starting: Boolean = false,
    val frequencyHint: RangePosition=RangePosition.UNAVAILABLE, val heartHint: RangePosition=RangePosition.UNAVAILABLE,
    val hintsMuted: Boolean=false, val hintEvent: Long=0
) {
    val mode: UseMode get()=when { session!=null -> if(paused) UseMode.PAUSED else UseMode.RECORDING; controlOnly -> UseMode.CONTROL_ONLY; else -> UseMode.IDLE }
    val inUse: Boolean get()=mode!=UseMode.IDLE
}
class Controller(val app: Application,
    val scope: CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    tickAutomatically: Boolean = true
) {
    val repo=Repository(app)
    val ble=BleClient(app,scope)
    val heart=BleClient(app,scope)
    val state=MutableStateFlow(ExerciseState())
    val finishedSession=MutableStateFlow(app.getSharedPreferences("preferences",Context.MODE_PRIVATE).getString("finished_detail_id",null))
    val prefs=repo.profilePreferences
    val currentUser=MutableStateFlow<UserProfile?>(null)
    val pendingStart=MutableStateFlow<StartRequest?>(null)
    val userPicker=MutableStateFlow(false)
    val identityPolicy=MutableStateFlow(runCatching { IdentityPolicy.valueOf(repo.preferences.getString("identity_policy",null) ?: "EACH_RECORDING") }.getOrDefault(IdentityPolicy.EACH_RECORDING))
    val activeHints=MutableStateFlow(HintPreferences())
    val historyScope=MutableStateFlow("current")
    val historyRequest=MutableStateFlow<HistoryQuery?>(null)
    private var identityGeneration=0L
    private var startupConfirmed=false
    private val hintThrottle=SessionHintThrottle()
    val theme=MutableStateFlow(prefs.getString("theme","system") ?: "system")
    val imperial=MutableStateFlow(prefs.getBoolean("imperial",false))
    val display=DisplayPreferences(prefs)
    val local=LocalPreferences(prefs)
    val backup=org.openmobifitness.app.data.BackupManager(app,repo,repo.preferences,scope,::reloadPreferences,::restoreOperation)
    private var hintKey: Triple<String?,Int,HintPreferences>?=null
    private val frequencyHint=RangeHint()
    private val heartHint=RangeHint()
    private val sessionLock=Mutex()
    private var engine: TrainingEngine?=null
    private var lastTick=clock()
    private var lastAuto=0L
    private var totals=TelemetryTotals()
    private var demoStrokes=0.0
    private var demoResistance=1.0
    private var manualControl: Job?=null
    private var useGeneration=0L
    private var observedConnection=ble.connectionId
    var serviceStarted=false
    init {
        scope.launch { try { repo.load(); reloadPreferences(); if(finishedSession.value!=null && repo.history.session(finishedSession.value!!)==null) dismissResult(); state.value=state.value.copy(ready=true) } catch(e: Exception) { error(R.string.storage_failed) } }
        if(tickAutomatically) scope.launch {
            delay(1000)
            while(isActive) {
                val startedAt=clock()
                sessionLock.withLock { tick() }
                // Database and GATT work are part of the one-second period.
                // A slow iteration never queues catch-up writes to the equipment.
                delay((1000-(clock()-startedAt)).coerceAtLeast(1))
            }
        }
    }
    fun error(id: Int?,row: Int?=null) { if(id!=null) org.openmobifitness.app.data.AppLog.event("ui_error",app.resources.getResourceEntryName(id)); state.value=state.value.copy(error=id,errorRow=row) }
    fun dismissResult() { finishedSession.value=null; prefs.edit().remove("finished_detail_id").apply() }
    fun setTheme(value: String) { prefs.edit().putString("theme",value).apply(); theme.value=value }
    fun setImperial(value: Boolean) { prefs.edit().putBoolean("imperial",value).apply(); imperial.value=value }
    fun reloadPreferences() {
        val previous=currentUser.value
        currentUser.value=repo.users.value.firstOrNull { it.id==prefs.userId && it.available }
        val weightChanged=previous!=null && previous.id==currentUser.value?.id && previous.weightKg!=currentUser.value?.weightKg
        display.reload(); local.reload(); theme.value=prefs.getString("theme","system") ?: "system"; imperial.value=prefs.getBoolean("imperial",false)
        display.weight.value=currentUser.value?.weightKg ?: 70.0; display.met.value=currentUser.value?.met ?: 5.0
        ble.userWeightKg=display.weight.value.toInt()
        if(weightChanged && ble.state.value.protocol==Protocol.HUANTONG && ble.state.value.phase!="disconnected") { invalidateControl(); ble.disconnect(); state.value=state.value.copy(metrics=Metrics()); error(R.string.user_reconnect_required) }
        identityPolicy.value=runCatching { IdentityPolicy.valueOf(repo.preferences.getString("identity_policy",null) ?: "EACH_RECORDING") }.getOrDefault(IdentityPolicy.EACH_RECORDING)
        if(android.os.Build.VERSION.SDK_INT>=33 && repo.preferences.contains("language")) app.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(repo.preferences.getString("language","").orEmpty())
    }
    val identityLocked get()=state.value.session!=null || state.value.starting || repo.maintenance.value || backup.status.value?.phase in setOf(TransferPhase.RESTORING,TransferPhase.FINALIZING)
    val dataActionsAllowed get()=state.value.ready && !identityLocked && pendingStart.value==null
    fun setIdentityPolicy(policy: IdentityPolicy) { repo.preferences.edit().putString("identity_policy",policy.name).apply(); identityPolicy.value=policy }
    fun cancelStart() { pendingStart.value=null }
    fun discardAvatars(candidates: Set<String>)=scope.launch { sessionLock.withLock {
        runCatching { repo.discardAvatars(candidates) }.onFailure { org.openmobifitness.app.data.AppLog.exception("avatar_cleanup",it) }
    } }
    fun historyOwner(): String = if(historyScope.value=="current") currentUser.value?.id ?: "none" else historyScope.value
    suspend fun idleOperation(action: suspend ()->Unit) {
        check(backup.status.value?.phase !in setOf(TransferPhase.RESTORING,TransferPhase.FINALIZING)) { "identity_locked" }
        restoreOperation(action)
    }
    private suspend fun restoreOperation(action: suspend ()->Unit) = sessionLock.withLock {
        check(state.value.ready && state.value.session==null && !state.value.starting && !repo.maintenance.value && pendingStart.value==null) { "identity_locked" }
        repo.maintenance.value=true
        try { action() } finally { repo.maintenance.value=false; reloadPreferences() }
    }
    suspend fun saveProfile(profile: UserProfile,requestId: String?=null)=sessionLock.withLock {
        check(state.value.ready && !identityLocked && (pendingStart.value==null || pendingStart.value?.id==requestId)) { "identity_locked" }
        repo.maintenance.value=true
        try { repo.saveUser(profile); reloadPreferences() } finally { repo.maintenance.value=false }
    }
    fun switchUser(id: String)=scope.launch { sessionLock.withLock {
        if(identityLocked) { error(R.string.identity_locked); return@withLock }
        pendingStart.value=null; repo.maintenance.value=true
        try { prepareUser(id); startupConfirmed=true } catch(_: Exception) { error(R.string.storage_failed) } finally { repo.maintenance.value=false }
    } }
    private suspend fun prepareUser(id: String) {
        val user=withContext(Dispatchers.IO) { PortablePreferences.export(prefs.forUser(id)); repo.db.records().user(id)?.model()?.takeIf { it.available } } ?: error("user_required")
        if(currentUser.value?.id==id) { currentUser.value=user; return }
        withContext(Dispatchers.IO) { prefs.select(id) }
        identityGeneration++; invalidateControl(); heart.disconnect(); ble.clearUserContext(); totals.rebase()
        state.value=state.value.copy(selected=null,metrics=Metrics(),frequencyHint=RangePosition.UNAVAILABLE,heartHint=RangePosition.UNAVAILABLE,hintsMuted=false)
        reloadPreferences(); ble.userWeightKg=(user.weightKg ?: 70.0).toInt()
        if(ble.state.value.protocol==Protocol.HUANTONG && ble.state.value.phase!="disconnected") { ble.disconnect(); error(R.string.user_reconnect_required) }
    }
    fun select(workout: Workout?) { if(!identityLocked) { pendingStart.value=null; state.value=state.value.copy(selected=workout) } }
    fun muteHints() { state.value=state.value.copy(hintsMuted=!state.value.hintsMuted) }
    fun setDemo(enabled: Boolean,machine: Machine=Machine.ELLIPTICAL) {
        if(!BuildConfig.DEBUG || state.value.session!=null || state.value.starting) return
        pendingStart.value=null
        invalidateControl()
        ble.disconnect(); heart.disconnect(); demoResistance=1.0; demoStrokes=0.0
        state.value=state.value.copy(demo=enabled,controlOnly=false,demoMachine=machine,metrics=if(enabled) Metrics(cadence=0.0,resistance=1.0,distanceM=0.0,strokes=if(machine==Machine.ROWER) 0 else null) else Metrics())
    }
    fun connect(device: FoundDevice) {
        if(device.heart) { heart.connect(device); return }
        pendingStart.value=null
        if(state.value.starting || (state.value.session!=null && (state.value.demo || device.address!=ble.state.value.address))) { error(R.string.workout_machine_changed); return }
        invalidateControl()
        state.value=state.value.copy(demo=false,paused=state.value.session!=null,pendingResistance=null)
        totals.rebase()
        prefs.edit().putString("last_address",device.address).putString("last_name",device.name).apply()
        ble.userWeightKg=display.weight.value.toInt()
        ble.connect(device)
        observedConnection=ble.connectionId
    }
    fun range() = if(state.value.demo) if(state.value.demoMachine in setOf(Machine.ELLIPTICAL,Machine.BIKE)) ResistanceRange(1.0,24.0) else null else ble.state.value.range
    fun controlResistance() = if(state.value.demo) demoResistance else ble.state.value.metrics.resistance ?: ble.state.value.commandedResistance
    fun displayMachine() = if(state.value.demo) state.value.demoMachine else ble.state.value.machine.takeIf { it!=Machine.UNKNOWN } ?: state.value.session?.machine ?: Machine.UNKNOWN
    fun canStart() = state.value.ready && (state.value.demo || ble.state.value.phase=="ready") &&
        (state.value.session==null || state.value.session?.machine==displayMachine())
    private fun invalidateControl() {
        useGeneration++
        manualControl?.cancel(); manualControl=null
        ble.invalidateControls()
        state.value=state.value.copy(pendingResistance=null)
    }
    fun enterControl() = scope.launch { sessionLock.withLock {
        if(state.value.session!=null || state.value.starting || !canStart()) { error(R.string.connect_first); return@withLock }
        if(state.value.controlOnly) return@withLock
        invalidateControl()
        state.value=state.value.copy(starting=true)
        try {
            if(!state.value.demo && !ble.beginLiveUse()) { error(R.string.control_unavailable); return@withLock }
            if(!canStart()) { error(R.string.connection_lost); return@withLock }
            dismissResult(); lastTick=clock()
            state.value=state.value.copy(controlOnly=true,selected=null,paused=false,automatic=false,error=null,hintsMuted=false)
        } finally { state.value=state.value.copy(starting=false) }
    } }
    fun exitControl() {
        if(state.value.session!=null || state.value.starting) return
        invalidateControl()
        state.value=state.value.copy(controlOnly=false,paused=false,selected=null,error=null)
        ble.disconnect(); heart.disconnect()
    }
    fun disconnectEquipment() {
        pendingStart.value=null
        invalidateControl(); totals.rebase(); ble.disconnect()
        if(state.value.session!=null) state.value=state.value.copy(paused=true,error=R.string.connection_lost)
    }
    fun start(): Job {
        val generation=identityGeneration; val blocked=identityLocked
        return scope.launch { sessionLock.withLock {
            if(blocked || generation!=identityGeneration || identityLocked || pendingStart.value!=null || !canStart()) { if(!canStart()) error(R.string.connect_first); return@withLock }
            val request=StartRequest(generation=identityGeneration,connection=ble.connectionId,machine=displayMachine(),workoutId=state.value.selected?.id)
            if(currentUser.value==null || identityPolicy.value==IdentityPolicy.EACH_RECORDING || identityPolicy.value==IdentityPolicy.STARTUP && !startupConfirmed) pendingStart.value=request
            else beginRecording()
        } }
    }
    fun confirmStart(requestId: String,userId: String)=scope.launch { sessionLock.withLock {
        val request=pendingStart.value ?: return@withLock
        if(request.id!=requestId) return@withLock
        if(request.generation!=identityGeneration || request.connection!=ble.connectionId || request.machine!=displayMachine() || request.workoutId!=state.value.selected?.id || identityLocked) { pendingStart.value=null; return@withLock }
        try {
            val plan=state.value.selected
            repo.maintenance.value=true
            try { prepareUser(userId) } finally { repo.maintenance.value=false }
            if(plan!=null && !WorkoutPolicy.compatible(plan,displayMachine(),userId)) { pendingStart.value=null; error(R.string.workout_machine_changed); return@withLock }
            state.value=state.value.copy(selected=plan)
            pendingStart.value=null; startupConfirmed=true
            beginRecording()
        } catch(e: Exception) { pendingStart.value=null; error(R.string.storage_failed) }
    } }
    private fun capabilitySnapshot(): String {
        val link=ble.state.value; val range=range()
        return JSONObject().put("version",1).put("machine",displayMachine().name).put("model",link.model).put("protocol",if(state.value.demo) "DEMO" else link.protocol.name).put("evidence","driver-v0.3.0")
            .put("resistanceWrite",if((state.value.demo || link.writable) && range!=null) "supported" else "unknown")
            .put("speedWrite","unsupported").put("inclineWrite","unsupported")
            .put("resistanceFeedback",if(link.protocol==Protocol.HUANTONG) "unsupported" else if(range!=null && link.resistanceFeedback) "supported" else "unknown")
            .put("units",JSONObject().put("frequency",if(displayMachine() in setOf(Machine.ROWER,Machine.TREADMILL)) "spm" else "rpm").put("speed","m/s").put("incline","percent").put("resistance","level").put("power","W").put("force","N").put("load","kg"))
            .put("observed",JSONObject().apply { SeriesMetric.entries.forEach { metric -> put(metric.name,if(metric.value(state.value.metrics)!=null) "supported" else "unknown") } })
            .put("resistanceMin",range?.min).put("resistanceMax",range?.max).put("resistanceIncrement",range?.increment).toString()
    }
    private suspend fun beginRecording() {
        if(state.value.session!=null || state.value.starting || !canStart()) { error(R.string.connect_first); return }
        val user=currentUser.value?.takeIf { it.available } ?: return
        if(state.value.selected?.let { !WorkoutPolicy.compatible(it,displayMachine(),user.id) }==true) { error(R.string.workout_machine_changed); return }
        if(state.value.selected?.steps?.any { (it.condition==Condition.DISTANCE && state.value.metrics.distanceM==null) || (it.condition==Condition.STROKES && state.value.metrics.strokes==null) }==true) {
            error(R.string.sensor_required); return
        }
        if(!state.value.inUse) state.value=state.value.copy(hintsMuted=false)
        invalidateControl()
        state.value=state.value.copy(starting=true)
        try {
        if(!state.value.demo && !ble.beginLiveUse()) { error(R.string.control_unavailable); return }
        dismissResult()
        state.value.selected?.let { plan -> state.value=state.value.copy(selected=plan.copy(steps=plan.steps.mapIndexed { i,step -> if(step.id.isEmpty()) step.copy(id=UUID.nameUUIDFromBytes("${plan.id}/$i".toByteArray()).toString()) else step })) }
        val s=Session(device=if(state.value.demo) "OpenMOBI Demo" else ble.state.value.name,
            machine=if(state.value.demo) state.value.demoMachine else ble.state.value.machine,
            protocol=if(state.value.demo) Protocol.DEMO else ble.state.value.protocol,demo=state.value.demo,weightKg=user.weightKg ?: 70.0,met=user.met,ownerUserId=user.id,startedUserId=user.id,startedUserName=user.name,weightSource=if(user.weightKg==null) "default" else if(user.id==LEGACY_USER_ID) "legacy" else "user",metSource=user.metSource,identityVersion=1,workoutSnapshot=state.value.selected?.let { Exchange.workouts(listOf(it)) }.orEmpty(),capabilitySnapshot=capabilitySnapshot(),enteredStageIds=state.value.selected?.steps?.firstOrNull()?.id.orEmpty(),workoutId=state.value.selected?.id ?: "free",workoutTitle=state.value.selected?.let { app.localized().workoutName(it) } ?: app.localized().getString(R.string.free_training))
        try { repo.save(s) } catch(e: Exception) {
            error(R.string.storage_failed); return
        }
        engine=state.value.selected?.let { TrainingEngine(it) }; hintThrottle.reset(); hintKey=null; frequencyHint.reset(); heartHint.reset()
        lastTick=clock(); lastAuto=0L
        totals=TelemetryTotals(state.value.metrics)
        org.openmobifitness.app.data.AppLog.event("session_start","${s.machine} ${s.protocol} demo=${s.demo}")
        engine?.resetBaseline(0,0.0,0)
        val connected=state.value.demo || ble.state.value.phase=="ready"
        state.value=state.value.copy(session=s,controlOnly=false,pendingResistance=null,strokeCount=null,jumpCount=null,repetitions=null,jumpInterruptions=null,paused=!connected,stage=0,progress=0f,remainingMs=engine?.remainingMs(0),automatic=range()!=null && (state.value.demo || ble.state.value.writable),done=false,error=if(connected) null else R.string.connection_lost)
        } finally { state.value=state.value.copy(starting=false) }
    }
    fun pauseResume() = changePause(state.value.session?.id,false)
    fun pauseForServiceLoss(sessionId: String) = changePause(sessionId,true)
    private fun changePause(sessionId: String?,pauseOnly: Boolean) = scope.launch { sessionLock.withLock {
        if(sessionId==null || state.value.session?.id!=sessionId || (pauseOnly && state.value.paused)) return@withLock
        if(state.value.session?.machine!=displayMachine()) { error(R.string.workout_machine_changed); return@withLock }
        if(state.value.paused && !canStart()) { error(R.string.connect_first); return@withLock }
        invalidateControl()
        // Pause the app's record, not the equipment's reporting or live calculation.
        if(state.value.paused && !state.value.demo && !ble.beginLiveUse()) { error(R.string.control_unavailable); return@withLock }
        // A pause shorter than the sampling interval may have no paused tick.
        // Rebase cumulative device counters before resuming, so that gap is never charged.
        if(state.value.paused) totals.rebase()
        val now=clock()
        val session=state.value.session!!.let { if(state.value.paused) it else it.copy(elapsedMs=it.elapsedMs+(now-lastTick).coerceAtLeast(0)) }
        lastTick=now; state.value=state.value.copy(session=session,paused=!state.value.paused,remainingMs=engine?.remainingMs(session.elapsedMs),error=null)
        org.openmobifitness.app.data.AppLog.event("session_pause","${state.value.paused}")
        try { repo.save(session) } catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed) }
    } }
    fun finish() = scope.launch { sessionLock.withLock {
        val stored=state.value.session ?: return@withLock
        invalidateControl()
        val s=if(state.value.paused) stored else stored.copy(elapsedMs=stored.elapsedMs+(clock()-lastTick).coerceAtLeast(0))
        try { repo.save(s.copy(end=Instant.now().toString(),status=if(state.value.done) "completed" else "stopped")) }
        catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed); return@withLock }
        org.openmobifitness.app.data.AppLog.event("session_finish","duration_ms=${s.elapsedMs}")
        prefs.edit().putString("finished_detail_id",s.id).apply()
        // A completed recording ends this use, while the existing Bluetooth links remain available.
        state.value=state.value.copy(session=null,controlOnly=false,selected=null,pendingResistance=null,automatic=false,paused=false,done=false,stage=0,progress=0f,remainingMs=null,strokeCount=null,jumpCount=null,repetitions=null,jumpInterruptions=null,frequencyHint=RangePosition.UNAVAILABLE,heartHint=RangePosition.UNAVAILABLE,error=null,errorRow=null); engine=null
        finishedSession.value=s.id
    } }
    fun automaticControl(enabled: Boolean) {
        if(state.value.session==null || (enabled && (state.value.selected==null || state.value.done))) return
        invalidateControl()
        state.value=state.value.copy(automatic=enabled,pendingResistance=null)
        lastAuto=0
    }
    fun resumeAutomatic() = automaticControl(true)
    fun adjust(delta: Int) {
        val range=range() ?: return
        val current=state.value.pendingResistance ?: ble.state.value.requested ?: controlResistance() ?: return
        adjustTo(range.next(current,delta))
    }
    fun adjustTo(value: Double) {
        val range=range() ?: return
        if(!value.isFinite() || !state.value.inUse || state.value.starting) return
        if(!state.value.demo && (ble.state.value.phase!="ready" || ble.state.value.busy)) return
        val target=range.next(value,0)
        val connection=ble.connectionId; val generation=useGeneration; val control=ble.controlGeneration; val demo=state.value.demo
        manualControl?.cancel()
        state.value=state.value.copy(automatic=false,pendingResistance=target)
        manualControl=scope.launch {
            if(!demo) delay(ble.resistanceDelayMs())
            // A pending slider release must never outlive its connection or workout.
            if(connection!=ble.connectionId || generation!=useGeneration || demo!=state.value.demo || !state.value.inUse) return@launch
            // Let an already dispatched GATT operation finish. The generation gate at
            // the actual write still rejects an obsolete request waiting in its queue.
            try { withContext(NonCancellable) { setResistance(target,control,generation) } }
            finally { if(generation==useGeneration && state.value.pendingResistance==target) state.value=state.value.copy(pendingResistance=null) }
        }
    }
    private suspend fun setResistance(value: Double,control: Long=ble.controlGeneration,generation: Long=useGeneration) {
        if(generation!=useGeneration || !state.value.inUse) return
        if(state.value.demo) { demoResistance=value; state.value=state.value.copy(metrics=state.value.metrics.copy(resistance=value)) }
        else if(!ble.resistance(value,control) && generation==useGeneration) error(R.string.control_unavailable)
    }
    internal suspend fun tick() {
        val now=clock(); val delta=(now-lastTick).coerceAtLeast(0); lastTick=now
        if(observedConnection!=ble.connectionId) { observedConnection=ble.connectionId; invalidateControl(); totals.rebase() }
        val old=state.value
        val current=old.session
        listOf(ble.state.value to false,heart.state.value to true).forEach { (link,isHeart) ->
            if(link.phase=="ready" && link.address.isNotBlank()) local.remember(SavedDevice(link.address,link.name,link.machine,isHeart))
        }
        if(!old.demo && old.inUse && ble.state.value.phase=="ready") ble.activeSecond()
        var metrics=if(old.demo) {
            val sample=DemoTelemetry.sample(old,delta,demoResistance,demoStrokes)
            demoStrokes=sample.second
            sample.first
        } else {
            val link=ble.state.value
            link.metrics.copy(cadence=link.metrics.cadence.takeIf { now-link.motionAt<5000 },speedMps=link.metrics.speedMps.takeIf { now-link.motionAt<5000 },
                powerW=link.metrics.powerW.takeIf { now-link.motionAt<5000 },stepRate=link.metrics.stepRate.takeIf { now-link.motionAt<5000 },forceN=link.metrics.forceN.takeIf { now-link.motionAt<5000 },heartBpm=heart.state.value.metrics.heartBpm.takeIf { now-heart.state.value.heartAt<10000 }
                    ?: link.metrics.heartBpm.takeIf { now-link.heartAt<10000 })
        }
        state.value=state.value.copy(metrics=metrics)
        if(ble.state.value.phase=="ready" && state.value.error==R.string.connection_lost) state.value=state.value.copy(error=null)
        if(current==null) { updateHints(now,metrics); return }
        if(!old.demo && (ble.state.value.phase!="ready" || ble.state.value.machine!=current.machine)) {
            totals.rebase()
            if(!old.paused) state.value=state.value.copy(paused=true,error=R.string.connection_lost)
            updateHints(now,metrics); return
        }
        totals.update(metrics,delta,!old.paused,current.machine,current.protocol,current.weightKg ?: 70.0,current.met ?: 5.0,if(metrics.cadence!=null) ble.legacyEnergyPower() else null,if(metrics.cadence!=null) ble.legacyEnergyRate() else null)
        if(old.paused) { updateHints(now,metrics); return }
        var session=current.copy(elapsedMs=current.elapsedMs+delta,distanceM=totals.distanceM ?: current.distanceM,
            caloriesKcal=totals.caloriesKcal,energyModel=totals.energyModel,caloriesEstimated=totals.caloriesEstimated,distanceEstimated=totals.distanceEstimated)
        val training=engine
        val previousStage=training?.index
        training?.tick(session.elapsedMs,totals.distanceM,totals.strokes)
        if(training!=null) session=session.copy(enteredStageIds=training.workout.steps.take(training.index+1).joinToString(";") { it.id })
        if(training?.index!=previousStage) org.openmobifitness.app.data.AppLog.event("stage","${training?.index}")
        state.value=state.value.copy(session=session,strokeCount=totals.strokes,jumpCount=totals.jumps,repetitions=totals.repetitions,jumpInterruptions=totals.jumpInterruptions,stage=training?.index ?: 0,
            progress=training?.progress(session.elapsedMs,totals.distanceM,totals.strokes)?.toFloat() ?: 0f,
            remainingMs=training?.remainingMs(session.elapsedMs),done=training?.done ?: false)
        updateHints(now,metrics)
        try { repo.save(session,Sample(session.id,session.elapsedMs,metrics.copy(distanceM=totals.distanceM,strokes=totals.strokes,caloriesKcal=totals.caloriesKcal,stepCount=totals.steps,jumpCount=totals.jumps,repetitions=totals.repetitions,jumpInterruptions=totals.jumpInterruptions))) }
        catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed); org.openmobifitness.app.data.AppLog.exception("recording",e); return }
        if(training!=null && !training.done && state.value.automatic && now-lastAuto>=2000) {
            val percent=training.workout.steps[training.index].resistancePercent
            val range=range(); val actual=controlResistance()
            if(percent!=null && range!=null && actual!=null && (old.demo || ble.state.value.requested==null)) {
                val target=range.percent(percent)
                if(kotlin.math.abs(target-actual)>=range.increment*0.5) {
                    lastAuto=now
                    setResistance(range.next(actual,if(target>actual) 1 else -1))
                }
            }
        }
    }
    private fun updateHints(now: Long,metrics: Metrics) {
        val state=state.value; val plan=state.selected; val stage=plan?.steps?.getOrNull(state.stage)
        val frequency=stage?.frequency?.resolve(plan.hints.frequency) ?: PersonalRange()
        val heart=stage?.heart?.resolve(plan.hints.heart) ?: PersonalRange()
        val machine=displayMachine()
        val config=HintPreferences(mapOf(machine to frequency),heart,plan?.hints?.sound==true,plan?.hints?.vibration==true)
        activeHints.value=config
        val key=Triple(plan?.id,state.stage,config)
        if(hintKey!=key) { hintKey=key; frequencyHint.reset(); heartHint.reset() }
        val active=state.session!=null && !state.paused && !state.done && (state.demo || ble.state.value.phase=="ready")
        val value=when(machine) { Machine.TREADMILL -> metrics.stepRate; Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.JUMP_ROPE -> metrics.cadence; else -> null }
        val f=frequencyHint.update(now,value,frequency,active); val h=heartHint.update(now,metrics.heartBpm?.toDouble(),heart,active)
        val alert=hintThrottle.accept(now,!state.hintsMuted && (f.notify || h.notify))
        this.state.value=this.state.value.copy(frequencyHint=f.position,heartHint=h.position,hintEvent=state.hintEvent+if(alert) 1 else 0)
    }

}
