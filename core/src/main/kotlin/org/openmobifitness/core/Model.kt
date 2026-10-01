package org.openmobifitness.core

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

enum class Machine { ELLIPTICAL, BIKE, ROWER, TREADMILL, HEART, UNKNOWN }
enum class Protocol { V1, V2, FTMS, HUANTONG, DEMO, UNKNOWN }
data class ResistanceRange(val min: Double, val max: Double, val increment: Double = 1.0) {
    init { require(min.isFinite() && max.isFinite() && increment.isFinite() && min >= 0 && max >= min && max <= 3276.7 && increment > 0) }
    fun contains(value: Double) = value.isFinite() && value >= min - 0.0001 && value <= max + 0.0001 &&
        kotlin.math.abs((value - min) / increment - ((value - min) / increment).roundToInt()) < 0.001
    fun percent(percent: Int): Double = (min + ((max - min) * percent.coerceIn(0, 100) / 100 / increment).roundToInt() * increment).coerceIn(min, max)
    fun next(value: Double, delta: Int) = (min + (((value - min) / increment).roundToInt() + delta) * increment).coerceIn(min, max)
    fun percentage(value: Double): Int = if(max==min) 0 else ((value-min)/(max-min)*100).roundToInt().coerceIn(0,100)
}
data class Metrics(
    val cadence: Double? = null, val resistance: Double? = null, val speedMps: Double? = null,
    val distanceM: Double? = null, val heartBpm: Int? = null, val powerW: Double? = null, val strokes: Int? = null,
    val caloriesKcal: Double? = null, val inclinePercent: Double? = null, val strideM: Double? = null,
    val forceN: Double? = null, val stepRate: Double? = null, val stepCount: Int? = null, val targetCadence: Double? = null
) {
    fun merge(new: Metrics) = Metrics(new.cadence ?: cadence, new.resistance ?: resistance, new.speedMps ?: speedMps,
        new.distanceM ?: distanceM, new.heartBpm ?: heartBpm, new.powerW ?: powerW, new.strokes ?: strokes,
        new.caloriesKcal ?: caloriesKcal,new.inclinePercent ?: inclinePercent,new.strideM ?: strideM,
        new.forceN ?: forceN,new.stepRate ?: stepRate,new.stepCount ?: stepCount,new.targetCadence ?: targetCadence)
}
data class Session(
    val id: String = UUID.randomUUID().toString(), val start: String = Instant.now().toString(),
    val end: String = "", val zone: String = ZoneId.systemDefault().id, val device: String = "",
    val machine: Machine = Machine.UNKNOWN, val protocol: Protocol = Protocol.UNKNOWN,
    val elapsedMs: Long = 0, val distanceM: Double? = null, val demo: Boolean = false, val status: String = "active",
    val caloriesKcal: Double? = null, val caloriesEstimated: Boolean = false, val distanceEstimated: Boolean = false,
    val weightKg: Double? = null, val met: Double? = null
)
data class Sample(val sessionId: String, val elapsedMs: Long, val metrics: Metrics)
enum class Condition { TIME, DISTANCE, STROKES }
data class Step(val condition: Condition = Condition.TIME, val target: Double, val resistancePercent: Int? = null) {
    init { require(target.isFinite() && target > 0 && target <= 86400); require(resistancePercent == null || resistancePercent in 0..100) }
}
data class Workout(val id: String = UUID.randomUUID().toString(), val title: String, val steps: List<Step>, val builtin: Boolean = false) {
    init { require(title.isNotBlank() && title.length <= 120 && steps.size in 1..200) }
}
object Presets {
    // Original, editable templates. Device-range targets are not physiological intensity measurements.
    private fun plan(id: String, vararg sections: Pair<Int, Int>) = Workout(id, id, sections.map { Step(target = it.first * 60.0, resistancePercent = it.second) }, true)
    val all = listOf(
        plan("warmup", 2 to 5, 2 to 10, 2 to 15, 2 to 20),
        plan("recovery", 2 to 5, 6 to 15, 2 to 5),
        plan("steady20", 5 to 10, 10 to 30, 5 to 10),
        plan("steady30", 5 to 10, 20 to 30, 5 to 10),
        plan("endurance", 5 to 10, 35 to 25, 5 to 10),
        plan("interval10", 2 to 10, 1 to 35, 1 to 10, 1 to 35, 1 to 10, 1 to 35, 1 to 10, 2 to 5),
        plan("interval20", 3 to 10, 2 to 40, 2 to 15, 2 to 40, 2 to 15, 2 to 40, 2 to 15, 2 to 30, 3 to 5),
        plan("interval30", 5 to 10, 3 to 40, 2 to 15, 3 to 40, 2 to 15, 3 to 40, 2 to 15, 3 to 40, 2 to 15, 5 to 5),
        plan("pyramid20", 3 to 10, 3 to 20, 3 to 30, 2 to 40, 3 to 30, 3 to 20, 3 to 5),
        plan("pyramid30", 5 to 10, 4 to 20, 4 to 30, 4 to 40, 4 to 30, 4 to 20, 5 to 5),
        plan("progressive", 5 to 10, 5 to 20, 5 to 30, 5 to 40, 5 to 10),
        plan("cooldown", 2 to 20, 2 to 15, 2 to 10, 2 to 5),
        plan("light", 5 to 5, 10 to 15, 5 to 5),
        plan("moderate", 5 to 10, 20 to 30, 5 to 5),
        plan("vigorous", 5 to 10, 5 to 40, 10 to 65, 5 to 40, 5 to 10),
        plan("strength", 5 to 10, 2 to 55, 2 to 15, 2 to 60, 2 to 15, 2 to 65, 2 to 15, 5 to 5),
        plan("weight", 5 to 10, 10 to 35, 10 to 45, 10 to 35, 5 to 5),
        plan("hiit", 5 to 10, 1 to 45, 1 to 15, 1 to 50, 1 to 15, 1 to 55, 1 to 15, 1 to 60, 1 to 15, 1 to 65, 1 to 15, 5 to 5),
        plan("cardio40", 5 to 10, 5 to 35, 10 to 50, 10 to 60, 5 to 45, 5 to 10),
        plan("hiit30", 5 to 10, 1 to 55, 1 to 20, 1 to 60, 1 to 20, 1 to 60, 1 to 20, 1 to 65, 1 to 20, 1 to 65, 1 to 20, 1 to 70, 1 to 20, 1 to 70, 1 to 20, 1 to 75, 1 to 20, 1 to 70, 1 to 20, 1 to 60, 1 to 20, 5 to 10),
        plan("hiit40", 5 to 10, 5 to 35, 3 to 65, 2 to 25, 3 to 70, 2 to 25, 3 to 75, 2 to 25, 3 to 80, 2 to 25, 5 to 40, 5 to 10)
    )
}

