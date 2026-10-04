@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.util.UUID

internal fun stageKindLabel(kind: StageKind)=when(kind) { StageKind.WARMUP -> R.string.warmup; StageKind.TRAINING -> R.string.stage_training; StageKind.RECOVERY -> R.string.recovery; StageKind.COOLDOWN -> R.string.cooldown }
internal fun hintModeLabel(mode: HintMode)=when(mode) { HintMode.FOLLOW_PLAN -> R.string.stage_follow; HintMode.CUSTOM -> R.string.stage_custom; HintMode.OFF -> R.string.stage_off }
internal fun frequencyMetric(machine: Machine)=when(machine) { Machine.ROWER -> MetricId.STROKE_RATE; Machine.TREADMILL -> MetricId.STEP_RATE; else -> MetricId.CADENCE }
internal fun frequencyUnit(machine: Machine)=if(machine in setOf(Machine.ROWER,Machine.TREADMILL)) "spm" else "rpm"

@Composable internal fun PlanPage(title: String,onBack: ()->Unit,footer: @Composable ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onBack,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                val compact=maxHeight<420.dp || LocalDensity.current.fontScale>=1.6f
                val heading: @Composable ()->Unit = { Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onBack,modifier=Modifier.testTag("editor-back")) { Icon(Icons.Default.ArrowBack,stringResource(R.string.back)) }
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                } }
                if(compact) Column(Modifier.widthIn(max=1000.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { heading(); content(); footer() }
                else Column(Modifier.widthIn(max=1100.dp).fillMaxSize().align(Alignment.TopCenter).padding(horizontal=20.dp,vertical=12.dp)) {
                    heading()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp),content=content)
                    footer()
                }
            }
        }
    }
}
@Composable internal fun EditorSection(title: String,content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); content() }
    }
}
@Composable private fun InputPair(first: @Composable (Modifier)->Unit,second: @Composable (Modifier)->Unit) {
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) { if(maxWidth/scale>=240.dp) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { first(Modifier.weight(1f)); second(Modifier.weight(1f)) }
        else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth()) }
    }
}
@Composable private fun RangeNumbers(lower: String,upper: String,onLower: (String)->Unit,onUpper: (String)->Unit,unit: String) {
    InputPair({ modifier -> OutlinedTextField(lower,onLower,label={ Text(stringResource(R.string.hint_lower)) },suffix={ Text(unit) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier) },{ modifier -> OutlinedTextField(upper,onUpper,label={ Text(stringResource(R.string.hint_upper)) },suffix={ Text(unit) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier) })
}
private fun rangeNumber(text: String)=text.trim().takeIf { it.isNotEmpty() }?.replace(',','.')?.toDouble()
private fun rangeValue(enabled: Boolean,lower: String,upper: String,heart: Boolean=false): PersonalRange {
    val result=PersonalRange(enabled,rangeNumber(lower),rangeNumber(upper))
    if(heart) require(listOfNotNull(result.lower,result.upper).all { it>0 && it%1.0==0.0 })
    return result
}
private fun fieldNumber(n: Double?)=n?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()

@Composable internal fun WorkoutEditor(c: Controller,workout: Workout,onDismiss: ()->Unit,onSaved: ()->Unit) {
    val context=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val ownerId by rememberSaveable(workout.id) { mutableStateOf(c.currentUser.value?.id) }
    val prepared=remember(workout.id) { workout.copy(id=if(workout.builtin) UUID.randomUUID().toString() else workout.id,ownerUserId=ownerId,builtin=false,title=context.workoutName(workout),clonedFrom=if(workout.builtin) workout.id else workout.clonedFrom,steps=workout.steps.map { if(it.id.isBlank()) it.copy(id=UUID.randomUUID().toString()) else it }) }
    var encoded by rememberSaveable(workout.id) { mutableStateOf(Exchange.workouts(listOf(prepared))) }
    val draft=remember(encoded) { Exchange.parse(encoded).workouts.single() }
    val machine=draft.machine ?: Machine.ELLIPTICAL
    var title by rememberSaveable(workout.id) { mutableStateOf(prepared.title) }
    var fOn by rememberSaveable(workout.id) { mutableStateOf(draft.hints.frequency.enabled) }; var fLower by rememberSaveable(workout.id) { mutableStateOf(fieldNumber(draft.hints.frequency.lower)) }; var fUpper by rememberSaveable(workout.id) { mutableStateOf(fieldNumber(draft.hints.frequency.upper)) }
    var hOn by rememberSaveable(workout.id) { mutableStateOf(draft.hints.heart.enabled) }; var hLower by rememberSaveable(workout.id) { mutableStateOf(fieldNumber(draft.hints.heart.lower)) }; var hUpper by rememberSaveable(workout.id) { mutableStateOf(fieldNumber(draft.hints.heart.upper)) }
    var sound by rememberSaveable(workout.id) { mutableStateOf(draft.hints.sound) }; var vibration by rememberSaveable(workout.id) { mutableStateOf(draft.hints.vibration) }
    var editing by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }; var adding by rememberSaveable(workout.id) { mutableStateOf(false) }
    var newStageId by rememberSaveable(workout.id) { mutableStateOf("") }
    var addingKind by rememberSaveable(workout.id) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }; var busy by remember { mutableStateOf(false) }; var discard by remember { mutableStateOf(false) }
    var importHints by remember { mutableStateOf(false) }
    val user by c.currentUser.collectAsStateWithLifecycle()
    val legacy=remember(ownerId,user?.legacyHints,machine) { archivedHints(user?.takeIf { it.id==ownerId }?.legacyHints.orEmpty(),machine) }
    val legacyCadence=remember(ownerId,user?.legacyHints) { runCatching { JSONObject(user?.legacyHints.orEmpty()).optInt("target_cadence").takeIf { it in 1..300 } }.getOrNull() }
    fun hints()=WorkoutHints(rangeValue(fOn,fLower,fUpper),rangeValue(hOn,hLower,hUpper,true),sound,vibration)
    fun close() { if(!busy) discard=true }
    fun updateSteps(steps: List<Step>) { encoded=Exchange.workouts(listOf(draft.copy(steps=steps))) }
    BackHandler { close() }
    val save: @Composable ()->Unit = {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            error?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error) }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=::close,enabled=!busy) { Text(stringResource(R.string.cancel)) }
                Button(enabled=!busy && ownerId==user?.id,onClick={
                    error=null
                    val result=runCatching { draft.copy(title=title.trim(),hints=hints(),machine=machine,ownerUserId=ownerId,legacyUnclassified=false).also(WorkoutPolicy::validate) }.getOrElse { error=R.string.invalid_input; return@Button }
                    busy=true
                    c.scope.launch {
                        runCatching { c.idleOperation { check(c.currentUser.value?.id==ownerId) { "stale_user" }; c.repo.saveWorkout(result) } }
                            .onSuccess { onSaved() }.onFailure { error=if(it.message=="stale_user" || it.message=="workout_owner_conflict") R.string.plan_stale_user else R.string.storage_failed }
                        busy=false
                    }
                },modifier=Modifier.testTag("save-plan")) { Text(stringResource(R.string.save)) }
            }
        }
    }
    PlanPage(stringResource(R.string.plan_edit),::close,save) {
        if(ownerId!=user?.id) Text(stringResource(R.string.plan_stale_user),color=MaterialTheme.colorScheme.error)
        OutlinedTextField(title,{ title=it },label={ Text(stringResource(R.string.title)) },singleLine=true,keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={ focus.clearFocus() }),modifier=Modifier.fillMaxWidth().testTag("plan-title"))
        Text(machineName(machine)+" · "+(user?.takeIf { it.id==ownerId }?.name.orEmpty()),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        val settings: @Composable ()->Unit = {
            EditorSection(stringResource(R.string.plan_defaults)) {
                Text(stringResource(R.string.plan_hint_modes_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(stringResource(metricLabel(frequencyMetric(machine)))+" · "+frequencyUnit(machine),fOn,{ fOn=it })
                if(fOn) RangeNumbers(fLower,fUpper,{ fLower=it },{ fUpper=it },frequencyUnit(machine))
                HorizontalDivider()
                ToggleRow(stringResource(R.string.heart_rate)+" · bpm",hOn,{ hOn=it })
                if(hOn) RangeNumbers(hLower,hUpper,{ hLower=it },{ hUpper=it },"bpm")
                Text(stringResource(R.string.hint_bounds_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(legacy!=null) TextButton(onClick={ importHints=true }) { Text(stringResource(R.string.plan_import_legacy_hints)) }
            }
            EditorSection(stringResource(R.string.plan_alert_methods)) { ToggleRow(stringResource(R.string.hint_sound),sound,{ sound=it }); ToggleRow(stringResource(R.string.hint_vibration),vibration,{ vibration=it }) }
        }
        val stages: @Composable ()->Unit = {
            EditorSection(stringResource(R.string.stage_overview)+" · "+draft.steps.size) {
                draft.steps.forEachIndexed { index,step ->
                    if(index>0) HorizontalDivider()
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { editing=step.id },verticalAlignment=Alignment.CenterVertically) {
                            Text("${index+1}. "+stringResource(stageKindLabel(step.kind)),Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            IconButton(onClick={ editing=step.id }) { Icon(Icons.Default.Edit,stringResource(R.string.stage_edit)) }
                        }
                        Text(stepDescription(step),style=MaterialTheme.typography.bodyMedium)
                        Text(stringResource(metricLabel(frequencyMetric(machine)))+" · "+stringResource(hintModeLabel(step.frequency.mode))+" / "+stringResource(R.string.heart_rate)+" · "+stringResource(hintModeLabel(step.heart.mode)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            IconButton(enabled=index>0,onClick={ val list=draft.steps.toMutableList(); java.util.Collections.swap(list,index,index-1); updateSteps(list) }) { Icon(Icons.Default.KeyboardArrowUp,stringResource(R.string.stage_move_up)) }
                            IconButton(enabled=index<draft.steps.lastIndex,onClick={ val list=draft.steps.toMutableList(); java.util.Collections.swap(list,index,index+1); updateSteps(list) }) { Icon(Icons.Default.KeyboardArrowDown,stringResource(R.string.stage_move_down)) }
                            IconButton(enabled=draft.steps.size>1,onClick={ updateSteps(draft.steps.filterNot { it.id==step.id }) }) { Icon(Icons.Default.Delete,stringResource(R.string.remove_step)) }
                        }
                    }
                }
                OutlinedButton(enabled=draft.steps.size<200,onClick={ adding=true },modifier=Modifier.fillMaxWidth().testTag("add-stage")) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.add_step)) }
            }
        }
        val scale=LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth/scale>=840.dp) Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) { Column(Modifier.weight(.9f),verticalArrangement=Arrangement.spacedBy(16.dp)) { settings() }; Column(Modifier.weight(1.1f)) { stages() } }
            else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) { settings(); stages() }
        }
        Text(stringResource(R.string.plan_control_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(adding) AlertDialog(onDismissRequest={ adding=false },title={ Text(stringResource(R.string.stage_kind)) },text={ Column { StageKind.entries.forEach { kind -> TextButton(onClick={ newStageId=UUID.randomUUID().toString(); addingKind=kind.name; adding=false },modifier=Modifier.fillMaxWidth()) { Text(stringResource(stageKindLabel(kind))) } } } },confirmButton={ TextButton(onClick={ adding=false }) { Text(stringResource(R.string.cancel)) } })
    val editingStep=draft.steps.firstOrNull { it.id==editing }
    val newStep=remember(addingKind,newStageId) { addingKind?.let { name -> val kind=StageKind.valueOf(name); val range=if(kind in setOf(StageKind.RECOVERY,StageKind.COOLDOWN)) StageRange(HintMode.OFF) else StageRange(); Step(target=60.0,id=newStageId,kind=kind,frequency=range,heart=range) } }
    (editingStep ?: newStep)?.let { step -> StageEditor(c,machine,step,editingStep==null,runCatching { hints() }.getOrNull(),legacyCadence,onClose={ editing=null; addingKind=null },onSave={ saved -> updateSteps(if(editingStep==null) draft.steps+saved else draft.steps.map { if(it.id==saved.id) saved else it }); editing=null; addingKind=null }) }
    if(discard) DiscardDialog(onKeep={ discard=false },onDiscard=onDismiss)
    if(importHints && legacy!=null) AlertDialog(onDismissRequest={ importHints=false },title={ Text(stringResource(R.string.plan_import_legacy_hints)) },text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.plan_import_legacy_help))
        Text(stringResource(metricLabel(frequencyMetric(machine)))+" · "+context.rangeText(legacy.frequency,frequencyUnit(machine)))
        Text(stringResource(R.string.heart_rate)+" · "+context.rangeText(legacy.heart,"bpm"))
    } },confirmButton={ TextButton(onClick={ fOn=legacy.frequency.enabled; fLower=fieldNumber(legacy.frequency.lower); fUpper=fieldNumber(legacy.frequency.upper); hOn=legacy.heart.enabled; hLower=fieldNumber(legacy.heart.lower); hUpper=fieldNumber(legacy.heart.upper); sound=legacy.sound; vibration=legacy.vibration; importHints=false }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ importHints=false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun stepDescription(step: Step): String {
    val context=LocalContext.current
    return when(step.condition) { Condition.TIME -> context.minutesSeconds((step.target*1000).toLong()); Condition.DISTANCE -> "${fieldNumber(step.target)} m"; Condition.STROKES -> "${fieldNumber(step.target)} ${stringResource(R.string.strokes)}" } + (step.resistancePercent?.let { " · $it%" } ?: "")
}
@Composable internal fun DiscardDialog(onKeep: ()->Unit,onDiscard: ()->Unit) { AlertDialog(onDismissRequest=onKeep,title={ Text(stringResource(R.string.plan_unsaved)) },confirmButton={ TextButton(onClick=onDiscard) { Text(stringResource(R.string.plan_discard)) } },dismissButton={ TextButton(onClick=onKeep) { Text(stringResource(R.string.plan_keep_editing)) } }) }
internal fun archivedHints(text: String,machine: Machine): WorkoutHints? = runCatching {
    if(text.isBlank()) return null
    val archive=JSONObject(text); val old=archive.optString("personal_hints").takeIf { it.startsWith('{') }?.let(::JSONObject) ?: return null
    fun range(value: JSONObject?)=if(value==null) PersonalRange() else PersonalRange(value.optBoolean("enabled"),if(value.has("lower")) value.getDouble("lower") else null,if(value.has("upper")) value.getDouble("upper") else null)
    WorkoutHints(range(old.optJSONObject("frequency")?.optJSONObject(machine.name)),range(old.optJSONObject("heart")),old.optBoolean("sound"),old.optBoolean("vibration"))
}.getOrNull()

@Composable private fun StageEditor(c: Controller,machine: Machine,step: Step,isNew: Boolean,plan: WorkoutHints?,legacyCadence: Int?,onClose: ()->Unit,onSave: (Step)->Unit) {
    val context=LocalContext.current; val link by c.ble.state.collectAsStateWithLifecycle()
    val imperial=rememberSaveable(step.id) { c.imperial.value }
    var kind by rememberSaveable(step.id) { mutableStateOf(step.kind.name) }; var condition by rememberSaveable(step.id) { mutableStateOf(step.condition.name) }
    var target by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.target)) }
    var resistanceOn by rememberSaveable(step.id) { mutableStateOf(step.resistancePercent!=null) }; var resistance by rememberSaveable(step.id) { mutableStateOf(step.resistancePercent?.toString().orEmpty()) }
    var speedOn by rememberSaveable(step.id) { mutableStateOf(step.speedTargetMps!=null) }; var speed by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.speedTargetMps?.times(if(imperial) 2.236936292 else 3.6))) }
    var inclineOn by rememberSaveable(step.id) { mutableStateOf(step.inclineTargetPercent!=null) }; var incline by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.inclineTargetPercent)) }
    var fMode by rememberSaveable(step.id) { mutableStateOf(step.frequency.mode.name) }; var fLower by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.frequency.lower)) }; var fUpper by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.frequency.upper)) }
    var hMode by rememberSaveable(step.id) { mutableStateOf(step.heart.mode.name) }; var hLower by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.heart.lower)) }; var hUpper by rememberSaveable(step.id) { mutableStateOf(fieldNumber(step.heart.upper)) }
    var rangeEdited by rememberSaveable(step.id) { mutableStateOf(false) }; var invalid by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }; var importCadence by remember { mutableStateOf(false) }
    val resistanceAllowed=machine in setOf(Machine.ELLIPTICAL,Machine.BIKE) || machine==Machine.ROWER && (step.resistancePercent!=null || link.machine==machine && link.writable && link.range!=null)
    val inclineAllowed=machine==Machine.TREADMILL && (step.inclineTargetPercent!=null || link.machine==Machine.TREADMILL && link.protocol==Protocol.FTMS)
    val range=c.range().takeIf { c.displayMachine()==machine }
    fun range(mode: String,lower: String,upper: String,heart: Boolean)=rangeValue(mode==HintMode.CUSTOM.name,lower,upper,heart).let { StageRange(HintMode.valueOf(mode),it.lower,it.upper) }
    PlanPage(stringResource(R.string.stage_edit),{ discard=true },footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(invalid) Text(stringResource(R.string.invalid_input),color=MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={ discard=true }) { Text(stringResource(R.string.cancel)) }
                Button(onClick={ runCatching {
                    step.copy(kind=StageKind.valueOf(kind),condition=Condition.valueOf(condition),target=target.replace(',','.').toDouble(),
                        resistancePercent=if(resistanceOn && resistanceAllowed) resistance.toInt() else null,
                        frequency=range(fMode,fLower,fUpper,false),heart=range(hMode,hLower,hUpper,true),
                        speedTargetMps=if(speedOn && machine==Machine.TREADMILL) speed.replace(',','.').toDouble()/(if(imperial) 2.236936292 else 3.6) else null,
                        inclineTargetPercent=if(inclineOn && inclineAllowed) incline.replace(',','.').toDouble() else null)
                }.onSuccess(onSave).onFailure { invalid=true } },modifier=Modifier.testTag("save-stage")) { Text(stringResource(R.string.save)) }
            }
        }
    }) {
        EditorSection(stringResource(R.string.stage_kind)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { StageKind.entries.forEach { value -> FilterChip(selected=kind==value.name,onClick={
                kind=value.name
                if(isNew && !rangeEdited) { val mode=if(value in setOf(StageKind.RECOVERY,StageKind.COOLDOWN)) HintMode.OFF else HintMode.FOLLOW_PLAN; fMode=mode.name; hMode=mode.name }
            },label={ Text(stringResource(stageKindLabel(value))) }) } }
            Text(stringResource(R.string.stage_type_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EditorSection(stringResource(R.string.stage_end)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { WorkoutPolicy.conditions(machine).forEach { value -> FilterChip(selected=condition==value.name,onClick={ condition=value.name },label={ Text(stringResource(when(value) { Condition.TIME -> R.string.seconds; Condition.DISTANCE -> R.string.meters; Condition.STROKES -> R.string.strokes })) }) } }
            OutlinedTextField(target,{ target=it },label={ Text(stringResource(R.string.target)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("stage-target"))
        }
        if(resistanceAllowed) EditorSection(stringResource(R.string.resistance_percent)) {
            ToggleRow(stringResource(R.string.target),resistanceOn,{ resistanceOn=it })
            if(resistanceOn) {
                OutlinedTextField(resistance,{ resistance=it },label={ Text(stringResource(R.string.resistance_percent)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                resistance.toIntOrNull()?.takeIf { it in 0..100 }?.let { p -> range?.let { r -> Text(stringResource(R.string.mapped_target,fieldNumber(r.percent(p)),r.percentage(r.percent(p))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) } }
            } else Text(stringResource(R.string.stage_unset),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(machine==Machine.TREADMILL) EditorSection(stringResource(R.string.plan_control_manual)) {
            ToggleRow(stringResource(R.string.stage_speed_target),speedOn,{ speedOn=it })
            if(speedOn) OutlinedTextField(speed,{ speed=it },label={ Text(stringResource(R.string.stage_speed_target)) },suffix={ Text(if(imperial) "mph" else "km/h") },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("stage-speed"))
            if(inclineAllowed) { ToggleRow(stringResource(R.string.stage_incline_target),inclineOn,{ inclineOn=it }); if(inclineOn) OutlinedTextField(incline,{ incline=it },label={ Text(stringResource(R.string.stage_incline_target)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth()) }
            Text(stringResource(R.string.plan_control_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EditorSection(stringResource(metricLabel(frequencyMetric(machine)))+" · "+frequencyUnit(machine)) {
            HintModePicker(fMode,{ fMode=it; rangeEdited=true })
            when(HintMode.valueOf(fMode)) {
                HintMode.CUSTOM -> RangeNumbers(fLower,fUpper,{ fLower=it; rangeEdited=true },{ fUpper=it; rangeEdited=true },frequencyUnit(machine))
                HintMode.FOLLOW_PLAN -> Text(if(plan==null) stringResource(R.string.invalid_hint_bounds) else context.rangeText(plan.frequency,frequencyUnit(machine)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HintMode.OFF -> Text(stringResource(R.string.report_ranges_off),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(machine==Machine.ROWER && legacyCadence!=null) TextButton(onClick={ importCadence=true }) { Text(stringResource(R.string.stage_import_cadence)) }
        }
        EditorSection(stringResource(R.string.heart_rate)+" · bpm") {
            HintModePicker(hMode,{ hMode=it; rangeEdited=true })
            when(HintMode.valueOf(hMode)) {
                HintMode.CUSTOM -> RangeNumbers(hLower,hUpper,{ hLower=it; rangeEdited=true },{ hUpper=it; rangeEdited=true },"bpm")
                HintMode.FOLLOW_PLAN -> Text(if(plan==null) stringResource(R.string.invalid_hint_bounds) else context.rangeText(plan.heart,"bpm"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HintMode.OFF -> Text(stringResource(R.string.report_ranges_off),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.hint_bounds_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(discard) DiscardDialog({ discard=false },onClose)
    if(importCadence) AlertDialog(onDismissRequest={ importCadence=false },title={ Text(stringResource(R.string.stage_import_cadence)) },text={ Text("$legacyCadence spm") },confirmButton={ TextButton(onClick={ fMode=HintMode.CUSTOM.name; fLower=legacyCadence.toString(); fUpper=legacyCadence.toString(); rangeEdited=true; importCadence=false }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ importCadence=false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun HintModePicker(selected: String,onSelect: (String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { HintMode.entries.forEach { mode -> FilterChip(selected=selected==mode.name,onClick={ onSelect(mode.name) },label={ Text(stringResource(hintModeLabel(mode))) }) } }
}
