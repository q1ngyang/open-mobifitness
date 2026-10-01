package org.openmobifitness.app

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.openmobifitness.core.MetricId

enum class DisplayScope(val key: String,val limit: Int,val defaults: List<MetricId>) {
    TRAINING("training_metrics",12,listOf(MetricId.DISTANCE,MetricId.CALORIES,MetricId.CADENCE,MetricId.HEART,MetricId.POWER,MetricId.SPEED)),
    COMPACT("compact_metrics",2,listOf(MetricId.TIME,MetricId.RESISTANCE)),
    EXPANDED("expanded_metrics",4,listOf(MetricId.DISTANCE,MetricId.CALORIES,MetricId.CADENCE,MetricId.HEART))
}
class DisplayPreferences(private val prefs: SharedPreferences) {
    val selections=MutableStateFlow(DisplayScope.entries.associateWith { scope ->
        prefs.getString(scope.key,null)?.split(',')?.mapNotNull { runCatching { MetricId.valueOf(it) }.getOrNull() }?.distinct()?.take(scope.limit)?.takeIf { it.isNotEmpty() } ?: scope.defaults
    })
    val weight=MutableStateFlow(prefs.getFloat("weight_kg",70f).toDouble().coerceIn(20.0,300.0))
    val met=MutableStateFlow(prefs.getFloat("estimate_met",5f).toDouble().coerceIn(1.0,20.0))
    val targetCadence=MutableStateFlow(prefs.getInt("target_cadence",24).coerceIn(1,300))
    val packets=MutableStateFlow(prefs.getBoolean("packet_logs",false))
    val floatingEnabled=MutableStateFlow(prefs.getBoolean("floating_enabled",true))
    val autoFloating=MutableStateFlow(prefs.getBoolean("auto_floating",true))
    fun floating(enabled: Boolean) { floatingEnabled.value=enabled; prefs.edit().putBoolean("floating_enabled",enabled).apply() }
    fun automaticFloating(enabled: Boolean) { autoFloating.value=enabled; prefs.edit().putBoolean("auto_floating",enabled).apply() }
    fun save(scope: DisplayScope,items: List<MetricId>) {
        require(items.isNotEmpty() && items.distinct().size==items.size && items.size<=scope.limit)
        prefs.edit().putString(scope.key,items.joinToString(",")).apply()
        selections.value=selections.value+(scope to items)
    }
    fun estimates(kg: Double,effort: Double,target: Int) {
        require(kg in 20.0..300.0 && effort in 1.0..20.0 && target in 1..300)
        weight.value=kg; met.value=effort; targetCadence.value=target
        prefs.edit().putFloat("weight_kg",kg.toFloat()).putFloat("estimate_met",effort.toFloat()).putInt("target_cadence",target).apply()
    }
    fun packetLogs(enabled: Boolean) { packets.value=enabled; prefs.edit().putBoolean("packet_logs",enabled).apply() }
}
