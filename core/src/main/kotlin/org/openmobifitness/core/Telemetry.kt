package org.openmobifitness.core

/** All selectable fields in the two original apps, plus useful derived summaries. */
enum class MetricId {
    TIME, DISTANCE, CALORIES, HEART, SPEED, RESISTANCE, CADENCE, STROKE_RATE, STROKES,
    FORCE, INCLINE, STEP_RATE, STRIDE, TARGET_CADENCE, POWER, PACE, AVERAGE_SPEED,
    JUMPS, JUMP_RATE, CONTINUOUS_JUMPS, JUMP_INTERRUPTS, REPETITIONS, LOAD
}

object Estimates {
    /** International 2.1.14's type-0 model, with the 0B11 elliptical correction.
     * Callers supply device cadence and the vendor's calculation resistance level.
     * This is a legacy model estimate, not measured mechanical power.
     */
    fun legacyPower(cadence: Double?, resistance: Double?, range: ResistanceRange?, magneticElliptical: Boolean,truncate: Boolean=true): Double? {
        if(cadence==null || !cadence.isFinite() || cadence !in 0.0..300.0 || resistance==null || range==null || !range.contains(resistance)) return null
        if(cadence==0.0) return 0.0
        val x=kotlin.math.exp(cadence/100)
        val y=kotlin.math.exp(resistance*32/range.max.coerceAtLeast(1.0)/10)
        val watts=(39.94501450202982-67.52588516*x-39.15593086*y+27.55487038*x*x+38.79652081*x*y-0.30231185*y*y).coerceAtLeast(0.0)
        // The original display truncates to integer watts after the subtype multiplier.
        val corrected=watts*if(magneticElliptical) 1.5 else 1.0
        return if(truncate) corrected.toInt().toDouble() else corrected
    }
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
    /** International type-0 energy rate. Uses unrounded model watts, as the original app does.
     * This reproduces the vendor estimate, not a calibrated physiological measurement. */
    fun legacyKcal(watts: Double,weightKg: Double,milliseconds: Long): Double {
        require(watts.isFinite() && watts>=0 && weightKg in 20.0..300.0 && milliseconds>=0)
        return if(watts==0.0) 0.0 else (weightKg/3600.0+watts/1000.0)*milliseconds/1000.0
    }
}

/** Session-relative counters. Device resets, pauses and reconnects never add offline movement. */
class TelemetryTotals(initial: Metrics = Metrics()) {
    var distanceM: Double? = null; private set
    var caloriesKcal: Double? = null; private set
    var strokes: Int? = null; private set
    var steps: Int? = null; private set
    var jumps: Int? = null; private set
    var repetitions: Int? = null; private set
    var jumpInterruptions: Int? = null; private set
    var distanceEstimated=false; private set
    var caloriesEstimated=false; private set
    var energyModel=""; private set
    private fun source(model: String) { energyModel=if(energyModel.isEmpty() || energyModel==model) model else "mixed" }
    private var lastDistance=initial.distanceM
    private var lastEnergy=initial.caloriesKcal
    private var lastStrokes=initial.strokes
    private var lastSteps=initial.stepCount
    private var lastJumps=initial.jumpCount
    private var lastRepetitions=initial.repetitions
    private var lastInterruptions=initial.jumpInterruptions
    fun rebase() { lastDistance=null; lastEnergy=null; lastStrokes=null; lastSteps=null; lastJumps=null; lastRepetitions=null; lastInterruptions=null }
    fun update(m: Metrics,elapsedMs: Long,active: Boolean,machine: Machine,protocol: Protocol,weightKg: Double,met: Double,legacyWatts: Double?=null,legacyRowingRate: Double?=null) {
        val distanceDelta=m.distanceM?.let { raw -> lastDistance?.let { (raw-it).coerceAtLeast(0.0) } ?: 0.0 }
        val energyDelta=m.caloriesKcal?.let { raw -> lastEnergy?.let { (raw-it).coerceAtLeast(0.0) } ?: 0.0 }
        val strokeDelta=m.strokes?.let { raw -> lastStrokes?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        val stepDelta=m.stepCount?.let { raw -> lastSteps?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        val jumpDelta=m.jumpCount?.let { raw -> lastJumps?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        val repetitionDelta=m.repetitions?.let { raw -> lastRepetitions?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        val interruptionDelta=m.jumpInterruptions?.let { raw -> lastInterruptions?.let { (raw-it).coerceAtLeast(0) } ?: 0 }
        lastDistance=m.distanceM; lastEnergy=m.caloriesKcal; lastStrokes=m.strokes; lastSteps=m.stepCount
        lastJumps=m.jumpCount; lastRepetitions=m.repetitions; lastInterruptions=m.jumpInterruptions
        if(!active) return
        // A current sample cannot explain a long suspension gap; do not extrapolate it.
        val interval=elapsedMs.coerceIn(0,5000)
        val virtual=if(protocol in setOf(Protocol.V1,Protocol.V2,Protocol.HUANTONG)) Estimates.legacySpeed(m.cadence,machine) else null
        if(distanceDelta!=null) distanceM=(distanceM ?: 0.0)+distanceDelta
        else (m.speedMps ?: virtual)?.let { distanceM=(distanceM ?: 0.0)+it*interval/1000; distanceEstimated=true }
        if(energyDelta!=null) { caloriesKcal=(caloriesKcal ?: 0.0)+energyDelta; source("device") }
        else if(machine==Machine.ROWER && legacyRowingRate!=null && legacyRowingRate.isFinite() && legacyRowingRate>=0) {
            caloriesKcal=(caloriesKcal ?: 0.0)+legacyRowingRate*interval/1000
            caloriesEstimated=true; source("legacy-rowing")
        }
        else if((protocol in setOf(Protocol.V1,Protocol.V2,Protocol.HUANTONG) || (protocol==Protocol.FTMS && legacyWatts!=null)) && m.powerEstimated && machine in setOf(Machine.BIKE,Machine.ELLIPTICAL)) {
            // Unknown/stale model input stays unknown; do not silently switch to constant MET.
            if(m.cadence!=null && m.powerW!=null) {
                caloriesKcal=(caloriesKcal ?: 0.0)+if(m.cadence>0) Estimates.legacyKcal(legacyWatts ?: m.powerW,weightKg,interval) else 0.0
                caloriesEstimated=true; source(if(protocol==Protocol.V1) "legacy-v1" else "legacy-mobi")
            }
        }
        else {
            val moving=(m.cadence ?: m.stepRate ?: 0.0)>0 || (m.speedMps ?: 0.0)>0
            if(m.cadence!=null || m.stepRate!=null || m.speedMps!=null) {
                caloriesKcal=(caloriesKcal ?: 0.0)+if(moving) Estimates.kcal(met,weightKg,interval) else 0.0
                caloriesEstimated=true
                source("met")
            }
        }
        strokeDelta?.let { strokes=(strokes ?: 0)+it }; stepDelta?.let { steps=(steps ?: 0)+it }
        jumpDelta?.let { jumps=(jumps ?: 0)+it }; repetitionDelta?.let { repetitions=(repetitions ?: 0)+it }
        interruptionDelta?.let { jumpInterruptions=(jumpInterruptions ?: 0)+it }
    }
}
