@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.shortDuration
import org.openmobifitness.app.data.rangeText
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

@Composable internal fun ActiveWorkoutBanner(state: ExerciseState,onClick: ()->Unit) {
    val s=state.session ?: return
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).testTag("return-workout"),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.4f))) {
        BoxWithConstraints(Modifier.padding(horizontal=14.dp,vertical=10.dp)) {
            val actionWidth=maxWidth*.5f
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(if(state.paused) Icons.Default.PlayArrow else Icons.Default.Favorite,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                    BasicText(shortDuration(s.elapsedMs),modifier=Modifier.weight(1.3f),style=MaterialTheme.typography.titleLarge.copy(fontSize=26.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer),maxLines=1,autoSize=TextAutoSize.StepBased(16.sp,26.sp,1.sp))
                    Text(stringResource(if(state.paused) R.string.paused else R.string.active),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
                    Icon(Icons.Default.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(machineName(s.machine)+(state.selected?.let { " · "+stringResource(R.string.stage_format,(state.stage+1).coerceAtMost(it.steps.size),it.steps.size) } ?: ""),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(stringResource(R.string.return_workout),modifier=Modifier.widthIn(max=actionWidth),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable internal fun WorkoutLibrary(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val custom by c.repo.workouts.collectAsStateWithLifecycle()
    val revision by c.repo.revision.collectAsStateWithLifecycle()
    val user by c.currentUser.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val resources=androidx.compose.ui.platform.LocalResources.current
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val ownerId=user?.id
    var search by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(PlanDuration.ALL.name) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var equipment by rememberSaveable { mutableStateOf((if(state.demo) state.demoMachine else link.machine).takeIf { it in WorkoutPolicy.supported }?.name ?: Machine.ELLIPTICAL.name) }
    val machine=Machine.valueOf(equipment)
    var machineMenu by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    var favorites by remember(ownerId) { mutableStateOf(WorkoutPolicy.favorites(c.prefs.getStringSet("favorite_workouts",emptySet()).orEmpty())) }
    LaunchedEffect(ownerId,revision) { if(c.prefs.userId==ownerId) favorites=WorkoutPolicy.favorites(c.prefs.getStringSet("favorite_workouts",emptySet()).orEmpty()) }
    var detailId by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    var editorCsv by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Workout?>(null) }
    var legacyList by rememberSaveable(ownerId) { mutableStateOf(false) }
    var classifyId by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    val personal=custom.filter { it.ownerUserId==ownerId && ownerId!=null }
    val legacy=personal.filter { it.legacyUnclassified && personal.none { copy -> copy.clonedFrom==it.id && !copy.legacyUnclassified } }
    val all=WorkoutPolicy.templates(machine)+personal.filter { it.machine==machine && !it.legacyUnclassified }
    val filtered=all.filter { (search.isBlank() || context.workoutName(it).contains(search.trim(),true)) && PlanFilter.matches(it,PlanDuration.valueOf(duration),PlanIntensity.ALL) && when(source) { 1 -> !it.builtin; 2 -> it.id in favorites; 3 -> it.builtin; else -> true } }
    fun favorite(w: Workout) {
        if(ownerId==null) { c.userPicker.value=true; return }
        if(c.currentUser.value?.id!=ownerId || c.repo.profilePreferences.userId!=ownerId) return
        val stored=WorkoutPolicy.favorites(c.prefs.getStringSet("favorite_workouts",emptySet()).orEmpty())
        favorites=if(w.id in stored) stored-w.id else stored+w.id
        c.prefs.edit().putStringSet("favorite_workouts",favorites).apply()
    }
    fun edit(w: Workout,copy: Boolean=false) {
        if(ownerId==null) { c.userPicker.value=true; return }
        val draft=if(copy || w.builtin) w.copy(id=java.util.UUID.randomUUID().toString(),title=if(w.builtin) context.workoutName(w) else resources.getString(R.string.plan_saved_copy,w.title),builtin=false,ownerUserId=ownerId,clonedFrom=w.id) else w
        editorCsv=Exchange.workouts(listOf(draft)); detailId=null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact=maxHeight<440.dp
        val padding=if(compact) 12.dp else 16.dp
        val contentWidth=maxWidth-padding*2
        val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale
        val minimum=300.dp*scale.coerceAtLeast(1f)
        val columns=when { contentWidth>=940.dp && contentWidth>=minimum*3+32.dp -> 3; contentWidth>=616.dp && contentWidth>=minimum*2+16.dp -> 2; else -> 1 }
        LazyColumn(Modifier.fillMaxSize().testTag("plan-library"),contentPadding=PaddingValues(padding),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { FreeTrainingCard(activity,c,state,link,compact) }
            item {
                val heading: @Composable (Modifier)->Unit = { modifier -> Text(stringResource(R.string.library),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=modifier) }
                val create: @Composable ()->Unit = { if(machine in WorkoutPolicy.supported) FilledTonalButton(enabled=!c.identityLocked,onClick={ edit(Workout(title=resources.getString(R.string.new_workout),ownerUserId=ownerId,machine=machine,steps=listOf(Step(target=300.0,id=java.util.UUID.randomUUID().toString())))) },modifier=Modifier.testTag("new-plan")) { Icon(Icons.Default.Add,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_workout)) } }
                if(contentWidth/scale<340.dp) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { heading(Modifier.fillMaxWidth()); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { create() } }
                else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { heading(Modifier.weight(1f)); create() }
            }
            item { Column {
                val equipmentPicker: @Composable ()->Unit = { Box {
                    OutlinedButton(onClick={ machineMenu=true },modifier=Modifier.testTag("plan-equipment")) { Text(machineName(machine)); Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(18.dp)) }
                    DropdownMenu(machineMenu,onDismissRequest={ machineMenu=false }) { Machine.entries.filter { it !in setOf(Machine.UNKNOWN,Machine.HEART) }.forEach { m -> DropdownMenuItem(text={ Text(machineName(m)) },onClick={ equipment=m.name; detailId=null; machineMenu=false }) } }
                } }
                val tabs: @Composable (Modifier)->Unit = { modifier -> Row(modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.all_items,R.string.plan_my,R.string.favorites,R.string.builtin_plans).forEachIndexed { index,label -> FilterChip(source==index,onClick={ source=index },label={ Text(stringResource(label)) }) }
                } }
                val searchTools: @Composable (Modifier)->Unit = { modifier -> Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SearchBox(search,{ search=it },stringResource(R.string.search_plans),Modifier.weight(1f),"plan-search")
                    Box {
                        FilledTonalIconButton(onClick={ durationMenu=true },modifier=Modifier.size(48.dp)) { FilterGlyph(stringResource(R.string.plan_duration_filter)) }
                        DropdownMenu(durationMenu,onDismissRequest={ durationMenu=false }) { PlanDuration.entries.forEach { value -> DropdownMenuItem(text={ Text(stringResource(durationLabel(value))) },leadingIcon={ if(duration==value.name) Icon(Icons.Default.Check,null) },onClick={ duration=value.name; durationMenu=false }) } }
                    }
                } }
                if(contentWidth/scale>=940.dp) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    equipmentPicker(); searchTools(Modifier.weight(1f)); tabs(Modifier.widthIn(max=450.dp))
                } else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { equipmentPicker(); searchTools(Modifier.fillMaxWidth()); tabs(Modifier.fillMaxWidth()) }
                if(duration!=PlanDuration.ALL.name) TextButton(onClick={ duration=PlanDuration.ALL.name },modifier=Modifier.padding(top=8.dp)) { Text(stringResource(durationLabel(PlanDuration.valueOf(duration)))); Icon(Icons.Default.Close,null,Modifier.padding(start=6.dp).size(16.dp)) }
            } }
            if(machine !in WorkoutPolicy.supported) item { Text(stringResource(R.string.plan_free_only),Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            else if(filtered.isEmpty()) item { Text(stringResource(if(source==3 && WorkoutPolicy.templates(machine).isEmpty()) R.string.plan_no_templates else if(source==2) R.string.favorites_empty else R.string.no_matches),Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(filtered.chunked(columns),key={ group -> group.joinToString { it.id } }) { group -> Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                group.forEach { w -> WorkoutCard(w,w.id in favorites,user?.name.orEmpty(),Modifier.weight(1f),{ favorite(w) },{ detailId=w.id }) }
                repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
            } }
            if(legacy.isNotEmpty()) item { TextButton(onClick={ legacyList=true },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_legacy_count,legacy.size)); Icon(Icons.Default.KeyboardArrowRight,null) } }
        }
    }
    all.firstOrNull { it.id==detailId }?.let { w ->
        val connected=c.canStart() && WorkoutPolicy.compatible(w,if(state.demo) state.demoMachine else link.machine,ownerId)
        val blockedLabel=if(!c.canStart()) R.string.plan_device_disconnected else R.string.plan_device_mismatch
        PlanPage(context.workoutName(w),{ detailId=null },footer={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!connected) Text(stringResource(blockedLabel),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!c.identityLocked,onClick={ edit(w) }) { Text(stringResource(if(w.builtin) R.string.duplicate else R.string.edit)) }
                    Button(enabled=!c.identityLocked,onClick={ if(connected) { c.select(w); detailId=null; activity.startTraining() } else { detailId=null; activity.page.value=2 } },modifier=Modifier.testTag("start-plan")) { Text(stringResource(if(connected) R.string.start else blockedLabel)) }
                }
            }
        }) {
            Text(machineName(w.machine ?: machine)+" · "+if(w.builtin) stringResource(R.string.builtin_plans) else user?.name.orEmpty(),color=MaterialTheme.colorScheme.primary)
            MiniProfile(w,Modifier.fillMaxWidth().height(72.dp))
            Text(stringResource(if(w.machine==Machine.TREADMILL) R.string.plan_treadmill_help else if(w.machine==Machine.ROWER) R.string.plan_rower_help else R.string.plan_resistance_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(w.builtin && w.id.startsWith("interval10_")) Text(stringResource(R.string.plan_short_interval_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            EditorSection(stringResource(R.string.plan_defaults)) {
                Text(stringResource(metricLabel(frequencyMetric(machine)))+" · "+context.rangeText(w.hints.frequency,frequencyUnit(machine)))
                Text(stringResource(R.string.heart_rate)+" · "+context.rangeText(w.hints.heart,"bpm"))
            }
            EditorSection(stringResource(R.string.stage_overview)) { w.steps.forEachIndexed { index,step ->
                if(index>0) HorizontalDivider()
                Text("${index+1}. "+stringResource(stageKindLabel(step.kind)),fontWeight=FontWeight.SemiBold)
                Text(stepDescription(step))
                step.speedTargetMps?.let { Text(stringResource(R.string.stage_speed_target)+" · "+WorkoutService.number(it*if(imperial) 2.236936292 else 3.6)+if(imperial) " mph" else " km/h") }
                step.inclineTargetPercent?.let { Text(stringResource(R.string.stage_incline_target)+" · "+WorkoutService.number(it)) }
                Text(stringResource(metricLabel(frequencyMetric(machine)))+" · "+context.rangeText(step.frequency.resolve(w.hints.frequency),frequencyUnit(machine)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.heart_rate)+" · "+context.rangeText(step.heart.resolve(w.hints.heart),"bpm"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if(!w.builtin) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=!c.identityLocked,onClick={ edit(w,true) }) { Text(stringResource(R.string.duplicate)) }
                TextButton(enabled=!c.identityLocked,onClick={ deleting=w }) { Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) }
            }
        }
    }
    deleting?.let { w -> AlertDialog(onDismissRequest={ deleting=null },title={ Text(stringResource(R.string.delete)) },text={ Text(workoutTitle(w)) },confirmButton={ TextButton(onClick={ c.scope.launch { runCatching { c.idleOperation { c.repo.deleteWorkout(w.id,w.ownerUserId) } }.onSuccess { detailId=null }.onFailure { c.error(R.string.io_failed) } }; deleting=null }) { Text(stringResource(R.string.delete)) } },dismissButton={ TextButton(onClick={ deleting=null }) { Text(stringResource(R.string.cancel)) } }) }
    if(legacyList) AlertDialog(onDismissRequest={ legacyList=false },title={ Text(stringResource(R.string.plan_assign_machine)) },text={ Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.plan_legacy_help)); legacy.forEach { w -> TextButton(onClick={ classifyId=w.id; legacyList=false }) { Text(w.title) } } } },confirmButton={ TextButton(onClick={ legacyList=false }) { Text(stringResource(R.string.close)) } })
    legacy.firstOrNull { it.id==classifyId }?.let { old -> AlertDialog(onDismissRequest={ classifyId=null },title={ Text(old.title) },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.plan_legacy_help)); Text(stringResource(R.string.plan_classify_targets),style=MaterialTheme.typography.bodySmall)
        WorkoutPolicy.supported.forEach { m -> TextButton(onClick={ equipment=m.name; edit(old.copy(id=java.util.UUID.randomUUID().toString(),machine=m,legacyUnclassified=false,clonedFrom=old.id,steps=old.steps.map { it.copy(id=java.util.UUID.randomUUID().toString(),resistancePercent=if(m==Machine.TREADMILL) null else it.resistancePercent) })); classifyId=null }) { Text(machineName(m)) } }
    } },confirmButton={ TextButton(onClick={ classifyId=null }) { Text(stringResource(R.string.cancel)) } }) }
    editorCsv?.let { csv -> val draft=remember(csv) { Exchange.parse(csv).workouts.single() }; WorkoutEditor(c,draft,onDismiss={ editorCsv=null },onSaved={ editorCsv=null; source=1 }) }
}
@Composable private fun WorkoutCard(w: Workout,favorite: Boolean,ownerName: String,modifier: Modifier,onFavorite: ()->Unit,onOpen: ()->Unit) {
    Surface(onClick=onOpen,modifier=modifier.testTag("plan-${w.id}"),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(start=18.dp,end=14.dp,top=12.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(workoutTitle(w),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text((if(w.steps.all { it.condition==Condition.TIME }) stringResource(R.string.minutes_format,(w.steps.sumOf { it.target }/60).toInt())+" · " else "")+stringResource(R.string.stages_format,w.steps.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconToggleButton(checked=favorite,onCheckedChange={ onFavorite() },modifier=Modifier.testTag("favorite-${w.id}")) { FavoriteGlyph(favorite,stringResource(if(favorite) R.string.favorite_remove else R.string.favorite_add)) }
            }
            MiniProfile(w,Modifier.fillMaxWidth().height(36.dp))
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(w.builtin) stringResource(R.string.builtin_plans) else ownerName,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape=RoundedCornerShape(6.dp),color=MaterialTheme.colorScheme.primaryContainer) { Text(stringResource(if(w.steps.none { it.resistancePercent!=null }) R.string.plan_control_manual else R.string.plan_control_auto),Modifier.padding(horizontal=7.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}
@Composable internal fun SearchBox(value: String,onValue: (String)->Unit,hint: String,modifier: Modifier=Modifier,tag: String="search") {
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    Surface(modifier=modifier,shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        androidx.compose.foundation.text.BasicTextField(value,onValue,modifier=Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription=hint },singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={ focus.clearFocus() }),textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),decorationBox={ field ->
            Row(Modifier.heightIn(min=48.dp).padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Search,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(vertical=10.dp)) { if(value.isEmpty()) Text(hint,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant); field() }
                if(value.isNotEmpty()) IconButton(onClick={ onValue("") },modifier=Modifier.size(48.dp)) { Icon(Icons.Default.Close,stringResource(R.string.close),Modifier.size(20.dp)) } else Spacer(Modifier.width(4.dp))
            }
        })
    }
}
@Composable private fun MiniProfile(w: Workout,modifier: Modifier) {
    val color=MaterialTheme.colorScheme.primary
    val faint=MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        val weights=w.steps.map { if(w.steps.all { it.condition==Condition.TIME }) it.target.toFloat() else 1f }; val total=weights.sum().coerceAtLeast(1f)
        var x=0f
        w.steps.forEachIndexed { i,step ->
            val width=size.width*weights[i]/total
            val h=size.height*(.24f+(step.resistancePercent ?: (step.speedTargetMps?.times(24)?.toInt() ?: step.frequency.resolve(w.hints.frequency).upper?.times(2)?.toInt() ?: 20)).coerceIn(0,100)/100f*.76f)
            drawRoundRect(if(step.kind in setOf(StageKind.RECOVERY,StageKind.COOLDOWN)) faint else color.copy(alpha=.55f),Offset(x,size.height-h),Size((width-2.dp.toPx()).coerceAtLeast(.5f),h),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            x+=width
        }
    }
}
private fun durationLabel(v: PlanDuration)=when(v) { PlanDuration.ALL->R.string.all_items; PlanDuration.SHORT->R.string.short_plans; PlanDuration.MEDIUM->R.string.medium_plans; PlanDuration.LONG->R.string.long_plans }
private fun intensityLabel(v: PlanIntensity)=when(v) { PlanIntensity.ALL->R.string.all_items; PlanIntensity.LOW->R.string.low_target; PlanIntensity.MODERATE->R.string.medium_target; PlanIntensity.HIGH->R.string.high_target }

@Composable private fun FavoriteGlyph(filled: Boolean,label: String) {
    val color=if(filled) Color(0xFFB98200) else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp).semantics { contentDescription=label }) {
        val path=androidx.compose.ui.graphics.Path()
        repeat(10) { i -> val angle=-Math.PI/2+i*Math.PI/5; val r=size.minDimension*(if(i%2==0) .46f else .21f)
            val x=size.width/2+kotlin.math.cos(angle).toFloat()*r; val y=size.height/2+kotlin.math.sin(angle).toFloat()*r
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }; path.close()
        drawPath(path,color,style=if(filled) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
    }
}
