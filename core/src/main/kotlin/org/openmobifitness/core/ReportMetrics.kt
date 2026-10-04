package org.openmobifitness.core

/** Canonical report rows used by the native report and CSV. Values stay in metric units. */
enum class ReportAggregation { VALUE, AVERAGE, MAXIMUM, MINIMUM }
enum class ReportSource { MEASURED, CALCULATED, ESTIMATED, MIXED }
data class ReportMetricDescriptor(val key: String,val metric: MetricId?,val value: Double?,val unit: String="",val aggregation: ReportAggregation=ReportAggregation.VALUE,val series: SeriesMetric?=null,val source: ReportSource=ReportSource.MEASURED,val coverageMs: Long?=null)

object ReportMetrics {
    /** Extra semantically compatible axes require explicit historical unit evidence. */
    fun series(machine: Machine,evidence: Set<SeriesMetric>): List<SeriesMetric> = (series(machine)+evidence.intersect(when(machine) {
        Machine.ELLIPTICAL,Machine.BIKE -> setOf(SeriesMetric.INCLINE,SeriesMetric.FORCE)
        Machine.UNKNOWN -> setOf(SeriesMetric.SPEED,SeriesMetric.POWER,SeriesMetric.RESISTANCE,SeriesMetric.INCLINE,SeriesMetric.FORCE,SeriesMetric.LOAD)
        else -> emptySet()
    })).distinct()
    fun series(machine: Machine): List<SeriesMetric> = listOf(SeriesMetric.HEART)+when(machine) {
        Machine.ELLIPTICAL,Machine.BIKE -> listOf(SeriesMetric.CADENCE,SeriesMetric.SPEED,SeriesMetric.POWER,SeriesMetric.RESISTANCE)
        Machine.ROWER -> listOf(SeriesMetric.CADENCE,SeriesMetric.SPEED,SeriesMetric.PACE,SeriesMetric.POWER,SeriesMetric.RESISTANCE,SeriesMetric.FORCE)
        Machine.TREADMILL -> listOf(SeriesMetric.STEP_RATE,SeriesMetric.SPEED,SeriesMetric.INCLINE,SeriesMetric.STRIDE,SeriesMetric.POWER,SeriesMetric.FORCE)
        Machine.JUMP_ROPE -> listOf(SeriesMetric.CADENCE)
        Machine.DUMBBELL -> listOf(SeriesMetric.LOAD)
        else -> emptyList()
    }
    fun metric(series: SeriesMetric,machine: Machine): MetricId=when(series) {
        SeriesMetric.HEART -> MetricId.HEART
        SeriesMetric.CADENCE -> when(machine) { Machine.ROWER -> MetricId.STROKE_RATE; Machine.JUMP_ROPE -> MetricId.JUMP_RATE; else -> MetricId.CADENCE }
        SeriesMetric.POWER -> MetricId.POWER
        SeriesMetric.SPEED -> MetricId.SPEED
        SeriesMetric.RESISTANCE -> MetricId.RESISTANCE
        SeriesMetric.STEP_RATE -> MetricId.STEP_RATE
        SeriesMetric.INCLINE -> MetricId.INCLINE
        SeriesMetric.STRIDE -> MetricId.STRIDE
        SeriesMetric.FORCE -> MetricId.FORCE
        SeriesMetric.LOAD -> MetricId.LOAD
        SeriesMetric.PACE -> MetricId.PACE
    }
    fun unit(series: SeriesMetric,machine: Machine)=when(series) {
        SeriesMetric.HEART -> "bpm"
        SeriesMetric.CADENCE -> if(machine==Machine.ROWER) "spm" else if(machine==Machine.JUMP_ROPE) "/min" else "rpm"
        SeriesMetric.POWER -> "W"
        SeriesMetric.SPEED -> "km/h"
        SeriesMetric.RESISTANCE -> ""
        SeriesMetric.STEP_RATE -> "spm"
        SeriesMetric.INCLINE -> "%"
        SeriesMetric.STRIDE -> "m"
        SeriesMetric.FORCE -> "N"
        SeriesMetric.LOAD -> "kg"
        SeriesMetric.PACE -> "/500m"
    }
    fun distanceApplicable(machine: Machine)=machine !in setOf(Machine.JUMP_ROPE,Machine.DUMBBELL,Machine.HEART)
    fun descriptors(s: Session,stats: SessionStats,evidence: Set<SeriesMetric> = emptySet()): List<ReportMetricDescriptor> = buildList {
        fun scalar(id: MetricId,value: Double?,unit: String="",source: ReportSource=ReportSource.MEASURED) { add(ReportMetricDescriptor(id.name.lowercase(),id,value,unit,source=source)) }
        scalar(MetricId.TIME,s.elapsedMs/1000.0,"s",ReportSource.CALCULATED)
        scalar(MetricId.CALORIES,s.caloriesKcal,"kcal",if(s.caloriesEstimated) ReportSource.ESTIMATED else ReportSource.MEASURED)
        if(distanceApplicable(s.machine)) {
            scalar(MetricId.DISTANCE,s.distanceM?.div(1000),"km",if(s.distanceEstimated) ReportSource.ESTIMATED else ReportSource.MEASURED)
            scalar(MetricId.AVERAGE_SPEED,s.distanceM?.takeIf { s.elapsedMs>0 }?.times(3600.0)?.div(s.elapsedMs),"km/h",if(s.distanceEstimated) ReportSource.ESTIMATED else ReportSource.CALCULATED)
        }
        when(s.machine) {
            Machine.TREADMILL -> add(ReportMetricDescriptor("steps",null,stats.steps?.toDouble()))
            Machine.ROWER -> scalar(MetricId.STROKES,stats.strokes?.toDouble())
            Machine.JUMP_ROPE -> { scalar(MetricId.JUMPS,stats.jumps?.toDouble()); scalar(MetricId.CONTINUOUS_JUMPS,stats.continuousJumps?.toDouble()); scalar(MetricId.JUMP_INTERRUPTS,stats.jumpInterruptions?.toDouble()) }
            Machine.DUMBBELL -> scalar(MetricId.REPETITIONS,stats.repetitions?.toDouble())
            else -> Unit
        }
        series(s.machine,evidence).forEach { series ->
            val metric=metric(series,s.machine); val v=stats.metrics.getValue(series)
            val source=when { series in setOf(SeriesMetric.SPEED,SeriesMetric.PACE) && stats.speedEstimated -> ReportSource.ESTIMATED; series==SeriesMetric.PACE -> ReportSource.CALCULATED; series==SeriesMetric.POWER && stats.powerMixed -> ReportSource.MIXED; series==SeriesMetric.POWER && stats.powerEstimated -> ReportSource.ESTIMATED; else -> ReportSource.MEASURED }
            listOf(ReportAggregation.AVERAGE to v.average,ReportAggregation.MAXIMUM to v.maximum,ReportAggregation.MINIMUM to v.minimum).forEach { (kind,value) ->
                add(ReportMetricDescriptor("${metric.name.lowercase()}.${kind.name.lowercase()}",metric,value,unit(series,s.machine),kind,series,source,v.coverageMs))
            }
        }
    }
}
