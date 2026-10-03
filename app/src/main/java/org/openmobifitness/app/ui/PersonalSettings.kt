@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

@Composable internal fun ResistancePresets(c: Controller,state: ExerciseState,link: LinkState,edit: ()->Unit) {
    val range=c.range() ?: return
    if(!state.demo && (!link.writable || link.address.isBlank())) return
    val revision by c.local.revision.collectAsStateWithLifecycle()
    val address=if(state.demo) "demo.${state.demoMachine}" else link.address
    val machine=c.displayMachine()
    val values=remember(revision,address,machine,range) { c.local.presets(address,machine,range) }
    Column(Modifier.fillMaxWidth().padding(top=6.dp)) {
        HorizontalDivider()
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.resistance_presets),Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.labelLarge)
            TextButton(onClick=edit,modifier=Modifier.testTag("edit-presets")) { Text(stringResource(R.string.edit)) }
        }
        if(values.isEmpty()) Text(stringResource(R.string.preset_empty),Modifier.padding(bottom=10.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth/LocalDensity.current.fontScale<260.dp) 2 else 4
            val cellWidth=(maxWidth-8.dp*(columns-1))/columns
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            values.forEach { value ->
                val selected=c.controlResistance()?.let { kotlin.math.abs(it-value)<range.increment/2 }==true
                OutlinedButton(onClick={ c.adjustTo(value) },enabled=c.canStart() && !link.busy && !state.starting,modifier=Modifier.heightIn(min=48.dp).width(cellWidth).testTag("preset-$value"),contentPadding=PaddingValues(horizontal=6.dp,vertical=8.dp),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) { Text(WorkoutService.number(value),maxLines=1,softWrap=false) }
            }
            }
        }
    }
}

/** Keep the editor outside adaptive dashboard branches: opening the keyboard can
 * turn a near-square window into a short layout without discarding the form. */
@Composable internal fun ResistancePresetEditor(c: Controller,state: ExerciseState,close: ()->Unit) {
        val link by c.ble.state.collectAsStateWithLifecycle()
        val range=c.range() ?: return
        val address=if(state.demo) "demo.${state.demoMachine}" else link.address
        val machine=c.displayMachine()
        val values=c.local.presets(address,machine,range)
        var input by rememberSaveable(address,machine.name) { mutableStateOf(values.joinToString(", ") { if(it%1.0==0.0) it.toInt().toString() else it.toString() }) }
        var invalid by remember { mutableStateOf(false) }
        FormDialog(stringResource(R.string.resistance_presets),close,{
            runCatching { val parsed=if(input.isBlank()) emptyList() else input.replace('，',',').split(',').map { it.trim().toDouble() }; c.local.savePresets(address,machine,range,parsed) }.onSuccess { close() }.onFailure { invalid=true }
        }) {
                Text(stringResource(R.string.preset_help),style=MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.range_format,WorkoutService.number(range.min),WorkoutService.number(range.max)))
                OutlinedTextField(input,{ input=it; invalid=false },label={ Text(stringResource(R.string.preset_values)) },isError=invalid,modifier=Modifier.fillMaxWidth().testTag("preset-input"),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text))
                if(invalid) Text(stringResource(R.string.invalid_presets),color=MaterialTheme.colorScheme.error)
        }
}

@Composable internal fun PersonalHintsSettings(c: Controller) {
    val config by c.local.hints.collectAsStateWithLifecycle()
    var machine by remember { mutableStateOf(c.displayMachine().takeIf { it in frequencyMachines } ?: Machine.ELLIPTICAL) }
    var edit by remember { mutableStateOf("") }
    Text(stringResource(R.string.personal_hints_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsGroup(stringResource(R.string.hint_frequency)) {
        FlowRow(Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            frequencyMachines.forEach { m -> FilterChip(machine==m,{ machine=m },label={ Text(machineName(m)) }) }
        }
        val range=config.frequency[machine] ?: PersonalRange()
        RangeSummary(range,when(machine) { Machine.TREADMILL,Machine.ROWER -> "spm"; Machine.JUMP_ROPE -> "/min"; else -> "rpm" }) { edit="frequency" }
    }
    SettingsGroup(stringResource(R.string.heart_rate)) { RangeSummary(config.heart,"bpm") { edit="heart" } }
    SettingsGroup("") {
        ToggleRow(stringResource(R.string.hint_sound),config.sound,{ c.local.saveHints(config.copy(sound=it)) })
        ToggleRow(stringResource(R.string.hint_vibration),config.vibration,{ c.local.saveHints(config.copy(vibration=it)) })
    }
    Text(stringResource(R.string.hint_bounds_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(edit.isNotEmpty()) RangeEditor(if(edit=="heart") config.heart else config.frequency[machine] ?: PersonalRange(),{ edit="" }) { range ->
        c.local.saveHints(if(edit=="heart") config.copy(heart=range) else config.copy(frequency=config.frequency+(machine to range))); edit=""
    }
}
private val frequencyMachines=listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL,Machine.JUMP_ROPE)
@Composable private fun RangeSummary(range: PersonalRange,unit: String,edit: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=edit).heightIn(min=64.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if(range.enabled) R.string.enabled else R.string.disabled),style=MaterialTheme.typography.titleSmall)
            Text("${WorkoutService.number(range.lower)} – ${WorkoutService.number(range.upper)} $unit",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick=edit) { Text(stringResource(R.string.edit)) }
    }
}
@Composable private fun RangeEditor(range: PersonalRange,close: ()->Unit,save: (PersonalRange)->Unit) {
    var lower by remember { mutableStateOf(range.lower?.toString().orEmpty()) }
    var upper by remember { mutableStateOf(range.upper?.toString().orEmpty()) }
    var enabled by remember { mutableStateOf(range.enabled) }
    var invalid by remember { mutableStateOf(false) }
    FormDialog(stringResource(R.string.personal_hints),close,{
        runCatching { PersonalRange(enabled,lower.takeIf { it.isNotBlank() }?.replace(',','.')?.toDouble(),upper.takeIf { it.isNotBlank() }?.replace(',','.')?.toDouble()) }.onSuccess(save).onFailure { invalid=true }
    }) {
            Text(stringResource(R.string.hint_bounds_help),style=MaterialTheme.typography.bodySmall)
            ToggleRow(stringResource(R.string.hint_enable),enabled,{ enabled=it })
            OutlinedTextField(lower,{ lower=it; invalid=false },label={ Text(stringResource(R.string.hint_lower)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("hint-lower"))
            OutlinedTextField(upper,{ upper=it; invalid=false },label={ Text(stringResource(R.string.hint_upper)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("hint-upper"))
            if(invalid) Text(stringResource(R.string.invalid_hint_bounds),color=MaterialTheme.colorScheme.error)
    }
}

/** Keep form actions above the IME; title/help/fields share the remaining scroll
 * area so large fonts cannot push Save and Cancel behind the keyboard. */
@Composable internal fun FormDialog(title: String,close: ()->Unit,save: ()->Unit,confirmLabel: String?=null,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),contentAlignment=Alignment.Center) {
            // Use the space above the keyboard, including in a tall split window.
            val shortWindow=maxHeight<480.dp
            Surface(Modifier.padding(vertical=if(shortWindow) 4.dp else 16.dp),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal=24.dp,vertical=if(shortWindow) 4.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(if(shortWindow) 4.dp else 8.dp)) {
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(title,style=MaterialTheme.typography.headlineSmall)
                        content()
                    }
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick=close) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick=save) { Text(confirmLabel ?: stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}
