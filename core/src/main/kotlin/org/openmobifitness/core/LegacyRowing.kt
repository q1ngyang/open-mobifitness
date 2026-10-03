package org.openmobifitness.core

import kotlin.math.*

/** Stateful pulse analysis used by the two reference MOBI APKs.
 * Units and reset semantics intentionally follow the vendor model. The device
 * adapter decides when a stale model value can be published as a live reading.
 * checkSlowMode/reportPull were recovered from Smali, not JADX's damaged branches.
 */
open class LegacyRowing {
    var state = 0; protected set
    var stroke = 1.0; protected set
    var speed = 0.0; protected set
    var spm = 0.0; protected set
    var caloriePerSecond = 0.0; protected set
    var graphValue = 0.0; protected set
    var backTime = 2.0; protected set
    var pullTime = 1.0; protected set
    var power = 0.0; protected set
    var strokeEnergy = 0.0; protected set
    var strokeDistance = 0.0; protected set
    var pullLength = 0.0; protected set
    var maxF = 0.0; protected set
    var avgF = 0.0; protected set
    var score = 0.0; protected set
    var first = true; protected set
    val freqFactor = 0.225
    var boat = 0
    var reverseMode = false
    private var type = -1
    private var trigger = 0.5
    private var timeout = 1500.0
    private var slowMode = false
    private var acc = 0
    private var count = 0
    private var subState = 0
    private var pullDot = 0
    private var last = 0.0
    private var lastLast = 1500.0
    private var t1 = 0
    private var t2 = 0
    private var t3 = 0
    private var previousT2 = 0
    private var v1 = 0.0
    private var v2 = 0.0
    private var v3 = 0.0
    private var vStart = 0.0
    private var vEnd = 0.0
    private var peakAcceleration = 0.0
    private var sum = 0.0
    private var sum1 = 0.0
    private var sum2 = 0.0
    private var sum3 = 0.0
    private var previousSum2 = 0.0
    private var p5 = 1.0
    private var p6 = 1.0
    private var p7 = 6.0
    private var p8 = 1.0
    private var buffer = DoubleArray(2)
    private val dpms = mutableListOf(180, 180, 180, 180)
    private val weight = 70.0 // The reference class has no user-weight setter.

    init { changeType(0) }

    fun changeType(value: Int) {
        if (value == type) return
        trigger = if (value == 1) 0.001 else 0.6
        timeout = if (value == 1 || value == 5) 5000.0 else 1000.0
        val coefficients = when (value) {
            0 -> doubleArrayOf(1.0, 1.0, 6.0, 1.0)
            1, 3 -> doubleArrayOf(0.85, 1.0, 6.0, 1.0)
            2 -> doubleArrayOf(0.75, 2.8, 6.0, 1.25)
            4 -> doubleArrayOf(0.7612800000000001, 13.36, 9.0, 8.0)
            5 -> doubleArrayOf(1.1, 1.0, 6.0, 1.0)
            else -> null
        }
        coefficients?.let { p5 = it[0]; p6 = it[1]; p7 = it[2]; p8 = it[3] }
        type = value
        reverseMode = value == 1
    }

    private fun slowCandidate(value: Int) = value >= 10000 || value in 1500..4000 || value == 0
    private fun checkSlowMode(value: Int, forceNormal: Boolean): Boolean {
        if (!slowMode && slowCandidate(value) && slowCandidate(last.toInt())) slowMode = true
        else if (slowMode && value in 1..599) slowMode = false
        if (forceNormal) slowMode = false
        return slowMode
    }

