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

fun machineResource(machine: Machine)=when(machine) { Machine.ELLIPTICAL->R.string.machine_elliptical; Machine.BIKE->R.string.machine_bike; Machine.ROWER->R.string.machine_rower; Machine.TREADMILL->R.string.machine_treadmill; Machine.JUMP_ROPE->R.string.machine_jump_rope; Machine.DUMBBELL->R.string.machine_dumbbell; Machine.HEART->R.string.heart_rate; Machine.UNKNOWN->R.string.machine_unknown }
fun seriesResource(metric: SeriesMetric,machine: Machine=Machine.UNKNOWN)=org.openmobifitness.app.metricLabel(ReportMetrics.metric(metric,machine))
fun seriesUnit(metric: SeriesMetric,machine: Machine,imperial: Boolean=false)=if(metric==SeriesMetric.SPEED && imperial) "mph" else ReportMetrics.unit(metric,machine)
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
        val emptyStats=Statistics.summarize(emptyList())
        val columns=Machine.entries.flatMap { machine -> ReportMetrics.descriptors(Session(machine=machine),emptyStats) }.distinctBy { it.key }
        val headers=listOf(s(R.string.local_date),s(R.string.end_date),s(R.string.machine_type),s(R.string.title),s(R.string.devices),s(R.string.report_owner),s(R.string.report_started_user))+
            columns.map { d -> context.reportLabel(d)+d.reportValue(imperial,locale).second.let { if(it.isBlank()) "" else " ($it)" } }+
            listOf(s(R.string.estimated_values),s(R.string.source_data),s(R.string.status_label),s(R.string.energy_source),s(R.string.sample_count),s(R.string.user_weight_optional),s(R.string.user_met),s(R.string.report_default))
        output.write(Csv.write(listOf(headers)).toByteArray(Charsets.UTF_8))
        suspend fun row(sessionId: String) {
            val (v,stats)=history.detail(sessionId) ?: return
            val date=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx").withZone(ZoneId.of(v.zone))
            val descriptors=ReportMetrics.descriptors(v,stats,v.reportEvidence()).associateBy { it.key }
            val cells=listOf(date.format(Instant.parse(v.start)),v.end.takeIf { it.isNotBlank() }?.let { date.format(Instant.parse(it)) }.orEmpty(),s(machineResource(v.machine)),v.workoutTitle.ifBlank { s(R.string.plan_unrecorded) },v.device,if(v.ownerUserId==null) s(R.string.history_unassigned) else "${history.ownerName(v.ownerUserId).orEmpty()} · ${v.ownerUserId}",v.startedUserName)+
                columns.map { d -> descriptors[d.key]?.reportValue(imperial,locale)?.first.orEmpty() }+
                listOf(descriptors.values.filter { it.source in setOf(ReportSource.ESTIMATED,ReportSource.MIXED) && it.value!=null }.joinToString("; ") { context.reportLabel(it) },s(if(v.demo) R.string.demo else R.string.real_data),s(sessionStatus(v.status)),s(energySourceResource(v)),stats.samples.toString(),number(v.weightKg),number(v.met),listOf("weight: "+s(sourceResource(v.weightSource)),"MET: "+s(sourceResource(v.metSource))).joinToString("; "))
            output.write(Csv.write(listOf(cells.map(::safe))).removePrefix("\uFEFF").toByteArray(Charsets.UTF_8))
        }
        // Freeze selection, without holding a write-blocking transaction during a large export.
        val ids=if(id!=null) listOf(id) else history.reportIds(query)
        ids.forEach { row(it) }
    }
}
