package org.openmobifitness.app

import android.content.Context
import org.openmobifitness.core.*
import org.openmobifitness.app.service.WorkoutService
import java.util.Locale
import kotlin.math.ceil

data class MetricReading(val label: String,val value: String,val unit: String,val estimated: Boolean=false)
fun metricLabel(id: MetricId)=when(id) {
    MetricId.TIME -> R.string.duration; MetricId.DISTANCE -> R.string.distance; MetricId.CALORIES -> R.string.calories
    MetricId.HEART -> R.string.heart_rate; MetricId.SPEED -> R.string.speed; MetricId.RESISTANCE -> R.string.resistance
    MetricId.CADENCE -> R.string.cadence; MetricId.STROKE_RATE -> R.string.stroke_rate; MetricId.STROKES -> R.string.strokes
    MetricId.FORCE -> R.string.force; MetricId.INCLINE -> R.string.incline; MetricId.STEP_RATE -> R.string.step_rate
    MetricId.STRIDE -> R.string.stride; MetricId.TARGET_CADENCE -> R.string.target_cadence; MetricId.POWER -> R.string.power
    MetricId.PACE -> R.string.pace; MetricId.AVERAGE_SPEED -> R.string.average_speed
}
fun Context.minutesSeconds(ms: Long): String {
    val seconds=ceil(ms.coerceAtLeast(0)/1000.0).toLong()
    return getString(R.string.minutes_seconds,seconds/60,seconds%60)
}
fun Context.reading(id: MetricId,c: Controller): MetricReading {
    val s=c.state.value; val session=s.session; val m=s.metrics
    val machine=session?.machine ?: if(s.demo) s.demoMachine else c.ble.state.value.machine
    val protocol=session?.protocol ?: c.ble.state.value.protocol
    val imperial=c.imperial.value; val locale=resources.configuration.locales[0] ?: Locale.getDefault()
    fun n(value: Double?,digits: Int=1)=value?.takeIf { it.isFinite() }?.let { String.format(locale,"%.${digits}f",it) } ?: "—"
    val legacy=if(protocol in setOf(Protocol.V1,Protocol.V2)) Estimates.legacySpeed(m.cadence,machine) else null
    val speed=m.speedMps ?: legacy
    var estimated=false; val label=metricLabel(id)
    val (value,unit)=when(id) {
        MetricId.TIME -> WorkoutService.elapsed(session?.elapsedMs ?: 0) to ""
        MetricId.DISTANCE -> { estimated=session?.distanceEstimated==true; n(session?.distanceM?.div(if(imperial) 1609.344 else 1000.0),2) to if(imperial) "mi" else "km" }
        MetricId.CALORIES -> { estimated=session?.caloriesEstimated==true; n(session?.caloriesKcal,1) to "kcal" }
        MetricId.HEART -> (m.heartBpm?.toString() ?: "—") to "bpm"
        MetricId.SPEED -> { estimated=m.speedMps==null && legacy!=null; n(speed?.times(if(imperial) 2.236936 else 3.6)) to if(imperial) "mph" else "km/h" }
        MetricId.RESISTANCE -> n(m.resistance,if(m.resistance?.rem(1.0)==0.0) 0 else 1) to (m.resistance?.let { c.range()?.percentage(it)?.let { p -> "$p%" } } ?: "")
        MetricId.CADENCE -> when {
            machine==Machine.ROWER -> { n(m.cadence) to "spm" }
            machine==Machine.TREADMILL || (m.cadence==null && m.stepRate!=null) -> { n(m.stepRate ?: m.cadence) to "spm" }
            else -> n(m.cadence) to "rpm"
        }
        MetricId.STROKE_RATE -> n(m.cadence.takeIf { machine==Machine.ROWER }) to "spm"
        MetricId.STROKES -> (s.strokeCount?.toString() ?: "—") to ""
        MetricId.FORCE -> n(m.forceN,0) to "N"
        MetricId.INCLINE -> n(m.inclinePercent) to "%"
        MetricId.STEP_RATE -> n(m.stepRate) to "spm"
        MetricId.STRIDE -> { estimated=protocol==Protocol.V2 && m.strideM!=null; n(m.strideM?.times(100),0) to "cm" }
        MetricId.TARGET_CADENCE -> (if(machine==Machine.ROWER) c.display.targetCadence.value.toString() else "—") to "spm"
        MetricId.POWER -> { estimated=m.powerEstimated; n(m.powerW,0) to "W" }
        MetricId.PACE -> (speed?.takeIf { it>0 }?.let { minutesSeconds((500/it*1000).toLong()) } ?: "—") to "/500 m"
        MetricId.AVERAGE_SPEED -> { estimated=session?.distanceEstimated==true
            n(session?.takeIf { it.elapsedMs>0 }?.let { it.distanceM?.div(it.elapsedMs/1000.0) }?.times(if(imperial) 2.236936 else 3.6)) to if(imperial) "mph" else "km/h" }
    }
    return MetricReading(getString(label),value,unit,estimated && value!="—")
}
