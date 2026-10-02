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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Pink=Color(0xFFD64C86)
private val Blue=Color(0xFF527CE8)
private val Yellow=Color(0xFFF3C64E)
private val Ink=Color(0xFF192538)
private val darkColors=darkColorScheme(
    primary=Color(0xFFAAC7FF),onPrimary=Color(0xFF002F65),primaryContainer=Color(0xFF234879),onPrimaryContainer=Color(0xFFD5E3FF),
    secondary=Color(0xFFADC3FF),secondaryContainer=Color(0xFF263956),onSecondaryContainer=Color(0xFFD9E4FF),
    tertiary=Yellow,tertiaryContainer=Color(0xFF483C1A),onTertiaryContainer=Color(0xFFFFEBAD),
    background=Color(0xFF111218),surface=Color(0xFF1C1D25),surfaceVariant=Color(0xFF2B2C32),onSurfaceVariant=Color(0xFFC7C7CE),
    onSurface=Color(0xFFF2F2F4),surfaceContainer=Color(0xFF23242B),surfaceContainerHigh=Color(0xFF2D2E35),surfaceContainerHighest=Color(0xFF35363D),
    surfaceContainerLow=Color(0xFF191A20),surfaceContainerLowest=Color(0xFF0E0F13),error=Color(0xFFFFADB4))
private val lightColors=lightColorScheme(
    primary=Color(0xFF0073E6),onPrimary=Color.White,primaryContainer=Color(0xFFF0F7FF),onPrimaryContainer=Color(0xFF173655),
    secondary=Color(0xFF3059B6),secondaryContainer=Color(0xFFE4EBFF),onSecondaryContainer=Color(0xFF18366D),
    tertiary=Color(0xFF755A00),tertiaryContainer=Color(0xFFFFF0B3),onTertiaryContainer=Color(0xFF352B00),
    background=Color(0xFFF8F9FB),surface=Color.White,surfaceVariant=Color(0xFFE1E6ED),onSurfaceVariant=Color(0xFF4D5B70),outline=Color(0xFFB8C2CF),outlineVariant=Color(0xFFDFE4EB),
    onSurface=Ink,surfaceContainer=Color(0xFFF0F0F3),surfaceContainerHigh=Color(0xFFE9E9ED),surfaceContainerHighest=Color(0xFFE3E3E8),
    surfaceContainerLow=Color(0xFFF5F5F7),surfaceContainerLowest=Color.White,error=Color(0xFFE5003A))

