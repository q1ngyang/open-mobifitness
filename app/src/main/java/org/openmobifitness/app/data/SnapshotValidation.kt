package org.openmobifitness.app.data

import org.json.JSONObject
import org.openmobifitness.core.*

/** Versioned JSON boundaries used by every staged restore. Unknown structures fail closed. */
internal object SnapshotValidation {
    private fun keys(obj: JSONObject,allowed: Set<String>) { require(obj.keys().asSequence().all { it in allowed }) { "snapshot_field" } }
    fun capability(session: Session) {
        if(session.capabilitySnapshot.isEmpty()) return
        val obj=JSONObject(session.capabilitySnapshot)
        keys(obj,setOf("version","machine","model","protocol","evidence","resistanceWrite","speedWrite","inclineWrite","resistanceFeedback","resistanceMin","resistanceMax","resistanceIncrement","units","observed"))
        require(obj.getInt("version")==1 && obj.getString("machine")==session.machine.name && obj.getString("protocol")==session.protocol.name) { "capability_version" }
        require(obj.getString("model").length<=120 && obj.getString("evidence").length<=120)
        listOf("resistanceWrite","speedWrite","inclineWrite","resistanceFeedback").forEach { if(obj.has(it)) require(obj.getString(it) in setOf("supported","unsupported","unknown")) }
        listOf("resistanceMin","resistanceMax","resistanceIncrement").map { obj.has(it) && !obj.isNull(it) }.let { fields ->
            if(fields.any { it }) { require(fields.all { it }); ResistanceRange(obj.getDouble("resistanceMin"),obj.getDouble("resistanceMax"),obj.getDouble("resistanceIncrement")) }
        }
        obj.optJSONObject("units")?.let { units -> keys(units,setOf("frequency","speed","incline","resistance","power","force","load")); units.keys().forEach { require(units.getString(it).length<=24) } }
        obj.optJSONObject("observed")?.let { observed -> keys(observed,SeriesMetric.entries.map { it.name }.toSet()); observed.keys().forEach { require(observed.getString(it) in setOf("supported","unsupported","unknown")) } }
    }
    fun workout(session: Session): Set<String> {
        if(session.workoutSnapshot.isEmpty()) { require(session.enteredStageIds.isEmpty()); return emptySet() }
        val plan=Exchange.parse(session.workoutSnapshot).workouts.single()
        require(plan.id==session.workoutId && plan.machine==session.machine && !plan.legacyUnclassified) { "workout_snapshot" }
        require(plan.ownerUserId==null || plan.ownerUserId==session.startedUserId) { "workout_snapshot_owner" }
        if(session.enteredStageIds.isNotEmpty()) require(plan.steps.take(session.enteredStageIds.split(';').size).map { it.id }==session.enteredStageIds.split(';')) { "entered_stage_reference" }
        return setOfNotNull(plan.ownerUserId)
    }
    fun legacy(text: String) {
        if(text.isEmpty()) return
        val obj=JSONObject(text); keys(obj,setOf("version","personal_hints","target_cadence")); require(obj.getInt("version")==1)
        if(obj.has("target_cadence") && !obj.isNull("target_cadence")) require(obj.getInt("target_cadence") in 1..300)
        if(obj.has("personal_hints") && !obj.isNull("personal_hints")) {
            val hints=when(val raw=obj.get("personal_hints")) { is JSONObject -> raw; is String -> JSONObject(raw); else -> error("legacy_hints") }
            keys(hints,setOf("frequency","heart","sound","vibration"))
            fun range(r: JSONObject,heart: Boolean=false) { keys(r,setOf("enabled","lower","upper")); val range=PersonalRange(r.optBoolean("enabled"),if(r.has("lower")) r.getDouble("lower") else null,if(r.has("upper")) r.getDouble("upper") else null); if(heart) require(listOfNotNull(range.lower,range.upper).all { it>0 && it%1.0==0.0 }) }
            hints.optJSONObject("frequency")?.let { f -> keys(f,Machine.entries.map { it.name }.toSet()); f.keys().forEach { range(f.getJSONObject(it)) } }
            hints.optJSONObject("heart")?.let { range(it,true) }
            listOf("sound","vibration").forEach { if(hints.has(it)) hints.getBoolean(it) }
        }
    }
}
