package org.openmobifitness.core

data class PersonalRange(val enabled: Boolean=false,val lower: Double?=null,val upper: Double?=null) {
    init { require(lower==null || lower.isFinite() && lower>=0); require(upper==null || upper.isFinite() && upper>=0); require(lower==null || upper==null || lower<=upper); require(!enabled || lower!=null || upper!=null) }
}
enum class RangePosition { UNAVAILABLE, BELOW, WITHIN, ABOVE }
data class HintResult(val position: RangePosition=RangePosition.UNAVAILABLE,val notify: Boolean=false)
/** 5 s continuous deviation, 2-unit recovery hysteresis, 30 s between alerts.
 * Missing/inactive samples reset dwell; settings/machine changes use a new evaluator. */
class RangeHint {
    private var position=RangePosition.UNAVAILABLE
    private var since=0L
    private var alertedAt: Long?=null
    fun update(now: Long,value: Double?,range: PersonalRange,active: Boolean): HintResult {
        if(!active || !range.enabled || value==null || !value.isFinite()) { reset(); return HintResult() }
        val next=when {
            range.lower!=null && (value<range.lower || position==RangePosition.BELOW && value<range.lower+recovery(range)) -> RangePosition.BELOW
            range.upper!=null && (value>range.upper || position==RangePosition.ABOVE && value>range.upper-recovery(range)) -> RangePosition.ABOVE
            else -> RangePosition.WITHIN
        }
        if(next!=position) { position=next; since=now }
        val notify=next!=RangePosition.WITHIN && now-since>=5000 && (alertedAt==null || now-alertedAt!!>=30000)
        if(notify) alertedAt=now
        return HintResult(next,notify)
    }
    private fun recovery(range: PersonalRange)=if(range.lower!=null && range.upper!=null) minOf(2.0,(range.upper-range.lower)/2) else 2.0
    fun reset() { position=RangePosition.UNAVAILABLE; since=0; alertedAt=null }
}
