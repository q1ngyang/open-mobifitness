package org.openmobifitness.core

/** Metric choices describe the machine, not whichever fields happened to arrive last.
 * A temporarily missing sensor must not erase the user's selection.
 */
object MetricCatalog {
    private val common = listOf(MetricId.TIME, MetricId.DISTANCE, MetricId.CALORIES, MetricId.HEART)
    fun forMachine(machine: Machine): List<MetricId> = (if(machine in setOf(Machine.JUMP_ROPE,Machine.DUMBBELL)) common-MetricId.DISTANCE else common) + when (machine) {
        Machine.ELLIPTICAL -> listOf(MetricId.CADENCE, MetricId.RESISTANCE, MetricId.POWER, MetricId.SPEED, MetricId.AVERAGE_SPEED)
        Machine.BIKE -> listOf(MetricId.CADENCE, MetricId.RESISTANCE, MetricId.POWER, MetricId.SPEED, MetricId.AVERAGE_SPEED)
        Machine.ROWER -> listOf(MetricId.STROKE_RATE, MetricId.STROKES, MetricId.RESISTANCE, MetricId.POWER, MetricId.FORCE, MetricId.PACE, MetricId.SPEED, MetricId.AVERAGE_SPEED, MetricId.TARGET_CADENCE)
        Machine.TREADMILL -> listOf(MetricId.SPEED, MetricId.AVERAGE_SPEED, MetricId.INCLINE, MetricId.STEP_RATE, MetricId.STRIDE, MetricId.POWER, MetricId.FORCE)
        Machine.JUMP_ROPE -> listOf(MetricId.JUMPS,MetricId.JUMP_RATE,MetricId.CONTINUOUS_JUMPS,MetricId.JUMP_INTERRUPTS)
        Machine.DUMBBELL -> listOf(MetricId.REPETITIONS,MetricId.LOAD)
        Machine.HEART, Machine.UNKNOWN -> emptyList()
    }
    fun trainingDefaults(machine: Machine): List<MetricId> = when (machine) {
        Machine.ROWER -> listOf(MetricId.DISTANCE, MetricId.CALORIES, MetricId.STROKE_RATE, MetricId.HEART, MetricId.POWER, MetricId.PACE)
        Machine.TREADMILL -> listOf(MetricId.DISTANCE, MetricId.CALORIES, MetricId.SPEED, MetricId.HEART, MetricId.STEP_RATE, MetricId.INCLINE)
        Machine.ELLIPTICAL, Machine.BIKE -> listOf(MetricId.DISTANCE, MetricId.CALORIES, MetricId.CADENCE, MetricId.HEART, MetricId.POWER, MetricId.SPEED)
        Machine.JUMP_ROPE -> listOf(MetricId.JUMPS,MetricId.CALORIES,MetricId.JUMP_RATE,MetricId.HEART,MetricId.CONTINUOUS_JUMPS,MetricId.JUMP_INTERRUPTS)
        Machine.DUMBBELL -> listOf(MetricId.REPETITIONS,MetricId.LOAD,MetricId.TIME,MetricId.HEART)
        else -> common
    }
    fun filter(machine: Machine, values: List<MetricId>): List<MetricId> = values.filter { it in forMachine(machine) }.distinct()
}
