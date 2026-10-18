package org.openmobifitness.core

/** Receipt times belong to individual measurements, not to a BLE connection.
 * A resistance or stroke-curve packet cannot extend a speed sample's lifetime. */
data class MetricTimes(val received: Map<MetricId, Long> = emptyMap()) {
    fun update(value: Metrics, now: Long): MetricTimes {
        val times=received.toMutableMap()
        value.invalidated.forEach(times::remove)
        listOf(MetricId.CADENCE to value.cadence,MetricId.SPEED to value.speedMps,
            MetricId.POWER to value.powerW,MetricId.FORCE to value.forceN,
            MetricId.STEP_RATE to value.stepRate,MetricId.STRIDE to value.strideM,
            MetricId.HEART to value.heartBpm,MetricId.RESISTANCE to value.resistance,
            MetricId.DISTANCE to value.distanceM,MetricId.CALORIES to value.caloriesKcal,
            MetricId.STROKES to value.strokes,MetricId.JUMPS to value.jumpCount,
            MetricId.REPETITIONS to value.repetitions,MetricId.INCLINE to value.inclinePercent).forEach { (id,v) ->
            if(v!=null) times[id]=now
        }
        // Legacy power may be recalculated on a resistance-only packet using cached cadence.
        if(value.powerEstimated && value.powerW!=null && value.cadence==null)
            times[MetricId.POWER]=times[MetricId.CADENCE] ?: Long.MIN_VALUE
        return MetricTimes(times)
    }
    fun fresh(value: Metrics, now: Long, fallbackMotionAt: Long?=null, fallbackHeartAt: Long?=null): Metrics {
        fun current(id: MetricId, age: Long=5000): Boolean {
            val at=received[id] ?: if(received.isEmpty()) (if(id==MetricId.HEART) fallbackHeartAt else fallbackMotionAt) else null
            return at!=null && at<=now && now-at in 0 until age
        }
        val speed=value.speedMps.takeIf { current(MetricId.SPEED) }
        val rate=value.stepRate.takeIf { current(MetricId.STEP_RATE) }
        return value.copy(cadence=value.cadence.takeIf { current(MetricId.CADENCE) },speedMps=speed,
            powerW=value.powerW.takeIf { current(MetricId.POWER) },forceN=value.forceN.takeIf { current(MetricId.FORCE) },
            stepRate=rate,strideM=value.strideM.takeIf { current(MetricId.STRIDE) && speed!=null && speed>0 && rate!=null && rate>0 },
            heartBpm=value.heartBpm.takeIf { current(MetricId.HEART,10000) })
    }
}

/** Reject impossible/sentinel exercise inputs while explicitly clearing the old field.
 * These are input validity bounds, not personal workout targets. */
fun Metrics.validated(): Metrics {
    val rejected=invalidated.toMutableSet()
    fun number(id: MetricId, value: Double?, minimum: Double=0.0, maximum: Double=Double.MAX_VALUE): Double? {
        if(value!=null && (!value.isFinite() || value<minimum || value>maximum)) { rejected+=id; return null }
        return value
    }
    val cadence=number(MetricId.CADENCE,cadence,maximum=300.0)
    val speed=number(MetricId.SPEED,speedMps,maximum=100.0)
    val power=number(MetricId.POWER,powerW,minimum=if(powerEstimated) 0.0 else -32768.0,maximum=if(powerEstimated) 50000.0 else 32767.0)
    val rate=number(MetricId.STEP_RATE,stepRate,maximum=600.0)
    val force=number(MetricId.FORCE,forceN,minimum=-32768.0,maximum=32767.0)
    val stride=number(MetricId.STRIDE,strideM,maximum=10.0)
    val incline=number(MetricId.INCLINE,inclinePercent,minimum=-100.0,maximum=100.0)
    val resistance=number(MetricId.RESISTANCE,resistance,maximum=3276.7)
    val heart=heartBpm?.takeIf { if(it in 1..255) true else { rejected+=MetricId.HEART; false } }
    return copy(cadence=cadence,speedMps=speed,powerW=power,stepRate=rate,forceN=force,strideM=stride,heartBpm=heart,
        resistance=resistance,inclinePercent=incline,invalidated=rejected)
}

/** A fixed-width counter only wraps near the two ends and within a bounded increment.
 * Every new connection, pause and reset establishes a new baseline instead. */
fun counterDelta(previous: Long, current: Long, modulus: Long?, maxWrapDelta: Long): Long {
    if(current>=previous) return current-previous
    if(modulus==null || previous !in 0 until modulus || current !in 0 until modulus) return 0
    val wrapped=modulus-previous+current
    return if(previous>=modulus-maxWrapDelta && current<=maxWrapDelta && wrapped in 1..maxWrapDelta) wrapped else 0
}
