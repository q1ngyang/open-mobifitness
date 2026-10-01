package org.openmobifitness.core

/** All selectable fields in the two original apps, plus useful derived summaries. */
enum class MetricId {
    TIME, DISTANCE, CALORIES, HEART, SPEED, RESISTANCE, CADENCE, STROKE_RATE, STROKES,
    FORCE, INCLINE, STEP_RATE, STRIDE, TARGET_CADENCE, POWER, PACE, AVERAGE_SPEED
}

object Estimates {
    // Legacy apps use this virtual speed conversion; it is not physical travel distance.
    fun legacySpeed(cadence: Double?, machine: Machine): Double? {
        if(cadence==null || !cadence.isFinite() || cadence !in 0.0..300.0) return null
        return when(machine) {
            Machine.ELLIPTICAL -> cadence/2.68/4/3.6
            Machine.BIKE -> cadence/2.68/3.6
            else -> null
        }
    }
    // Gross energy estimate: MET × 3.5 × body mass / 200 kcal per minute.
    fun kcal(met: Double,weightKg: Double,milliseconds: Long): Double {
        require(met in 1.0..20.0 && weightKg in 20.0..300.0 && milliseconds>=0)
        return met*3.5*weightKg/200*milliseconds/60000
    }
}

/** Session-relative counters. Device resets, pauses and reconnects never add offline movement. */
class TelemetryTotals(initial: Metrics = Metrics()) {
    var distanceM: Double? = null; private set
    var caloriesKcal: Double? = null; private set
    var strokes: Int? = null; private set
    var steps: Int? = null; private set
    var distanceEstimated=false; private set
    var caloriesEstimated=false; private set
    private var lastDistance=initial.distanceM
    private var lastEnergy=initial.caloriesKcal
    private var lastStrokes=initial.strokes
    private var lastSteps=initial.stepCount
    fun rebase() { lastDistance=null; lastEnergy=null; lastStrokes=null; lastSteps=null }
    fun update(m: Metrics,elapsedMs: Long,active: Boolean,machine: Machine,protocol: Protocol,weightKg: Double,met: Double) {
        val distanceDelta=m.distanceM?.let { raw -> lastDistance?.let { (raw-it).coerceAtLeast(0.0) } ?: 0.0 }
        val energyDelta=m.caloriesKcal?.let { raw -> lastEnergy?.let { (raw-it).coerceAtLeast(0.0) } ?: 0.0 }
        val strokeDelta=m.strokes?.let { raw -> lastStrokes?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        val stepDelta=m.stepCount?.let { raw -> lastSteps?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        lastDistance=m.distanceM; lastEnergy=m.caloriesKcal; lastStrokes=m.strokes; lastSteps=m.stepCount
        if(!active) return
        // A current sample cannot explain a long suspension gap; do not extrapolate it.
        val interval=elapsedMs.coerceIn(0,5000)
        val virtual=if(protocol in setOf(Protocol.V1,Protocol.V2)) Estimates.legacySpeed(m.cadence,machine) else null
        if(distanceDelta!=null) distanceM=(distanceM ?: 0.0)+distanceDelta
        else (m.speedMps ?: virtual)?.let { distanceM=(distanceM ?: 0.0)+it*interval/1000; distanceEstimated=true }
        if(energyDelta!=null) caloriesKcal=(caloriesKcal ?: 0.0)+energyDelta
        else {
            val moving=(m.cadence ?: m.stepRate ?: 0.0)>0 || (m.speedMps ?: 0.0)>0
            if(m.cadence!=null || m.stepRate!=null || m.speedMps!=null) {
                caloriesKcal=(caloriesKcal ?: 0.0)+if(moving) Estimates.kcal(met,weightKg,interval) else 0.0
                caloriesEstimated=true
            }
        }
        strokeDelta?.let { strokes=(strokes ?: 0)+it }; stepDelta?.let { steps=(steps ?: 0)+it }
    }
}