@Composable fun OpenMobi(activity: MainActivity,controller: Controller) {
    val theme by controller.theme.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val link by controller.ble.state.collectAsStateWithLifecycle()
    val pageState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val page by activity.page
    val focused by activity.focusTraining
    val dark=theme=="dark" || (theme=="system" && isSystemInDarkTheme())
    SideEffect { WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } }
    MaterialTheme(colorScheme=if(dark) darkColors else lightColors,typography=Typography()) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                if(focused && state.session!=null) { TrainingScreen(activity,controller,state,link) } else {
                val shortLayout=maxHeight<480.dp
                val rail=maxWidth>=720.dp || (maxWidth>=520.dp && shortLayout)
                val labels=listOf(R.string.train,R.string.history,R.string.devices,R.string.settings)
                val icons=listOf(Icons.Default.PlayArrow,Icons.Default.List,Icons.Default.Search,Icons.Default.Settings)
                Row(Modifier.fillMaxSize()) {
                    if(rail) NavigationRail(containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight().width(100.dp)) {
                        BrandMark(dark,Modifier.size(if(shortLayout) 40.dp else 76.dp))
                        Spacer(Modifier.height(if(shortLayout) 4.dp else 32.dp))
                        labels.forEachIndexed { i,id -> NavigationRailItem(selected=page==i,onClick={ activity.page.value=i; if(i==0 && state.session!=null) activity.focusTraining.value=true },icon={ Icon(icons[i],stringResource(id)) },label=if(shortLayout) null else ({ Text(stringResource(id)) }),modifier=Modifier.padding(vertical=if(shortLayout) 0.dp else 8.dp)) }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if(!shortLayout || !rail) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(!rail) { BrandMark(dark,Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
                            Text("OpenMOBI",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            BadgeText(if(state.demo) stringResource(R.string.demo) else stringResource(R.string.local_only),if(state.demo) Yellow else Blue)
                        }
                        if(state.error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),shape=RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(state.errorRow!=null) stringResource(state.error!!,state.errorRow!!) else stringResource(state.error!!),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                IconButton(onClick={ controller.error(null) }) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
                            }
                        }
                        if(state.session!=null && page!=0) ActiveWorkoutBanner(state) { activity.page.value=0; activity.focusTraining.value=true }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            pageState.SaveableStateProvider(page) { when(page) {
                                0 -> if(state.session==null) WorkoutLibrary(activity,controller,state,link) else TrainingScreen(activity,controller,state,link)
                                1 -> HistoryScreen(activity,controller)
                                2 -> DevicesScreen(activity,controller,state,link)
                                else -> SettingsScreen(activity,controller)
                            } }
                        }
                        if(!rail) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                            labels.forEachIndexed { i,id -> NavigationBarItem(selected=page==i,onClick={ activity.page.value=i; if(i==0 && state.session!=null) activity.focusTraining.value=true },icon={ Icon(icons[i],null) },label={
                                BasicText(stringResource(id),style=LocalTextStyle.current.copy(color=LocalContentColor.current,textAlign=TextAlign.Center,letterSpacing=0.sp),maxLines=2,
                                    autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=12.sp,stepSize=1.sp))
                            }) }
                        }
                    }
                }
                }
            }
        }
        val finished by controller.finishedSession.collectAsStateWithLifecycle()
        if(finished!=null) Dialog(onDismissRequest=controller::dismissResult,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=Alignment.Center) {
                Surface(Modifier.widthIn(max=1040.dp).fillMaxWidth().fillMaxHeight(if(maxHeight<440.dp) 1f else .94f),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.background) {
                    SessionDetailScreen(activity,controller,finished!!,saved=true,onBack=controller::dismissResult)
                }
            }
        }
        val preview by activity.preview
        activity.metricScope.value?.let { scope -> MetricPicker(controller,scope) { activity.metricScope.value=null; activity.intent.removeExtra("metrics_scope") } }
        if(preview!=null) AlertDialog(onDismissRequest={ activity.preview.value=null },title={ Text(stringResource(R.string.import_preview)) },
            text={ Text(stringResource(R.string.import_summary,preview!!.sessions.size,preview!!.samples.size,preview!!.workouts.size)) },
            confirmButton={ TextButton(onClick=activity::confirmImport) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ activity.preview.value=null }) { Text(stringResource(R.string.cancel)) } })
    }
}
@Composable private fun BrandMark(dark: Boolean,modifier: Modifier=Modifier) {
    Image(painterResource(if(dark) R.drawable.brand_dark else R.drawable.brand_light),"OpenMOBI",modifier.clip(RoundedCornerShape(12.dp)))
}
@Composable private fun BadgeText(text: String,color: Color) {
    Surface(color=color.copy(alpha=.13f),shape=RoundedCornerShape(30.dp)) {
        Text(text,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
private data class Draft(val condition: Condition,val target: String,val resistance: String)
@Composable internal fun WorkoutEditor(workout: Workout,range: ResistanceRange?=null,onDismiss: ()->Unit,onSave: (Workout)->Unit) {
    val originalName=if(workout.title.isEmpty()) "" else workoutTitle(workout)
    var title by remember { mutableStateOf(originalName) }; var invalid by remember { mutableStateOf(false) }
    val steps=remember { mutableStateListOf<Draft>().apply { addAll(workout.steps.map { Draft(it.condition,num(it.target),(it.resistancePercent ?: 0).toString()) }) } }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=740.dp).fillMaxHeight(.92f).imePadding(),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.new_workout),style=MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=20.dp)) {
                    item { OutlinedTextField(title,{ title=it },label={ Text(stringResource(R.string.title)) },modifier=Modifier.fillMaxWidth(),singleLine=true) }
                    items(steps.size) { i -> val draft=steps[i]
                        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.stage_format,i+1,steps.size),fontWeight=FontWeight.Bold)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Condition.entries.forEach { condition -> FilterChip(selected=draft.condition==condition,onClick={ steps[i]=draft.copy(condition=condition) },label={ Text(stringResource(when(condition) { Condition.TIME -> R.string.seconds; Condition.DISTANCE -> R.string.meters; Condition.STROKES -> R.string.strokes })) }) }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(draft.target,{ steps[i]=draft.copy(target=it) },label={ Text(stringResource(R.string.target)) },modifier=Modifier.weight(1f),singleLine=true)
                                OutlinedTextField(draft.resistance,{ steps[i]=draft.copy(resistance=it) },label={ Text(stringResource(R.string.resistance_percent)) },modifier=Modifier.weight(1f),singleLine=true)
                            }
                            draft.resistance.toIntOrNull()?.takeIf { it in 0..100 }?.let { percent -> range?.let { r -> Text(stringResource(R.string.mapped_target,num(r.percent(percent)),r.percentage(r.percent(percent))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) } }
                            if(steps.size>1) TextButton(onClick={ steps.removeAt(i) }) { Text(stringResource(R.string.remove_step)) }
                        } }
                    }
                    item { TextButton(enabled=steps.size<200,onClick={ steps.add(Draft(Condition.TIME,"60","20")) }) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.add_step)) } }
                }
                if(invalid) Text(stringResource(R.string.invalid_input),color=MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick={ try { onSave(Workout(if(workout.builtin) UUID.randomUUID().toString() else workout.id,title.trim(),steps.map { Step(it.condition,it.target.toDouble(),it.resistance.toInt()) })) } catch(e: Exception) { invalid=true } }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
@Composable internal fun workoutTitle(workout: Workout): String = LocalContext.current.workoutName(workout)
@Composable private fun phase(value: String) = stringResource(when(value) { "ready" -> R.string.connected; "awaiting_data" -> R.string.awaiting_data; "subscription_failed" -> R.string.subscription_failed; "connecting" -> R.string.connecting; "discovering" -> R.string.discovering; else -> R.string.disconnected })
@Composable private fun status(value: String) = stringResource(when(value) { "completed" -> R.string.completed; "interrupted" -> R.string.interrupted; "active" -> R.string.active; else -> R.string.stopped })
private fun num(value: Double?)=WorkoutService.number(value)
private fun date(value: String)=runCatching { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault(value)