/** Uses active session time, never wall clock. Missing sensor targets cannot complete a stage. */
class TrainingEngine(val workout: Workout) {
    var index = 0; private set
    private var startMs = 0L
    private var startDistance: Double? = null
    private var startStrokes: Int? = null
    var done = false; private set
    fun remainingMs(elapsedMs: Long): Long? = when {
        done -> 0L
        workout.steps[index].condition!=Condition.TIME -> null
        else -> (startMs+(workout.steps[index].target*1000).toLong()-elapsedMs).coerceAtLeast(0)
    }
    fun resetBaseline(elapsedMs: Long, distance: Double?, strokes: Int?) {
        startMs = elapsedMs; startDistance = distance; startStrokes = strokes
    }
    fun progress(elapsedMs: Long, distance: Double?, strokes: Int?): Double {
        val s = workout.steps[index]
        val v = when(s.condition) {
            Condition.TIME -> (elapsedMs - startMs) / 1000.0
            Condition.DISTANCE -> if(distance != null && startDistance != null) distance - startDistance!! else 0.0
            Condition.STROKES -> if(strokes != null && startStrokes != null) (strokes - startStrokes!!).toDouble() else 0.0
        }
        return (v / s.target).coerceIn(0.0, 1.0)
    }
    fun tick(elapsedMs: Long, distance: Double?, strokes: Int?): Boolean {
        if (done) return false
        if (startDistance == null) startDistance = distance
        if (startStrokes == null) startStrokes = strokes
        var advanced = false
        while (!done && progress(elapsedMs, distance, strokes) >= 1.0) {
            advanced = true
            val previous = workout.steps[index]
            if (index == workout.steps.lastIndex) {
                done = true
            } else {
                // Carry elapsed time through timed stages so a delayed tick does not
                // extend every interval. Sensor boundaries use the observed sample.
                val boundary = if (previous.condition == Condition.TIME) startMs + (previous.target * 1000).toLong() else elapsedMs
                index++
                resetBaseline(boundary, distance, strokes)
            }
        }
        return advanced
    }
}
