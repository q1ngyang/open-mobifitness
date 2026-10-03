package org.openmobifitness.core

/** Optional type-1 formulas present in both APKs. Default live sessions use type-0.
 * Preserve the vendor's integer table indexing and potentially negative outputs;
 * these are compatibility formulas, not calibrated measurements.
 */
object AlternateCalculations {
    private val factors=intArrayOf(71,71,71,40,41,42,43,44,45,47,48,49,50,51,52,54,55,56,58,60,63,67,68,69,71,72,73,74,76,79,82,84,86,89,91,94,97,99,101,104,106,108,111,113,116,119,121,123,126,128,130,132,134,136,138,140,142,145,147,149,152,154,157,160,162,164,167,169,172,175,177,180,183,185,188,191,193,195,198,201,204,207,210,213,216,219,222,226,229,232,235,239,243,247,251,255,260)
    fun caloriesPerHour(rpm: Double, level: Int, maximum: Int): Double {
        val max=maximum.coerceAtLeast(1)
        val actual=level.coerceIn(1,max)
        val factor=factors[minOf(96,(96/max)*actual)]
        return factor/1000.0*rpm*60 + (rpm/20-1.8)*((actual-1)*12.0/7) + (0.058264444*rpm*rpm-4.3285*rpm) + 125.303
    }
    fun power(rpm: Double, level: Int, maximum: Int) = caloriesPerHour(rpm,level,maximum)/4
    fun caloriesPerSecond(rpm: Double, level: Int, maximum: Int) = caloriesPerHour(rpm,level,maximum)/3600
    fun rowingPower(speedMps: Double, model: Int=0) = if(model==0) 2.8/Math.pow(1.0/speedMps,3.0) else speedMps*66.6-95
    fun rowingCaloriesPerSecond(power: Double, model: Int=0) = if(power>0 && model!=0) (power*3.5+50)/3600 else 0.0
}
