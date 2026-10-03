package org.openmobifitness.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.openmobifitness.app.DisplayScope
import org.openmobifitness.core.*

enum class PreferenceGroup { APPEARANCE, METRICS, HINTS, DEVICES, OVERLAY }

/** Independently versioned, allowlisted settings. Unknown keys/types are rejected. */
object PortablePreferences {
    fun group(key: String): PreferenceGroup?=when {
        key in setOf("theme","imperial","weight_kg","estimate_met","target_cadence") -> PreferenceGroup.APPEARANCE
        key=="favorite_workouts" || DisplayScope.entries.any { key==it.key || Machine.entries.any { m -> key=="${it.key}.${m.name}" } } -> PreferenceGroup.METRICS
        key=="personal_hints" -> PreferenceGroup.HINTS
        key=="saved_devices" || key.startsWith("presets.") && key.length in 9..100 -> PreferenceGroup.DEVICES
        key in setOf("floating_enabled","auto_floating","overlay_position.portrait.x","overlay_position.portrait.y","overlay_position.landscape.x","overlay_position.landscape.y") -> PreferenceGroup.OVERLAY
        else -> null
    }
    fun export(prefs: SharedPreferences): String {
        val values=JSONObject()
        prefs.all.forEach { (key,value) -> if(group(key)!=null) values.put(key,if(value is Set<*>) JSONArray(value.toList()) else value) }
        return JSONObject().put("version",1).put("groups",JSONArray(PreferenceGroup.entries.map { it.name })).put("values",values).toString().also { validate(it) }
    }
    fun select(json: String,groups: Set<PreferenceGroup>): String {
        val source=validate(json); val values=JSONObject()
        source.getJSONObject("values").let { all -> all.keys().forEach { key -> if(group(key) in groups) values.put(key,all.get(key)) } }
        return JSONObject().put("version",1).put("groups",JSONArray(groups.map { it.name })).put("values",values).toString()
    }
    fun validate(json: String): JSONObject {
        require(json.length<=1024*1024)
        val root=JSONObject(json); require(root.getInt("version")==1)
        val groups=root.getJSONArray("groups"); val selected=(0 until groups.length()).map { PreferenceGroup.valueOf(groups.getString(it)) }.toSet()
        val values=root.getJSONObject("values"); require(values.length()<=256)
        values.keys().forEach { key ->
            require(group(key) in selected)
            val value=values.get(key)
            when {
                key=="theme" -> require(value in setOf("system","light","dark"))
                key in setOf("imperial","floating_enabled","auto_floating") -> require(value is Boolean)
                key=="target_cadence" -> require(value is Number && value.toDouble()%1.0==0.0 && value.toInt() in 1..300)
                key in setOf("weight_kg","estimate_met") -> require(value is Number && value.toDouble().isFinite() && value.toDouble() in if(key=="weight_kg") 20.0..300.0 else 1.0..20.0)
                key.startsWith("overlay_position.") -> require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.0..1.0)
                key=="favorite_workouts" -> { require(value is JSONArray && value.length()<=10000); for(i in 0 until value.length()) require(value.getString(i).matches(Regex("[A-Za-z0-9_-]{1,80}"))) }
                key=="saved_devices" -> {
                    require(value is String); val array=JSONArray(value); require(array.length()<=16)
                    val addresses=HashSet<String>()
                    for(i in 0 until array.length()) { val d=array.getJSONObject(i); require(d.getString("address").matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) && addresses.add(d.getString("address"))); require(d.getString("name").length<=120 && d.optString("note").length<=80); Machine.valueOf(d.getString("machine")); d.getBoolean("heart") }
                }
                key=="personal_hints" -> {
                    require(value is String); val hints=JSONObject(value)
                    fun range(o: JSONObject) { PersonalRange(o.getBoolean("enabled"),if(o.has("lower")) o.getDouble("lower") else null,if(o.has("upper")) o.getDouble("upper") else null) }
                    range(hints.getJSONObject("heart")); hints.getBoolean("sound"); hints.getBoolean("vibration")
                    val frequencies=hints.getJSONObject("frequency"); frequencies.keys().forEach { m -> require(Machine.valueOf(m) in setOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL,Machine.JUMP_ROPE)); range(frequencies.getJSONObject(m)) }
                }
                key.startsWith("presets.") -> {
                    require(value is String); val o=JSONObject(value); Machine.valueOf(o.getString("machine")); val levels=o.getJSONArray("values"); require(levels.length()<=4)
                    val seen=HashSet<Double>(); for(i in 0 until levels.length()) { val level=levels.getDouble(i); require(level.isFinite() && level in 0.0..3276.7 && seen.add(level)) }
                }
                else -> {
                    require(value is String)
                    val scope=DisplayScope.entries.first { key==it.key || key.startsWith(it.key+".") }
                    val metrics=value.split(',').map { MetricId.valueOf(it) }
                    require(metrics.isNotEmpty() && metrics.distinct()==metrics && metrics.size<=scope.limit)
                    if(key.contains('.')) require(MetricCatalog.filter(Machine.valueOf(key.substringAfter('.')),metrics)==metrics)
                }
            }
        }
        return root
    }
    /** One synchronous SharedPreferences commit; journal retained if durable commit fails. */
    fun apply(prefs: SharedPreferences,json: String): Boolean {
        val root=validate(json); val groups=root.getJSONArray("groups").let { a -> (0 until a.length()).map { PreferenceGroup.valueOf(a.getString(it)) }.toSet() }
        val values=root.getJSONObject("values"); val editor=prefs.edit()
        prefs.all.keys.filter { group(it) in groups }.forEach(editor::remove)
        if(PreferenceGroup.DEVICES in groups) { editor.remove("last_address").remove("last_name") }
        values.keys().forEach { key -> when(val value=values.get(key)) {
            is Boolean -> editor.putBoolean(key,value)
            is String -> editor.putString(key,value)
            is JSONArray -> editor.putStringSet(key,(0 until value.length()).map { value.getString(it) }.toSet())
            is Number -> if(key=="target_cadence") editor.putInt(key,value.toInt()) else editor.putFloat(key,value.toFloat())
        } }
        return editor.commit()
    }
}
