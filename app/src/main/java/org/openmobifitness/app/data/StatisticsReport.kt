package org.openmobifitness.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmobifitness.app.R
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.io.OutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun machineResource(machine: Machine)=when(machine) { Machine.ELLIPTICAL->R.string.machine_elliptical; Machine.BIKE->R.string.machine_bike; Machine.ROWER->R.string.machine_rower; Machine.TREADMILL->R.string.machine_treadmill; Machine.JUMP_ROPE->R.string.machine_jump_rope; Machine.DUMBBELL->R.string.machine_dumbbell; else->R.string.devices }
fun seriesResource(metric: SeriesMetric)=when(metric) { SeriesMetric.HEART->R.string.heart_rate; SeriesMetric.CADENCE->R.string.cadence; SeriesMetric.POWER->R.string.power; SeriesMetric.SPEED->R.string.speed; SeriesMetric.RESISTANCE->R.string.resistance }
fun seriesUnit(metric: SeriesMetric,machine: Machine,imperial: Boolean=false)=when(metric) { SeriesMetric.HEART->"bpm"; SeriesMetric.CADENCE->if(machine in setOf(Machine.ROWER,Machine.TREADMILL)) "spm" else "rpm"; SeriesMetric.POWER->"W"; SeriesMetric.SPEED->if(imperial) "mph" else "km/h"; SeriesMetric.RESISTANCE->"" }
fun displayNumber(value: Double?,digits: Int=0): String=value?.let { String.format(Locale.getDefault(),"%.${digits}f",it) } ?: "—"
fun sessionDate(session: Session,pattern: String="yyyy-MM-dd HH:mm"): String=runCatching { DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.parse(session.start)) }.getOrDefault(session.start)
fun shortDuration(ms: Long): String = if(ms<3600000) "%02d:%02d".format(ms/60000,ms/1000%60) else WorkoutService.elapsed(ms)
fun sessionStatus(status: String)=when(status) { "active"->R.string.active; "completed"->R.string.completed; "interrupted"->R.string.interrupted; else->R.string.stopped }

fun energySourceResource(s: Session)=when(s.energyModel) {
    "legacy-v1"->R.string.energy_legacy
    "met"->R.string.energy_met
    "device"->R.string.measured_label
    "mixed"->R.string.energy_mixed
    else->if(s.caloriesEstimated && s.met!=null) R.string.energy_met else if(!s.caloriesEstimated && s.caloriesKcal!=null) R.string.measured_label else R.string.energy_unknown
}

object StatisticsReport {
    /** Stream one human-readable row per workout. Raw samples remain in the ZIP backup. */
    suspend fun write(context: Context,history: HistoryStore,query: HistoryQuery,id: String?,output: OutputStream,imperial: Boolean) = withContext(Dispatchers.IO) {
        val locale=context.resources.configuration.locales[0]
        fun s(res: Int)=context.getString(res)
        fun number(v: Double?,digits: Int=1)=v?.let { String.format(locale,"%.${digits}f",it) } ?: "—"
        fun safe(v: String)=if(v.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$v" else v
        val distanceUnit=if(imperial) "mi" else "km"
        val speedUnit=if(imperial) "mph" else "km/h"
        val headers=mutableListOf(s(R.string.local_date),s(R.string.end_date),s(R.string.machine_type),s(R.string.title),s(R.string.devices),s(R.string.duration),"${s(R.string.distance)} ($distanceUnit)","${s(R.string.calories)} (kcal)",s(R.string.estimated_label))
        SeriesMetric.entries.forEach { m -> val unit=when(m) { SeriesMetric.CADENCE->"rpm / spm"; SeriesMetric.SPEED->speedUnit; else->seriesUnit(m,Machine.ELLIPTICAL) }; val suffix=if(unit.isEmpty()) "" else " ($unit)"; headers+=context.getString(R.string.metric_average,s(seriesResource(m)))+suffix; headers+=context.getString(R.string.metric_maximum,s(seriesResource(m)))+suffix }
        headers+=listOf(s(R.string.steps_total),s(R.string.strokes),s(R.string.power_source),s(R.string.source_data),s(R.string.status_label),s(R.string.energy_source),s(R.string.sample_count))
        output.write(Csv.write(listOf(headers)).toByteArray(Charsets.UTF_8))
        suspend fun row(sessionId: String) {
            val detail=history.detail(sessionId) ?: return
            val (v,stats)=detail
            val date=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx").withZone(ZoneId.of(v.zone))
            val cells=mutableListOf(date.format(Instant.parse(v.start)),v.end.takeIf { it.isNotBlank() }?.let { date.format(Instant.parse(it)) }.orEmpty(),s(machineResource(v.machine)),v.workoutTitle.ifBlank { s(R.string.plan_unrecorded) },v.device,WorkoutService.elapsed(v.elapsedMs),number(v.distanceM?.div(if(imperial) 1609.344 else 1000.0),2),number(v.caloriesKcal),listOfNotNull(s(R.string.distance).takeIf { v.distanceEstimated },s(R.string.calories).takeIf { v.caloriesEstimated }).joinToString("; "))
            SeriesMetric.entries.forEach { m -> val scale=if(m==SeriesMetric.SPEED && imperial) 1/1.609344 else 1.0; val metric=stats.metrics.getValue(m); cells+=number(metric.average?.times(scale)); cells+=number(metric.maximum?.times(scale)) }
            cells+=listOf(stats.steps?.toString() ?: "—",stats.strokes?.toString() ?: "—",if(stats.metrics.getValue(SeriesMetric.POWER).average==null) "—" else s(if(stats.powerEstimated) R.string.estimated_label else R.string.measured_label),s(if(v.demo) R.string.demo else R.string.real_data),s(sessionStatus(v.status)),s(energySourceResource(v)),stats.samples.toString())
            output.write(Csv.write(listOf(cells.map(::safe))).removePrefix("\uFEFF").toByteArray(Charsets.UTF_8))
        }
        // Freeze selection, without holding a write-blocking transaction during a large export.
        val ids=if(id!=null) listOf(id) else history.reportIds(query)
        ids.forEach { row(it) }
    }
}
