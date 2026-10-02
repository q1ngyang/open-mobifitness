package org.openmobifitness.app
import org.openmobifitness.core.Metrics
internal object DemoTelemetry {
    fun sample(old: ExerciseState,delta: Long,resistance: Double,strokes: Double): Pair<Metrics,Double> = error("Simulation is unavailable in release builds")
}
