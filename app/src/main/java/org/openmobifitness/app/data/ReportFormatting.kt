package org.openmobifitness.app.data

import android.content.Context
import org.openmobifitness.app.R
import org.openmobifitness.app.metricLabel
import org.openmobifitness.core.*
import java.util.Locale

/** Read archived units, never infer a capability from a device name or a desired target. */
fun Session.reportEvidence(): Set<SeriesMetric> = runCatching {
    if(capabilitySnapshot.isEmpty()) return@runCatching emptySet()
    SnapshotValidation.capability(this)
    val units=org.json.JSONObject(capabilitySnapshot).optJSONObject("units") ?: return@runCatching emptySet()
    mapOf(SeriesMetric.SPEED to ("speed" to "m/s"),SeriesMetric.INCLINE to ("incline" to "percent"),
        SeriesMetric.RESISTANCE to ("resistance" to "level"),SeriesMetric.POWER to ("power" to "W"),
        SeriesMetric.FORCE to ("force" to "N"),SeriesMetric.LOAD to ("load" to "kg"))
        .filterValues { (key,unit) -> units.optString(key)==unit }.keys
}.getOrDefault(emptySet())

fun Context.reportLabel(d: ReportMetricDescriptor): String {
    if(d.metric==MetricId.AVERAGE_SPEED) return getString(R.string.report_overall_speed)
    if(d.metric==MetricId.PACE && d.aggregation==ReportAggregation.MINIMUM) return getString(R.string.report_pace_fastest)
    if(d.metric==MetricId.PACE && d.aggregation==ReportAggregation.MAXIMUM) return getString(R.string.report_pace_slowest)
    val name=getString(d.metric?.let(::metricLabel) ?: R.string.steps_total)
    return when(d.aggregation) {
        ReportAggregation.VALUE -> name
        ReportAggregation.AVERAGE -> getString(R.string.metric_average,name)
        ReportAggregation.MAXIMUM -> getString(R.string.metric_maximum,name)
        ReportAggregation.MINIMUM -> getString(R.string.report_minimum,name)
    }
}
fun ReportMetricDescriptor.reportValue(imperial: Boolean,locale: Locale): Pair<String,String> {
    val converted=if(!imperial) value to unit else when(unit) {
        "km" -> value?.div(1.609344) to "mi"
        "km/h" -> value?.div(1.609344) to "mph"
        "m" -> value?.times(3.280839895) to "ft"
        "kg" -> value?.times(2.204622622) to "lb"
        else -> value to unit
    }
    val raw=value ?: return "—" to converted.second
    if(metric==MetricId.TIME) return shortDuration((raw*1000).toLong()) to ""
    if(metric==MetricId.PACE) return shortDuration((raw*1000).toLong()) to unit
    val decimals=when(metric) { MetricId.DISTANCE,MetricId.STRIDE -> 2; MetricId.SPEED,MetricId.AVERAGE_SPEED,MetricId.LOAD,MetricId.INCLINE,MetricId.RESISTANCE -> 1; else -> 0 }
    return String.format(locale,"%.${decimals}f",converted.first) to converted.second
}
fun sourceResource(source: String)=when(source) { "default" -> R.string.report_default; "user" -> R.string.report_user_entered; "legacy" -> R.string.report_legacy; else -> R.string.plan_unrecorded }
fun Context.rangeText(range: PersonalRange,unit: String): String {
    if(!range.enabled) return getString(R.string.report_ranges_off)
    fun n(v: Double)=String.format(resources.configuration.locales[0],if(v%1.0==0.0) "%.0f" else "%.1f",v)
    val lower=range.lower; val upper=range.upper
    return when { lower!=null && upper!=null -> "${n(lower)}–${n(upper)} $unit"; lower!=null -> getString(R.string.report_lower,n(lower))+" $unit"; else -> getString(R.string.report_upper,n(upper!!))+" $unit" }
}

/** Use actual peak samples, not means of a downsampled series. Keep missing-data segments separate. */
fun powerBarPeaks(points: List<PlotPoint>,elapsedMs: Long,buckets: Int=48): List<PlotPoint> {
    require(buckets>0)
    return points.groupBy { it.segment to powerBarIndex(it.ms,elapsedMs,buckets) }
        .values.map { group -> group.maxBy { it.value } }.sortedBy { it.ms }
}
fun powerBarIndex(ms: Long,elapsedMs: Long,buckets: Int=48): Int {
    require(buckets>0)
    val interval=(elapsedMs.coerceAtLeast(1)/buckets.toDouble()).coerceAtLeast(1.0)
    return (ms/interval).toInt().coerceIn(0,buckets-1)
}
/** Select the displayed bar, not a neighboring sample whose timestamp happens to be closer. */
fun powerBarAtTime(peaks: List<PlotPoint>,elapsedMs: Long,ms: Long,buckets: Int=48): PlotPoint? {
    if(ms<0 || ms>elapsedMs) return null
    val index=powerBarIndex(ms,elapsedMs,buckets)
    return peaks.filter { powerBarIndex(it.ms,elapsedMs,buckets)==index }.maxByOrNull { it.value }
}
