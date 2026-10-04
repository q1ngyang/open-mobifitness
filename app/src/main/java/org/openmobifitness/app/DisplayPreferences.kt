package org.openmobifitness.app

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.openmobifitness.core.*

enum class DisplayScope(val key: String,val limit: Int,val defaults: List<MetricId>) {
    TRAINING("training_metrics",12,listOf(MetricId.DISTANCE,MetricId.CALORIES,MetricId.CADENCE,MetricId.HEART,MetricId.POWER,MetricId.SPEED)),
    COMPACT("compact_metrics",2,listOf(MetricId.TIME,MetricId.RESISTANCE)),
    EXPANDED("expanded_metrics",6,listOf(MetricId.DISTANCE,MetricId.CALORIES,MetricId.CADENCE,MetricId.HEART,MetricId.POWER,MetricId.SPEED))
}
class DisplayPreferences(private val prefs: SharedPreferences) {
    val selections=MutableStateFlow(DisplayScope.entries.associateWith { scope ->
        prefs.getString(scope.key,null)?.split(',')?.mapNotNull { runCatching { MetricId.valueOf(it) }.getOrNull() }?.distinct()?.take(scope.limit)?.takeIf { it.isNotEmpty() } ?: scope.defaults
    })
    val byMachine=MutableStateFlow(Machine.entries.associateWith { machine ->
        DisplayScope.entries.mapNotNull { scope ->
            prefs.getString("${scope.key}.${machine.name}",null)?.let { raw ->
                scope to raw.split(',').mapNotNull { runCatching { MetricId.valueOf(it) }.getOrNull() }
            }
        }.toMap()
    })
    fun selected(scope: DisplayScope,machine: Machine,controlOnly: Boolean=false,freeRecording: Boolean=false): List<MetricId> {
        val stored=byMachine.value[machine]?.get(scope)
        // Existing global customizations migrate by filtering, without rewriting other machines.
        val legacy=prefs.getString(scope.key,null)?.split(',')?.mapNotNull { runCatching { MetricId.valueOf(it) }.getOrNull() }
        val instant=MetricCatalog.trainingDefaults(machine).filterNot { it in setOf(MetricId.TIME,MetricId.DISTANCE,MetricId.CALORIES,MetricId.STROKES,MetricId.JUMPS,MetricId.REPETITIONS,MetricId.JUMP_INTERRUPTS,MetricId.AVERAGE_SPEED) }.let {
            val frequency=when(machine) { Machine.ROWER -> MetricId.STROKE_RATE; Machine.TREADMILL -> MetricId.STEP_RATE; Machine.JUMP_ROPE -> MetricId.JUMP_RATE; else -> MetricId.CADENCE }
            MetricCatalog.filter(machine,listOf(frequency,MetricId.HEART)+it).distinct()
        }
        val defaults=if(controlOnly) instant.take(scope.limit) else when(scope) {
            DisplayScope.TRAINING -> if(freeRecording && machine in setOf(Machine.ELLIPTICAL,Machine.BIKE)) listOf(MetricId.CADENCE,MetricId.HEART,MetricId.DISTANCE,MetricId.CALORIES,MetricId.POWER,MetricId.SPEED) else MetricCatalog.trainingDefaults(machine)
            DisplayScope.EXPANDED -> MetricCatalog.trainingDefaults(machine).take(6)
            DisplayScope.COMPACT -> listOf(MetricId.TIME,when(machine) {
                Machine.TREADMILL -> MetricId.SPEED
                Machine.ROWER -> MetricId.STROKE_RATE
                Machine.JUMP_ROPE -> MetricId.JUMPS
                Machine.DUMBBELL -> MetricId.REPETITIONS
                Machine.HEART,Machine.UNKNOWN -> MetricId.HEART
                else -> MetricId.RESISTANCE
            })
        }
        return MetricCatalog.filter(machine,stored ?: legacy ?: defaults).take(scope.limit).ifEmpty { defaults.take(scope.limit) }
    }
    val weight=MutableStateFlow(prefs.getFloat("weight_kg",70f).toDouble().coerceIn(20.0,300.0))
    val met=MutableStateFlow(prefs.getFloat("estimate_met",5f).toDouble().coerceIn(1.0,20.0))
    val targetCadence=MutableStateFlow(prefs.getInt("target_cadence",24).coerceIn(1,300))
    val packets=MutableStateFlow(prefs.getBoolean("packet_logs",true))
    val floatingEnabled=MutableStateFlow(prefs.getBoolean("floating_enabled",true))
    val autoFloating=MutableStateFlow(prefs.getBoolean("auto_floating",true))
    fun floating(enabled: Boolean) { floatingEnabled.value=enabled; prefs.edit().putBoolean("floating_enabled",enabled).apply() }
    fun automaticFloating(enabled: Boolean) { autoFloating.value=enabled; prefs.edit().putBoolean("auto_floating",enabled).apply() }
    fun save(scope: DisplayScope,items: List<MetricId>) {
        require(items.isNotEmpty() && items.distinct().size==items.size && items.size<=scope.limit)
        prefs.edit().putString(scope.key,items.joinToString(",")).apply()
        selections.value=selections.value+(scope to items)
    }
    fun save(scope: DisplayScope,items: List<MetricId>,machine: Machine) {
        require(items.isNotEmpty() && items.size<=scope.limit && MetricCatalog.filter(machine,items)==items)
        prefs.edit().putString("${scope.key}.${machine.name}",items.joinToString(",")).apply()
        byMachine.value=byMachine.value+(machine to (byMachine.value.getValue(machine)+(scope to items)))
    }
    fun estimates(kg: Double,effort: Double,target: Int) {
        require(kg in 20.0..300.0 && effort in 1.0..20.0 && target in 1..300)
        weight.value=kg; met.value=effort; targetCadence.value=target
        prefs.edit().putFloat("weight_kg",kg.toFloat()).putFloat("estimate_met",effort.toFloat()).putInt("target_cadence",target).apply()
    }
    fun packetLogs(enabled: Boolean) { packets.value=enabled; prefs.edit().putBoolean("packet_logs",enabled).apply() }
    fun reload() {
        val loaded=DisplayPreferences(prefs)
        selections.value=loaded.selections.value; byMachine.value=loaded.byMachine.value
        weight.value=loaded.weight.value; met.value=loaded.met.value; targetCadence.value=loaded.targetCadence.value
        floatingEnabled.value=loaded.floatingEnabled.value; autoFloating.value=loaded.autoFloating.value
    }
}