    open fun append(interval: Int, forceNormal: Boolean = true): Int {
        if (checkSlowMode(interval, forceNormal)) {
            pullTime = 1.0 * interval / 3.0
            backTime = 2.0 * interval / 3.0
            spm = if (interval > 0) 60000.0 / interval else 20.0
            state = 10
            return state
        }
        if (state == 10) state = 0
        val value = interval.toDouble()
        if (value <= 0) { reset(); return state }
        if (value > last && acc > 0) acc-- else if (value < last && acc < 2) acc++
        if (value > timeout) { reset(); return state }
        val index = count++
        sum += value
        if (state == 0 || state == 3) {
            vStart = max(vStart, value)
            if (acc <= 0) { t1 = index; v1 = value; sum1 = sum }
            else peakAcceleration = max(peakAcceleration, 1.0 / value - 1.0 / min(last, value / trigger))
            if (subState > 0) {
                if (subState == 1 && acc == 2) subState = 2
                else if (subState == 2 && acc == 0) subState = 0
                state = 0; lastLast = last; last = value
                return state
            }
            if ((index < 3 || acc != 2) && value >= vStart * trigger) state = 0
            else {
                acc = 2; vStart = value; vEnd = last; t3 = index; v3 = value; sum3 = sum
                backTime = abs(sum1 - sum2).let { if (it == 0.0) 1.0 else it / 1000 }
                graphValue = max(0.0, 1.0 / value - 1.0 / last) * 150 * p8
                state = 1
            }
        } else if (state != 10) {
            vEnd = min(vEnd, value)
            if (acc == 2) { t3 = index; v3 = value; sum3 = sum }
            val acceleration = max(0.0, 1.0 / value - 1.0 / lastLast) / 2
            graphValue = 150 * acceleration * p8
            peakAcceleration = max(peakAcceleration, acceleration)
            if (acc <= 0 || value > vEnd / trigger) {
                acc = 0; vEnd = value; vStart = last
                didPull()
                t1 = index; v1 = value; sum1 = sum
                if (previousT2 > 0) {
                    count -= previousT2; sum -= previousSum2; t1 -= previousT2
                    sum1 -= previousSum2; t2 -= previousT2; sum2 -= previousSum2
                    previousT2 = 0; previousSum2 = 0.0
                }
                state = 3
                if (reverseMode && pullDot > 4) subState = 1
            } else state = 2
        }
        lastLast = last; last = value
        return state
    }

    private fun barSpeed(value: Double) {
        if (value == 0.0) { buffer.fill(0.0); speed = 0.0; return }
        if (buffer[0] == 0.0) { buffer[0] = value; buffer[1] = value }
        val a = buffer[0]; val b = buffer[1]
        buffer[0] = b; buffer[1] = value; speed = (a + b + value) / 3
    }

    private fun didPull() {
        val points = max(2, t3 - t1 + 1)
        if (points <= 4) v1 = min(v3 / trigger, v1)
        previousT2 = t2; previousSum2 = sum2
        t2 = t3; v2 = v3; sum2 = sum3
        val duration = if (first) 3000.0 else sum2 - previousSum2
        spm = 60000.0 / duration
        val adjustedDuration = duration / min(1.0, sqrt(points.toDouble() / 10))
        var rawSpeed = ((abs(1.0 / v2.pow(2.0) - 1.0 / v1.pow(2.0)) / adjustedDuration).pow(1.0 / 3) * 526.3157894736843) / p5
        if (rawSpeed > 8.333333333333334) rawSpeed = 2.793296089385475
        if (spm > 200) spm = 19.0
        if (type == 3 || type == 4) {
            val mass = when (boat) {
                0 -> weight + 14
                1, 2 -> (weight * 2 + 27) / 2
                3 -> (weight * 3 + 32) / 2
                4 -> (weight * 4 + 52) / 4
                5 -> (weight * 4 + 50) / 4
                6 -> (weight * 5 + 51) / 4
                7 -> (weight * 9 + 96) / 8
                else -> 84.0
            }
            rawSpeed *= (84.0 / mass).pow(1.0 / 3)
        }
        barSpeed(rawSpeed)
        caloriePerSecond = min(0.5, ((weight / 79.5 * 300) + speed.pow(3.0) * 9.7) / 3600)
        stroke = 1.0
        val n = max(3, t2 - t1 + 1)
        power = 2.8 / (1.0 / speed).pow(3.0)
        strokeDistance = 60.0 / spm * speed
        pullLength = p7 * n
        pullTime = abs(sum2 - sum1).let { if (it == 0.0) 1.0 else it / 1000 }
        score = if (pullTime == 0.0 || backTime == 0.0) 0.0 else pullTime * 2 / backTime
        if (score > 1) score = 1 / score
        maxF = peakAcceleration * (p6 * 30000)
        avgF = (abs(1.0 / v2 - 1.0 / v1) / n * (p6 * 30000)).coerceAtLeast(0.0)
        strokeEnergy = (pullTime + backTime) * power
        pullDot = n; sum1 = sum; peakAcceleration = 0.0; first = false
    }

