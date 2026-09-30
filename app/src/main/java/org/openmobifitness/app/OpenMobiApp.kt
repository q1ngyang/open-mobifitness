package org.openmobifitness.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmobifitness.app.ble.*
import org.openmobifitness.app.data.Repository
import org.openmobifitness.core.*
import android.os.SystemClock
import java.time.Instant
import java.util.Locale
import kotlin.math.sin

class OpenMobiApp : Application() {
    lateinit var controller: Controller; private set
    override fun onCreate() { super.onCreate(); controller=Controller(this) }
}
fun Context.localized(): Context {
    if(android.os.Build.VERSION.SDK_INT>=33) return this // Framework owns per-app locale configuration.
    val tag=getSharedPreferences("preferences",Context.MODE_PRIVATE).getString("language","") ?: ""
    if(tag.isEmpty()) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
}
data class ExerciseState(
    val session: Session? = null, val paused: Boolean = false, val selected: Workout? = null,
    val stage: Int = 0, val progress: Float = 0f, val automatic: Boolean = true, val done: Boolean = false,
    val demo: Boolean = false, val metrics: Metrics = Metrics(), val error: Int? = null, val errorRow: Int? = null, val ready: Boolean = false
)
class Controller(val app: Application) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val repo=Repository(app)
    val ble=BleClient(app,scope)
    val heart=BleClient(app,scope)
    val state=MutableStateFlow(ExerciseState())
    val prefs=app.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    val theme=MutableStateFlow(prefs.getString("theme","system") ?: "system")
    val imperial=MutableStateFlow(prefs.getBoolean("imperial",false))
    private val sessionLock=Mutex()
    private var engine: TrainingEngine?=null
    private var lastTick=SystemClock.elapsedRealtime()
    private var lastAuto=0L
    private var distanceOrigin: Double?=null
    private var distanceOffset=0.0
    private var lastDeviceDistance: Double?=null
    private var lastDeviceStrokes: Int?=null
    private var activeStrokes=0
    private var demoResistance=1.0
    var serviceStarted=false
    init {
        scope.launch { try { repo.load(); state.value=state.value.copy(ready=true) } catch(e: Exception) { error(R.string.storage_failed) } }
        scope.launch {
            while(isActive) { delay(1000); sessionLock.withLock { tick() } }
        }
    }
    fun error(id: Int?,row: Int?=null) { state.value=state.value.copy(error=id,errorRow=row) }
    fun setTheme(value: String) { prefs.edit().putString("theme",value).apply(); theme.value=value }
    fun setImperial(value: Boolean) { prefs.edit().putBoolean("imperial",value).apply(); imperial.value=value }
    fun select(workout: Workout?) { if(state.value.session==null) state.value=state.value.copy(selected=workout) }
    fun setDemo(enabled: Boolean) {
        if(state.value.session!=null) return
        ble.disconnect(); heart.disconnect(); demoResistance=1.0
        state.value=state.value.copy(demo=enabled,metrics=if(enabled) Metrics(cadence=0.0,resistance=1.0,distanceM=0.0) else Metrics())
    }
    fun connect(device: FoundDevice) {
        if(device.heart) { heart.connect(device); return }
        if(state.value.session!=null && state.value.demo) return
        state.value=state.value.copy(demo=false,paused=state.value.session!=null)
        distanceOrigin=null; lastDeviceDistance=null; lastDeviceStrokes=null
        distanceOffset=state.value.session?.distanceM ?: 0.0
        prefs.edit().putString("last_address",device.address).putString("last_name",device.name).apply()
        ble.connect(device)
    }
    fun range() = if(state.value.demo) ResistanceRange(1.0,24.0) else ble.state.value.range
    fun canStart() = state.value.ready && (state.value.demo || ble.state.value.phase=="ready")
    fun start() { scope.launch { sessionLock.withLock {
        if(state.value.session!=null || !canStart()) { error(R.string.connect_first); return@withLock }
        if(state.value.selected?.steps?.any { (it.condition==Condition.DISTANCE && state.value.metrics.distanceM==null) || (it.condition==Condition.STROKES && state.value.metrics.strokes==null) }==true) {
            error(R.string.sensor_required); return@withLock
        }
        val s=Session(device=if(state.value.demo) "OpenMobi Demo" else ble.state.value.name,
            machine=if(state.value.demo) Machine.ELLIPTICAL else ble.state.value.machine,
            protocol=if(state.value.demo) Protocol.DEMO else ble.state.value.protocol,demo=state.value.demo)
        try { repo.save(s) } catch(e: Exception) { error(R.string.storage_failed); return@withLock }
        engine=state.value.selected?.let { TrainingEngine(it) }
        lastTick=SystemClock.elapsedRealtime(); lastAuto=0L
        distanceOrigin=state.value.metrics.distanceM; distanceOffset=0.0; lastDeviceDistance=distanceOrigin
        lastDeviceStrokes=state.value.metrics.strokes; activeStrokes=0
        engine?.resetBaseline(0,0.0,0)
        state.value=state.value.copy(session=s,paused=false,stage=0,progress=0f,automatic=state.value.demo || (ble.state.value.writable && ble.state.value.range!=null),done=false,error=null)
    } } }
    fun pauseResume() { scope.launch { sessionLock.withLock {
        if(state.value.session==null) return@withLock
        if(state.value.paused && !canStart()) { error(R.string.connect_first); return@withLock }
        val now=SystemClock.elapsedRealtime()
        val session=state.value.session!!.let { if(state.value.paused) it else it.copy(elapsedMs=it.elapsedMs+(now-lastTick).coerceAtLeast(0)) }
        lastTick=now; state.value=state.value.copy(session=session,paused=!state.value.paused,error=null)
        try { repo.save(session) } catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed) }
    } } }
    fun finish() { scope.launch { sessionLock.withLock {
        val stored=state.value.session ?: return@withLock
        val s=if(state.value.paused) stored else stored.copy(elapsedMs=stored.elapsedMs+(SystemClock.elapsedRealtime()-lastTick).coerceAtLeast(0))
        try { repo.save(s.copy(end=Instant.now().toString(),status=if(state.value.done) "completed" else "stopped")) }
        catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed); return@withLock }
        state.value=state.value.copy(session=null,paused=false,done=false,stage=0,progress=0f); engine=null
    } } }
    fun resumeAutomatic() { state.value=state.value.copy(automatic=true); lastAuto=0 }
    fun adjust(delta: Int) { scope.launch {
        val range=range() ?: return@launch
        val current=if(state.value.demo) demoResistance else ble.state.value.requested ?: ble.state.value.metrics.resistance ?: return@launch
        state.value=state.value.copy(automatic=false)
        setResistance(range.next(current,delta))
    } }
    private suspend fun setResistance(value: Double) {
        if(state.value.demo) { demoResistance=value; state.value=state.value.copy(metrics=state.value.metrics.copy(resistance=value)) }
        else if(!ble.resistance(value)) error(R.string.control_unavailable)
    }
    private suspend fun tick() {
        val now=SystemClock.elapsedRealtime(); val delta=(now-lastTick).coerceAtLeast(0); lastTick=now
        val old=state.value
        val current=old.session
        var metrics=if(old.demo) {
            val moving=current!=null && !old.paused
            val cadence=if(moving) 62+sin((current!!.elapsedMs)/7000.0)*6 else 0.0
            Metrics(cadence,demoResistance,if(moving) 2.0 else 0.0,
                (old.metrics.distanceM ?: 0.0)+if(moving) delta/500.0 else 0.0,if(moving) 115+(cadence/10).toInt() else null,if(moving) 60+demoResistance*3 else 0.0)
        } else {
            val link=ble.state.value
            link.metrics.copy(cadence=link.metrics.cadence.takeIf { now-link.motionAt<5000 },speedMps=link.metrics.speedMps.takeIf { now-link.motionAt<5000 },
                powerW=link.metrics.powerW.takeIf { now-link.motionAt<5000 },heartBpm=heart.state.value.metrics.heartBpm.takeIf { now-heart.state.value.heartAt<10000 }
                    ?: link.metrics.heartBpm.takeIf { now-link.heartAt<10000 })
        }
        state.value=state.value.copy(metrics=metrics)
        if(current==null) return
        if(!old.demo && ble.state.value.phase!="ready") {
            if(!old.paused) state.value=state.value.copy(paused=true,error=R.string.connection_lost)
            return
        }
        val rawDistance=metrics.distanceM
        if(rawDistance!=null) {
            if(distanceOrigin==null) distanceOrigin=rawDistance
            if(lastDeviceDistance!=null && rawDistance<lastDeviceDistance!!) { distanceOffset=current.distanceM ?: 0.0; distanceOrigin=rawDistance }
            if(old.paused && lastDeviceDistance!=null) distanceOrigin=distanceOrigin!!+(rawDistance-lastDeviceDistance!!).coerceAtLeast(0.0)
            lastDeviceDistance=rawDistance
        }
        val strokes=metrics.strokes
        if(strokes!=null) {
            if(!old.paused && lastDeviceStrokes!=null) activeStrokes+=(strokes-lastDeviceStrokes!!).coerceAtLeast(0)
            lastDeviceStrokes=strokes
        }
        if(old.paused) return
        val distance=rawDistance?.let { (distanceOffset+it-distanceOrigin!!).coerceAtLeast(0.0) }
        val session=current.copy(elapsedMs=current.elapsedMs+delta,distanceM=distance ?: current.distanceM)
        val training=engine
        training?.tick(session.elapsedMs,distance,strokes?.let { activeStrokes })
        state.value=state.value.copy(session=session,stage=training?.index ?: 0,progress=training?.progress(session.elapsedMs,distance,strokes?.let { activeStrokes })?.toFloat() ?: 0f,done=training?.done ?: false)
        try { repo.save(session,Sample(session.id,session.elapsedMs,metrics.copy(distanceM=distance,strokes=strokes?.let { activeStrokes }))) }
        catch(e: Exception) { state.value=state.value.copy(paused=true,error=R.string.storage_failed); return }
        if(training!=null && !training.done && state.value.automatic && now-lastAuto>=2000) {
            val percent=training.workout.steps[training.index].resistancePercent
            val range=range(); val actual=metrics.resistance
            if(percent!=null && range!=null && actual!=null && (old.demo || ble.state.value.requested==null)) {
                val target=range.percent(percent)
                if(kotlin.math.abs(target-actual)>=range.increment*0.5) {
                    lastAuto=now
                    setResistance(range.next(actual,if(target>actual) 1 else -1))
                }
            }
        }
    }
}
