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
    val context=LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(PlanDuration.ALL.name) }
    var intensity by rememberSaveable { mutableStateOf(PlanIntensity.ALL.name) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(c.prefs.getStringSet("favorite_workouts",emptySet())!!.toSet()) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<Workout?>(null) }
    var deleting by remember { mutableStateOf<Workout?>(null) }
    val all=Presets.all+custom
    val filtered=all.filter { (search.isBlank() || context.workoutName(it).contains(search.trim(),true)) && PlanFilter.matches(it,PlanDuration.valueOf(duration),PlanIntensity.valueOf(intensity)) && (source==0 || source==1 && it.builtin || source==2 && !it.builtin) }
    val filtering=search.isNotBlank() || duration!=PlanDuration.ALL.name || intensity!=PlanIntensity.ALL.name || source!=0
    val groups=listOf(R.string.favorites to filtered.filter { it.id in favorites },R.string.custom to filtered.filter { !it.builtin && it.id !in favorites },R.string.builtin_plans to filtered.filter { it.builtin && it.id !in favorites })
    val expanded=rememberSaveable { mutableStateOf(emptySet<Int>()) }
    val collapsed=rememberSaveable { mutableStateOf(emptySet<Int>()) }
    val newTitle=stringResource(R.string.new_workout)
    fun favorite(w: Workout) { favorites=if(w.id in favorites) favorites-w.id else favorites+w.id; c.prefs.edit().putStringSet("favorite_workouts",favorites).apply() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact=maxHeight<440.dp
        val columns=if(maxWidth>=780.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize().testTag("plan-library"),contentPadding=PaddingValues(if(compact) 12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 12.dp)) {
            item { Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.library),style=if(compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={ editor=Workout(title=newTitle,steps=listOf(Step(target=60.0))) }) { Icon(Icons.Default.Add,stringResource(R.string.new_workout)) }
                if(compact) Button(onClick={ c.select(null); activity.startTraining() },contentPadding=PaddingValues(horizontal=12.dp)) { Text(stringResource(R.string.free_training),maxLines=1) }
            } }
            if(!compact) item { Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).clickable { activity.page.value=2 }.padding(4.dp)) {
                        Text(if(state.demo) stringResource(R.string.demo) else link.name.ifBlank { stringResource(R.string.devices) },fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(stringResource(if(state.demo || link.phase=="ready") R.string.connected else R.string.connect),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick={ c.select(null); activity.startTraining() },contentPadding=PaddingValues(horizontal=16.dp)) { Text(stringResource(R.string.free_training)) }
                }
            } }
            item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SearchBox(search,{ search=it },stringResource(R.string.search_plans),Modifier.weight(1f),"plan-search")
                FilledTonalIconButton(onClick={ filters=true },modifier=Modifier.size(48.dp)) { FilterGlyph(stringResource(R.string.filters)) }
            } }
            item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { PlanDuration.entries.forEach { value -> FilterChip(selected=duration==value.name,onClick={ duration=value.name },label={ Text(stringResource(durationLabel(value))) }) } } }
            if(intensity!=PlanIntensity.ALL.name || source!=0) item { TextButton(onClick={ intensity=PlanIntensity.ALL.name; source=0; duration=PlanDuration.ALL.name; search="" }) { Text(stringResource(R.string.reset_filters)) } }
            if(filtered.isEmpty()) item { Text(stringResource(R.string.no_matches),Modifier.padding(vertical=24.dp)) }
            groups.forEach { (label,plans) ->
                if(plans.isNotEmpty() || !filtering && label in setOf(R.string.favorites,R.string.custom)) {
                    item(key="header-$label") { Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { collapsed.value=if(label in collapsed.value) collapsed.value-label else collapsed.value+label },verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(label),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text("  ${plans.size}",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f)); Icon(if(label in collapsed.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,stringResource(if(label in collapsed.value) R.string.show_all_count else R.string.show_less,plans.size))
                    } }
                    if(label !in collapsed.value) {
                        if(plans.isEmpty() && label==R.string.favorites) item { Text(stringResource(R.string.favorites_empty),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        val shown=if(label in expanded.value) plans else plans.take(if(label==R.string.builtin_plans) 4 else 2)
                        items(shown.chunked(columns),key={ it.joinToString { w->w.id } }) { group -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            group.forEach { w -> WorkoutRow(w,w.id in favorites,Modifier.weight(1f),{ favorite(w) },{ detailId=w.id }) }
                            if(group.size<columns) Spacer(Modifier.weight(1f))
                        } }
                        if(plans.size>if(label==R.string.builtin_plans) 4 else 2) item { TextButton(onClick={ expanded.value=if(label in expanded.value) expanded.value-label else expanded.value+label },modifier=Modifier.fillMaxWidth()) { Text(if(label in expanded.value) stringResource(R.string.show_less) else stringResource(R.string.show_all_count,plans.size)) } }
                    }
                }
            }
        }
    }
    if(filters) AlertDialog(onDismissRequest={ filters=false },title={ Text(stringResource(R.string.filters)) },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.peak_target),fontWeight=FontWeight.Bold)
        PlanIntensity.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable { intensity=value.name },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=intensity==value.name,onClick={ intensity=value.name }); Text(stringResource(intensityLabel(value))) } }
        Text(stringResource(R.string.plan_type),fontWeight=FontWeight.Bold)
        listOf(R.string.all_items,R.string.builtin_plans,R.string.custom).forEachIndexed { i,id -> Row(Modifier.fillMaxWidth().clickable { source=i },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=source==i,onClick={ source=i }); Text(stringResource(id)) } }
    } },confirmButton={ TextButton(onClick={ filters=false }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ intensity=PlanIntensity.ALL.name; source=0; duration=PlanDuration.ALL.name; search=""; filters=false }) { Text(stringResource(R.string.reset_filters)) } })
    all.find { it.id==detailId }?.let { w ->
        AlertDialog(onDismissRequest={ detailId=null },title={ Text(workoutTitle(w)) },text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MiniProfile(w,Modifier.fillMaxWidth().height(42.dp))
            if(w.builtin && w.id=="cardio40") { val kg by c.display.weight.collectAsStateWithLifecycle(); Text(stringResource(R.string.plan_energy_reference,WorkoutService.number(kg),(6*3.5*kg/200*40).toInt(),(8*3.5*kg/200*40).toInt()),style=MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.percentage_help),style=MaterialTheme.typography.bodySmall)
            w.steps.forEachIndexed { i,step -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${i+1}",Modifier.width(22.dp),style=MaterialTheme.typography.bodySmall)
                Text(if(step.condition==Condition.TIME) context.minutesSeconds((step.target*1000).toLong()) else "${WorkoutService.number(step.target)} ${if(step.condition==Condition.DISTANCE) "m" else stringResource(R.string.strokes)}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                Text(step.resistancePercent?.let { p -> c.range()?.let { r -> "${WorkoutService.number(r.percent(p))} · ${r.percentage(r.percent(p))}%" } ?: "$p%" } ?: "—",style=MaterialTheme.typography.bodySmall)
            } }
            Text(stringResource(R.string.plan_help),style=MaterialTheme.typography.bodySmall)
            if(!w.builtin) TextButton(onClick={ deleting=w; detailId=null }) { Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) }
        } },confirmButton={ TextButton(onClick={ c.select(w); detailId=null; activity.startTraining() }) { Text(stringResource(R.string.start)) } },dismissButton={ TextButton(onClick={ editor=w; detailId=null }) { Text(stringResource(if(w.builtin) R.string.duplicate else R.string.edit)) } })
    }
    deleting?.let { w -> AlertDialog(onDismissRequest={ deleting=null },title={ Text(stringResource(R.string.delete)) },text={ Text(workoutTitle(w)) },confirmButton={ TextButton(onClick={ c.scope.launch { runCatching { c.repo.deleteWorkout(w.id) }.onFailure { c.error(R.string.io_failed) } }; deleting=null }) { Text(stringResource(R.string.delete)) } },dismissButton={ TextButton(onClick={ deleting=null }) { Text(stringResource(R.string.cancel)) } }) }
    editor?.let { WorkoutEditor(it,c.range(),onDismiss={ editor=null },onSave={ w -> c.scope.launch { runCatching { c.repo.saveWorkout(w) }.onFailure { c.error(R.string.storage_failed) } }; editor=null }) }
}
@Composable private fun WorkoutRow(w: Workout,favorite: Boolean,modifier: Modifier,onFavorite: ()->Unit,onOpen: ()->Unit) {
    Surface(onClick=onOpen,modifier=modifier.testTag("plan-${w.id}"),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start=12.dp,top=10.dp,bottom=10.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MiniProfile(w,Modifier.width(28.dp).height(32.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(workoutTitle(w),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text((if(w.steps.all { it.condition==Condition.TIME }) stringResource(R.string.minutes_format,(w.steps.sumOf { it.target }/60).toInt())+" · " else "")+stringResource(R.string.stages_format,w.steps.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconToggleButton(checked=favorite,onCheckedChange={ onFavorite() },modifier=Modifier.testTag("favorite-${w.id}")) { FavoriteGlyph(favorite,stringResource(if(favorite) R.string.favorite_remove else R.string.favorite_add)) }
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
    Canvas(modifier) { val width=size.width/w.steps.size; w.steps.forEachIndexed { i,s -> val h=size.height*(.15f+(s.resistancePercent ?: 0)/100f*.85f); drawRect(color.copy(alpha=.8f),Offset(i*width,size.height-h),Size((width-1).coerceAtLeast(.5f),h)) } }
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