    fun appendOldMagnet(value: Int) {
        dpms.add(value); dpms.removeAt(0)
        val average = dpms.sum().toDouble() / dpms.size
        speed = if (average == 0.0) 0.0 else average * 0.225
        spm = average * 0.225 * 10
        capLegacyReadings()
        if (value == 0) caloriePerSecond = 0.0
    }

    fun appendOldWater(pace: Int, rate: Int) {
        speed = if (pace == 0) 0.0 else 500.0 / max(10.0, (pace - 60.0) - 40 * min(1.0, max(0.0, 1 - ((pace - 130 - 60).toDouble() / 50))))
        spm = rate.toDouble()
        capLegacyReadings()
        if (pace == 0 || rate == 0) caloriePerSecond = 0.0
    }

    private fun capLegacyReadings() {
        if (speed > 8.333333333333334) speed = 2.793296089385475
        if (spm > 200) spm = 19.0
        caloriePerSecond = ((weight / 79.5 * 300) + speed.pow(3.0) * 9.7) / 3600
    }

    open fun reset() {
        count = 0; state = 0; t1 = 0; t2 = 0; t3 = 0; previousT2 = 0
        barSpeed(0.0); spm = 0.0; acc = 0; lastLast = timeout; last = timeout
        stroke = 1.0; buffer = DoubleArray(2)
        sum = 0.0; sum1 = 0.0; sum2 = 0.0; sum3 = 0.0; previousSum2 = 0.0
        v1 = timeout; v2 = 0.0; v3 = 0.0; peakAcceleration = 0.0; first = true
    }
}

/** MRH3209 uses a pulse counter/flag algorithm rather than the water-wheel model. */
class LegacyRowing3209 : LegacyRowing() {
    private val queue = IntArray(5)
    private var count = 0
    private var lastFlag = 0
    private var lastPull = 0
    private var duration = 1

    private fun calcStroke() {
        spm = 60000.0 / duration
        strokeDistance = (count - lastPull).toDouble()
        speed = 1000.0 * strokeDistance / duration
        if (speed.isNaN()) speed = 0.0
        power = max(speed * 66.6 - 95, 0.0)
        caloriePerSecond = (power * 3.5 + 50) / 3600
        strokeEnergy = caloriePerSecond * duration / 1000
        duration = 0
    }

    override fun append(interval: Int, forceNormal: Boolean): Int {
        for (i in 0..3) queue[i] = queue[i + 1]
        queue[4] = interval
        val (a, b, c, d, e) = queue
        if (a > b && b > c && d > c && d > e && lastPull + 3 < count) {
            state = 3; stroke++; calcStroke(); lastPull = count
        } else if (d <= c || d <= e || state != 0) {
            if (state == 1) state = 2 else if (state == 3) state = 0
        } else state = 1
        count++; duration += interval
        return state
    }

    fun append(interval: Int, flag: Int): Int {
        if (interval == 0) return state
        queue[0] = queue[1]; queue[1] = queue[2]; queue[2] = interval
        val (a, b, c) = queue
        count++; duration += interval
        if (lastFlag != flag) { state = 3; stroke++; calcStroke(); lastPull = count }
        else if (((b > a && b > c) || (b < a && b < c)) && lastPull + 2 < count && state == 0) state = 1
        else if (state == 1) state = 2 else if (state == 3) state = 0
        lastFlag = flag
        return state
    }

    override fun reset() {
        queue.fill(0); lastPull = 0; count = 0; stroke = 0.0; duration = 0
        spm = 0.0; speed = 0.0; caloriePerSecond = 0.0; lastFlag = 0; state = 0
    }
}
