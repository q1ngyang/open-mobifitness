package org.openmobifitness.core

/** MotionData.updateCurveData: the calculation level follows feedback once per
 * active second, by max(floor(maximum / 10), 1). The UI still shows actual feedback.
 */
class LegacyResistance {
    var level = 0; private set
    fun reset() { level=0 }
    fun tick(actual: Int, maximum: Int) {
        if(level==0) level=actual
        val step=(maximum/10).coerceAtLeast(1)
        level=when { level<actual -> (level+step).coerceAtMost(actual); level>actual -> (level-step).coerceAtLeast(actual); else -> level }
    }
    fun power(rpm: Double?, range: ResistanceRange?, magneticElliptical: Boolean): Double? =
        range?.let { Estimates.legacyPower(rpm,level.toDouble(),it.copy(min=0.0),magneticElliptical,false) }
}
