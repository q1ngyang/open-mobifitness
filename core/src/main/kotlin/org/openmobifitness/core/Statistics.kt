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
    HEART, CADENCE, POWER, SPEED, RESISTANCE, STEP_RATE, INCLINE, STRIDE, FORCE, LOAD, PACE;
    fun value(m: Metrics): Double? = when(this) {
        HEART -> m.heartBpm?.takeIf { it>0 }?.toDouble()
        CADENCE -> m.cadence
        POWER -> m.powerW
        SPEED -> m.speedMps?.times(3.6)
        RESISTANCE -> m.resistance
        STEP_RATE -> m.stepRate
        INCLINE -> m.inclinePercent
        STRIDE -> m.strideM
        FORCE -> m.forceN
        LOAD -> m.loadKg
        PACE -> m.speedMps?.takeIf { it>0 }?.let { 500.0/it }
    }?.takeIf { it.isFinite() }
}
data class PlotPoint(val ms: Long, val value: Double, val segment: Int=0)
data class MetricStats(val average: Double?, val maximum: Double?, val minimum: Double?, val coverageMs: Long, val points: List<PlotPoint>)
data class SessionStats(val metrics: Map<SeriesMetric,MetricStats>, val steps: Int?, val strokes: Int?, val powerEstimated: Boolean, val samples: Int,val jumps: Int?=null,val continuousJumps: Int?=null,val jumpInterruptions: Int?=null,val repetitions: Int?=null,val powerMixed: Boolean=false,val speedEstimated: Boolean=false)
object Statistics {
    /** Samples describe the preceding active-time interval. Limit held data to 5 seconds.
     * Missing/zero heart rate is absent, not a zero-valued measurement. */
    fun summarize(samples: List<Sample>): SessionStats = Accumulator().also { stats -> samples.sortedBy { it.elapsedMs }.forEach { stats.add(it) } }.finish()
    /** Bounded plots and exact totals for paged histories; no full sample list is retained. */
    class Accumulator {
        private class Series {
            var coverage=0L; var total=0.0; var distance=0.0; var sum=0.0; var count=0
            var maximum: Double?=null; var minimum: Double?=null
            var segment=0; var lastValid=0L; var gap=false
            val points=ArrayList<PlotPoint>()
        }
        private val series=SeriesMetric.entries.associateWith { Series() }
        private var prior=0L; private var count=0
        private var steps: Int?=null; private var strokes: Int?=null; private var jumps: Int?=null
        private var continuous: Int?=null; private var interruptions: Int?=null; private var repetitions: Int?=null
        private var estimated=false; private var measured=false; private var estimatedSpeed=false
        private fun max(old: Int?,value: Int?)=if(value==null) old else old?.let { maxOf(it,value) } ?: value
        fun add(sample: Sample,speedEstimated: Boolean=false) {
            estimatedSpeed=estimatedSpeed || speedEstimated
            require(sample.elapsedMs>=prior)
            val dt=(sample.elapsedMs-prior).coerceIn(0,5000); prior=sample.elapsedMs; count++
            val m=sample.metrics
            steps=max(steps,m.stepCount); strokes=max(strokes,m.strokes); jumps=max(jumps,m.jumpCount)
            continuous=max(continuous,m.continuousJumps); interruptions=max(interruptions,m.jumpInterruptions); repetitions=max(repetitions,m.repetitions)
            if(m.powerW!=null) { if(m.powerEstimated) estimated=true else measured=true }
            series.forEach { (metric,s) ->
                val value=metric.value(m)
                if(metric==SeriesMetric.PACE && m.speedMps?.let { it.isFinite() && it>=0 }==true) {
                    s.coverage+=dt; s.distance+=m.speedMps*dt/1000.0
                }
                if(value==null) { s.gap=true; return@forEach }
                if(s.gap || sample.elapsedMs-s.lastValid>5000) s.segment++
                s.gap=false; s.lastValid=sample.elapsedMs
                if(metric!=SeriesMetric.PACE) { s.coverage+=dt; s.total+=value*dt }
                s.sum+=value; s.count++; s.maximum=s.maximum?.let { maxOf(it,value) } ?: value; s.minimum=s.minimum?.let { minOf(it,value) } ?: value
                s.points+=PlotPoint(sample.elapsedMs,value,s.segment)
                if(s.points.size>=960) { val reduced=decimate(s.points); s.points.clear(); s.points.addAll(reduced) }
            }
        }
        fun finish()=SessionStats(series.mapValues { (metric,s) ->
            val average=if(metric==SeriesMetric.PACE) if(s.distance>0) 500.0*(s.coverage/1000.0)/s.distance else null
                else if(s.coverage>0) s.total/s.coverage else if(s.count>0) s.sum/s.count else null
            MetricStats(average,s.maximum,s.minimum,s.coverage,decimate(s.points))
        },steps,strokes,estimated,count,jumps,continuous,interruptions,repetitions,estimated && measured,estimatedSpeed)
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
