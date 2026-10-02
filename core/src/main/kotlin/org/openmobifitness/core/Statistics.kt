package org.openmobifitness.core

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.*

enum class HistoryPeriod { WEEK, MONTH, YEAR, ALL }
data class PeriodWindow(val from: Long, val until: Long) {
    companion object {
        fun of(period: HistoryPeriod, date: LocalDate, zone: ZoneId): PeriodWindow {
            if(period==HistoryPeriod.ALL) return PeriodWindow(0,Long.MAX_VALUE)
            val begin=when(period) {
                HistoryPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                HistoryPeriod.MONTH -> date.withDayOfMonth(1)
                else -> date.withDayOfYear(1)
            }
            val end=when(period) { HistoryPeriod.WEEK -> begin.plusWeeks(1); HistoryPeriod.MONTH -> begin.plusMonths(1); else -> begin.plusYears(1) }
            return PeriodWindow(begin.atStartOfDay(zone).toInstant().toEpochMilli(),end.atStartOfDay(zone).toInstant().toEpochMilli())
        }
    }
}
enum class SeriesMetric {
    HEART, CADENCE, POWER, SPEED, RESISTANCE;
    fun value(m: Metrics): Double? = when(this) {
        HEART -> m.heartBpm?.takeIf { it>0 }?.toDouble()
        CADENCE -> m.cadence ?: m.stepRate
        POWER -> m.powerW
        SPEED -> m.speedMps?.times(3.6)
        RESISTANCE -> m.resistance
    }?.takeIf { it.isFinite() }
}
data class PlotPoint(val ms: Long, val value: Double, val segment: Int=0)
data class MetricStats(val average: Double?, val maximum: Double?, val minimum: Double?, val coverageMs: Long, val points: List<PlotPoint>)
data class SessionStats(val metrics: Map<SeriesMetric,MetricStats>, val steps: Int?, val strokes: Int?, val powerEstimated: Boolean, val samples: Int)
object Statistics {
    /** Samples describe the preceding active-time interval. Limit held data to 5 seconds.
     * Missing/zero heart rate is absent, not a zero-valued measurement. */
    fun summarize(samples: List<Sample>): SessionStats {
        val ordered=samples.sortedBy { it.elapsedMs }
        val metrics=SeriesMetric.entries.associateWith { metric ->
            var prior=0L; var segment=0; var lastValid=0L; var weight=0L; var total=0.0
            val points=mutableListOf<PlotPoint>()
            ordered.forEach { sample ->
                val dt=(sample.elapsedMs-prior).coerceIn(0,5000); prior=sample.elapsedMs
                metric.value(sample.metrics)?.let { value ->
                    if(sample.elapsedMs-lastValid>5000) segment++; lastValid=sample.elapsedMs
                    points+=PlotPoint(sample.elapsedMs,value,segment); total+=value*dt; weight+=dt
                }
            }
            MetricStats(if(weight>0) total/weight else points.map { it.value }.takeIf { it.isNotEmpty() }?.average(),points.maxOfOrNull { it.value },points.minOfOrNull { it.value },weight,decimate(points))
        }
        return SessionStats(metrics,ordered.mapNotNull { it.metrics.stepCount }.maxOrNull(),ordered.mapNotNull { it.metrics.strokes }.maxOrNull(),ordered.any { it.metrics.powerEstimated },ordered.size)
    }
    /** Keep endpoints and both extrema of each bucket, rather than losing peaks to averaging. */
    fun decimate(points: List<PlotPoint>, limit: Int=240): List<PlotPoint> {
        require(limit>=4)
        if(points.size<=limit) return points
        val width=ceil((points.size-2).toDouble()/((limit-2)/2)).toInt()
        return (listOf(points.first())+points.drop(1).dropLast(1).chunked(width).flatMap { bucket ->
            listOf(bucket.minBy { it.value },bucket.maxBy { it.value }).distinct().sortedBy { it.ms }
        }+points.last()).distinct()
    }
}

enum class PlanDuration { ALL, SHORT, MEDIUM, LONG }
enum class PlanIntensity { ALL, LOW, MODERATE, HIGH }
object PlanFilter {
    fun matches(w: Workout,duration: PlanDuration,intensity: PlanIntensity): Boolean {
        val minutes=if(w.steps.all { it.condition==Condition.TIME }) w.steps.sumOf { it.target }/60 else null
        val peak=w.steps.mapNotNull { it.resistancePercent }.maxOrNull() ?: 0
        return when(duration) { PlanDuration.ALL -> true; PlanDuration.SHORT -> minutes!=null && minutes<20; PlanDuration.MEDIUM -> minutes!=null && minutes in 20.0..40.0; PlanDuration.LONG -> minutes!=null && minutes>40 } &&
            when(intensity) { PlanIntensity.ALL -> true; PlanIntensity.LOW -> peak<=25; PlanIntensity.MODERATE -> peak in 26..50; PlanIntensity.HIGH -> peak>50 }
    }
}
