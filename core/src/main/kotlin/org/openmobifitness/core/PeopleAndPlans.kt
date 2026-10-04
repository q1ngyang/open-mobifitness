package org.openmobifitness.core

import java.text.Normalizer
import java.util.UUID

const val LEGACY_USER_ID="95785d7e-77bb-4622-a641-6171369af043"

data class UserProfile(
    val id: String=UUID.randomUUID().toString(), val name: String,
    val weightKg: Double?=null, val met: Double=5.0, val metSource: String="default",
    val avatar: String="", val createdAt: Long=System.currentTimeMillis(), val removedAt: Long?=null,
    val deleted: Boolean=false, val preferences: String="", val legacyHints: String=""
) {
    init {
        require(UUID.fromString(id).toString()==id)
        require(name.isNotBlank() && name.codePointCount(0,name.length)<=24 && name.none { it.isISOControl() })
        require(weightKg==null || weightKg.isFinite() && weightKg in 20.0..300.0)
        require(met.isFinite() && met in 1.0..20.0 && metSource in setOf("default","user","legacy"))
        require(avatar.isEmpty() || avatar.matches(Regex("[0-9a-f]{64}\\.jpg")))
        require(createdAt>=0 && (removedAt==null || removedAt>=0) && (!deleted || removedAt!=null))
    }
    val available get()=removedAt==null && !deleted
    val normalizedName get()=Normalizer.normalize(name.trim(),Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
}

enum class IdentityPolicy { STARTUP, EACH_RECORDING, REMEMBER }
enum class StageKind { WARMUP, TRAINING, RECOVERY, COOLDOWN }
enum class HintMode { FOLLOW_PLAN, CUSTOM, OFF }
data class StageRange(val mode: HintMode=HintMode.FOLLOW_PLAN,val lower: Double?=null,val upper: Double?=null) {
    init { PersonalRange(mode==HintMode.CUSTOM,lower,upper) }
    fun resolve(default: PersonalRange)=when(mode) {
        HintMode.FOLLOW_PLAN -> default
        HintMode.CUSTOM -> PersonalRange(true,lower,upper)
        HintMode.OFF -> PersonalRange()
    }
}
data class WorkoutHints(val frequency: PersonalRange=PersonalRange(),val heart: PersonalRange=PersonalRange(),val sound: Boolean=false,val vibration: Boolean=false) {
    init { require(listOfNotNull(heart.lower,heart.upper).all { it>0 && it%1.0==0.0 }) }
}

/** Editing may be offline. Physical writes still require the connected driver's evidence. */
object WorkoutPolicy {
    val supported=setOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL)
    /** Retire legacy aliases so an unfavorited typed template cannot reappear on reload. */
    fun favorites(ids: Set<String>): Set<String> = ids.flatMap { id ->
        if(Presets.all.any { it.id==id }) listOf("${id}_elliptical","${id}_bike") else listOf(id)
    }.toSet()
    fun conditions(machine: Machine)=if(machine==Machine.ROWER) Condition.entries else listOf(Condition.TIME,Condition.DISTANCE)
    fun validate(w: Workout) {
        val m=w.machine ?: return // Retained legacy template, cannot start until classified.
        require(m in supported && w.steps.all { it.condition in conditions(m) })
        require(w.steps.none { it.speedTargetMps!=null && m!=Machine.TREADMILL })
        require(w.steps.none { it.inclineTargetPercent!=null && m!=Machine.TREADMILL })
        require(w.steps.none { it.resistancePercent!=null && m==Machine.TREADMILL })
    }
    fun compatible(w: Workout,machine: Machine,userId: String?)=w.machine==machine && !w.legacyUnclassified && (w.builtin || w.ownerUserId==userId && userId!=null)
    fun templates(machine: Machine): List<Workout> = BuiltinPrograms.forMachine(machine)
}

/** A shared session budget, unaffected by stage changes, missing data or pause/resume. */
class SessionHintThrottle {
    private var last: Long?=null
    fun accept(now: Long,event: Boolean): Boolean {
        if(!event || last?.let { now-it<30_000 }==true) return false
        last=now; return true
    }
    fun reset() { last=null }
}
