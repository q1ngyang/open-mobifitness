package org.openmobifitness.app

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.openmobifitness.core.*

data class SavedDevice(val address: String,val name: String,val machine: Machine,val heart: Boolean=false,val note: String="")
data class HintPreferences(val frequency: Map<Machine,PersonalRange> = emptyMap(),val heart: PersonalRange=PersonalRange(),val sound: Boolean=false,val vibration: Boolean=false)
data class OverlayPosition(val x: Float,val y: Float) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) }
}
/** Local portable settings. No pairing credentials or absolute display coordinates. */
class LocalPreferences(private val prefs: SharedPreferences) {
    val devices=MutableStateFlow(readDevices())
    val revision=MutableStateFlow(0)
    val hints=MutableStateFlow(readHints())
    fun reload() { devices.value=readDevices(); hints.value=readHints(); revision.value++ }
    private fun readDevices(): List<SavedDevice> = runCatching {
        val rows=JSONArray(prefs.getString("saved_devices","[]"))
        (0 until rows.length()).map { i -> val r=rows.getJSONObject(i); SavedDevice(r.getString("address"),r.getString("name"),Machine.valueOf(r.getString("machine")),r.optBoolean("heart"),r.optString("note")) }.distinctBy { it.address }.take(16)
    }.getOrDefault(emptyList()).ifEmpty {
        prefs.getString("last_address",null)?.let { listOf(SavedDevice(it,prefs.getString("last_name","") ?: "",Machine.UNKNOWN)) }.orEmpty()
    }
    fun remember(device: SavedDevice) {
        val previous=devices.value.firstOrNull { it.address==device.address }
        val row=device.copy(note=previous?.note ?: device.note)
        if(previous==row) return
        writeDevices((devices.value.filterNot { it.address==row.address }+row).takeLast(16))
    }
    fun rename(address: String,note: String) { require(note.length<=80); writeDevices(devices.value.map { if(it.address==address) it.copy(note=note.trim()) else it }) }
    fun remove(address: String) {
        writeDevices(devices.value.filterNot { it.address==address })
        prefs.edit().remove("presets.$address").apply()
        if(prefs.getString("last_address",null)==address) prefs.edit().remove("last_address").remove("last_name").apply()
        revision.value++
    }
    private fun writeDevices(rows: List<SavedDevice>) {
        val array=JSONArray(); rows.forEach { d -> array.put(JSONObject().put("address",d.address).put("name",d.name).put("machine",d.machine.name).put("heart",d.heart).put("note",d.note)) }
        prefs.edit().putString("saved_devices",array.toString()).apply(); devices.value=rows
    }
    fun presets(address: String,machine: Machine,range: ResistanceRange): List<Double> = runCatching {
        val obj=JSONObject(prefs.getString("presets.$address",null) ?: return emptyList())
        if(obj.getString("machine")!=machine.name) return emptyList()
        val values=obj.getJSONArray("values")
        (0 until values.length()).map { values.getDouble(it) }.filter { validPreset(it,range) }.distinct().take(4)
    }.getOrDefault(emptyList())
    fun savePresets(address: String,machine: Machine,range: ResistanceRange,values: List<Double>) {
        require(address.isNotBlank() && values.size<=4 && values.distinct().size==values.size && values.all { validPreset(it,range) })
        prefs.edit().putString("presets.$address",JSONObject().put("machine",machine.name).put("values",JSONArray(values)).toString()).apply(); revision.value++
    }
    private fun validPreset(v: Double,r: ResistanceRange)=v.isFinite() && v in r.min..r.max && kotlin.math.abs(r.next(v,0)-v)<1e-6
    private fun range(o: JSONObject?): PersonalRange=if(o==null) PersonalRange() else PersonalRange(o.optBoolean("enabled"),if(o.has("lower")) o.getDouble("lower") else null,if(o.has("upper")) o.getDouble("upper") else null)
    private fun json(r: PersonalRange)=JSONObject().put("enabled",r.enabled).apply { r.lower?.let { put("lower",it) }; r.upper?.let { put("upper",it) } }
    private fun readHints(): HintPreferences=runCatching {
        val o=JSONObject(prefs.getString("personal_hints","{}")); val f=o.optJSONObject("frequency")
        HintPreferences(Machine.entries.mapNotNull { m -> f?.optJSONObject(m.name)?.let { m to range(it) } }.toMap(),range(o.optJSONObject("heart")),o.optBoolean("sound"),o.optBoolean("vibration"))
    }.getOrDefault(HintPreferences())
    fun saveHints(value: HintPreferences) {
        val f=JSONObject(); value.frequency.forEach { (m,r) -> f.put(m.name,json(r)) }
        prefs.edit().putString("personal_hints",JSONObject().put("frequency",f).put("heart",json(value.heart)).put("sound",value.sound).put("vibration",value.vibration).toString()).apply()
        hints.value=value
    }
    fun position(landscape: Boolean): OverlayPosition?=runCatching {
        val key="overlay_position.${if(landscape) "landscape" else "portrait"}"
        if(!prefs.contains("$key.x")) null else OverlayPosition(prefs.getFloat("$key.x",0f),prefs.getFloat("$key.y",0f))
    }.getOrNull()
    fun position(landscape: Boolean,position: OverlayPosition) {
        val key="overlay_position.${if(landscape) "landscape" else "portrait"}"
        prefs.edit().putFloat("$key.x",position.x).putFloat("$key.y",position.y).apply()
    }
    fun resetPositions() { prefs.edit().apply { listOf("portrait","landscape").forEach { remove("overlay_position.$it.x"); remove("overlay_position.$it.y") } }.apply(); revision.value++ }
}
