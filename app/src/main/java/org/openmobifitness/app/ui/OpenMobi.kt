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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
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
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.Updates
import org.openmobifitness.app.data.Release
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Pink=Color(0xFFD64C86)
private val Blue=Color(0xFF527CE8)
private val Yellow=Color(0xFFF3C64E)
private val Ink=Color(0xFF191A22)
private val darkColors=darkColorScheme(
    primary=Color(0xFFFF9FC7),onPrimary=Color(0xFF4D1730),primaryContainer=Color(0xFF492737),onPrimaryContainer=Color(0xFFFFDAE9),
    secondary=Color(0xFFADC3FF),secondaryContainer=Color(0xFF263956),onSecondaryContainer=Color(0xFFD9E4FF),
    tertiary=Yellow,tertiaryContainer=Color(0xFF483C1A),onTertiaryContainer=Color(0xFFFFEBAD),
    background=Color(0xFF111218),surface=Color(0xFF1C1D25),surfaceVariant=Color(0xFF2B2C32),onSurfaceVariant=Color(0xFFC7C7CE),
    onSurface=Color(0xFFF2F2F4),surfaceContainer=Color(0xFF23242B),surfaceContainerHigh=Color(0xFF2D2E35),surfaceContainerHighest=Color(0xFF35363D),
    surfaceContainerLow=Color(0xFF191A20),surfaceContainerLowest=Color(0xFF0E0F13),error=Color(0xFFFFADB4))
private val lightColors=lightColorScheme(
    primary=Color(0xFFA62D60),onPrimary=Color.White,primaryContainer=Color(0xFFFADCE7),onPrimaryContainer=Color(0xFF5A1631),
    secondary=Color(0xFF3059B6),secondaryContainer=Color(0xFFE4EBFF),onSecondaryContainer=Color(0xFF18366D),
    tertiary=Color(0xFF755A00),tertiaryContainer=Color(0xFFFFF0B3),onTertiaryContainer=Color(0xFF352B00),
    background=Color(0xFFF8F8FA),surface=Color.White,surfaceVariant=Color(0xFFF0F0F3),onSurfaceVariant=Color(0xFF565760),
    onSurface=Ink,surfaceContainer=Color(0xFFF0F0F3),surfaceContainerHigh=Color(0xFFE9E9ED),surfaceContainerHighest=Color(0xFFE3E3E8),
    surfaceContainerLow=Color(0xFFF5F5F7),surfaceContainerLowest=Color.White,error=Color(0xFFBA263F))

@Composable fun OpenMobi(activity: MainActivity,controller: Controller) {
    val theme by controller.theme.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val link by controller.ble.state.collectAsStateWithLifecycle()
    val page by activity.page
    val dark=theme=="dark" || (theme=="system" && isSystemInDarkTheme())
    SideEffect { WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } }
    MaterialTheme(colorScheme=if(dark) darkColors else lightColors,typography=Typography()) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val rail=maxWidth>=720.dp
                val labels=listOf(R.string.train,R.string.history,R.string.devices,R.string.settings)
                val icons=listOf(Icons.Default.PlayArrow,Icons.Default.List,Icons.Default.Search,Icons.Default.Settings)
                Row(Modifier.fillMaxSize()) {
                    if(rail) NavigationRail(containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight().width(100.dp)) {
                        Image(painterResource(R.drawable.ic_mark),"OpenMobi",Modifier.size(76.dp))
                        Spacer(Modifier.height(32.dp))
                        labels.forEachIndexed { i,id -> NavigationRailItem(selected=page==i,onClick={ activity.page.value=i },icon={ Icon(icons[i],null) },label={ Text(stringResource(id)) },modifier=Modifier.padding(vertical=8.dp)) }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(!rail) Image(painterResource(R.drawable.ic_mark),null,Modifier.size(40.dp))
                            Text("OpenMobi",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            BadgeText(if(state.demo) stringResource(R.string.demo) else stringResource(R.string.local_only),if(state.demo) Yellow else Blue)
                        }
                        if(state.error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),shape=RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(state.errorRow!=null) stringResource(state.error!!,state.errorRow!!) else stringResource(state.error!!),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                IconButton(onClick={ controller.error(null) }) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
                            }
                        }
                        if(state.session!=null && page!=0) TextButton(onClick={ activity.page.value=0 },modifier=Modifier.fillMaxWidth()) { Text("${stringResource(R.string.active)} · ${WorkoutService.elapsed(state.session!!.elapsedMs)}") }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when(page) {
                                0 -> if(state.session==null) Home(activity,controller,state,link) else Live(activity,controller,state,link)
                                1 -> History(activity,controller)
                                2 -> Devices(activity,controller,state,link)
                                else -> SettingsPage(activity,controller)
                            }
                        }
                        if(!rail) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                            labels.forEachIndexed { i,id -> NavigationBarItem(selected=page==i,onClick={ activity.page.value=i },icon={ Icon(icons[i],null) },label={
                                BasicText(stringResource(id),style=LocalTextStyle.current.copy(color=LocalContentColor.current,textAlign=TextAlign.Center,letterSpacing=0.sp),maxLines=2,
                                    autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=12.sp,stepSize=1.sp))
                            }) }
                        }
                    }
                }
            }
        }
        val preview by activity.preview
        if(preview!=null) AlertDialog(onDismissRequest={ activity.preview.value=null },title={ Text(stringResource(R.string.import_preview)) },
            text={ Text(stringResource(R.string.import_summary,preview!!.sessions.size,preview!!.samples.size,preview!!.workouts.size)) },
            confirmButton={ TextButton(onClick=activity::confirmImport) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ activity.preview.value=null }) { Text(stringResource(R.string.cancel)) } })
    }
}
@Composable private fun BadgeText(text: String,color: Color) {
    Surface(color=color.copy(alpha=.13f),shape=RoundedCornerShape(30.dp)) {
        Text(text,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun SectionTitle(title: String,subtitle: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Home(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val workouts by c.repo.workouts.collectAsStateWithLifecycle()
    val newWorkoutTitle=stringResource(R.string.new_workout)
    var detail by remember { mutableStateOf<Workout?>(null) }
    var editor by remember { mutableStateOf<Workout?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns=if(maxWidth>=1000.dp) 3 else if(maxWidth>=600.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item {
                Surface(color=Ink,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth()) {
                    Box {
                        OrbitArt(Modifier.align(Alignment.TopEnd).size(230.dp))
                        Column(Modifier.padding(28.dp).widthIn(max=640.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            Text(stringResource(R.string.ready_title),color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                            Text(stringResource(R.string.ready_body),color=Color(0xFFCAC8D3),style=MaterialTheme.typography.bodyLarge)
                            Button(onClick={ c.select(null); activity.startTraining() },colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFACCD),contentColor=Ink),contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp)) {
                                Icon(Icons.Default.PlayArrow,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.free_training))
                            }
                        }
                    }
                }
            }
            item { ConnectionCard(state,link) { activity.page.value=2 } }
            item { SectionTitle(stringResource(R.string.presets),stringResource(R.string.original_templates)) }
            items(Presets.all.chunked(columns)) { group -> Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                group.forEachIndexed { i,workout -> PlanCard(workout,i,Modifier.weight(1f)) { detail=workout } }
                repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
            } }
            item { Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.custom),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                TextButton(onClick={ editor=Workout(title=newWorkoutTitle,steps=listOf(Step(target=60.0))) }) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.new_workout)) }
            } }
            items(workouts) { w -> PlanCard(w,0,Modifier.fillMaxWidth()) { detail=w } }
        }
    }
    detail?.let { workout -> AlertDialog(onDismissRequest={ detail=null },title={ Text(workoutTitle(workout)) },text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { Profile(workout); Text(stringResource(R.string.stages_format,workout.steps.size)); Text(workout.steps.joinToString(" → ") { "${it.resistancePercent ?: 0}%" },style=MaterialTheme.typography.bodySmall) } },
        confirmButton={ TextButton(onClick={ c.select(workout); detail=null; activity.startTraining() }) { Text(stringResource(R.string.start)) } },
        dismissButton={ TextButton(onClick={ editor=workout; detail=null }) { Text(stringResource(if(workout.builtin) R.string.duplicate else R.string.edit)) } }) }
    editor?.let { WorkoutEditor(it,onDismiss={ editor=null },onSave={ w -> c.scope.launch { runCatching { c.repo.saveWorkout(w) }.onFailure { c.error(R.string.storage_failed) } }; editor=null }) }
}
@Composable private fun ConnectionCard(state: ExerciseState,link: LinkState,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Settings,null,tint=MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(state.demo) "OpenMobi Demo" else link.name.ifEmpty { stringResource(R.string.devices) },fontWeight=FontWeight.Bold)
                Text(if(state.demo) stringResource(R.string.demo) else phase(link.phase),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.devices))
        }
    }
}
@Composable private fun OrbitArt(modifier: Modifier) { Canvas(modifier) {
    val center=androidx.compose.ui.geometry.Offset(size.width*.77f,size.height*.44f)
    listOf(Pink,Blue,Yellow).forEachIndexed { i,color -> drawCircle(color.copy(alpha=.16f),size.width*(.2f+i*.14f),center,style=Stroke(12.dp.toPx())) }
} }
@Composable private fun PlanCard(workout: Workout,index: Int,modifier: Modifier,onClick: ()->Unit) {
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(workoutTitle(workout),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Icon(Icons.Default.PlayArrow,null,tint=listOf(Pink,Blue,MaterialTheme.colorScheme.primary)[index%3])
            }
            Profile(workout)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(workout.steps.all { it.condition==Condition.TIME }) Text(stringResource(R.string.minutes_format,(workout.steps.sumOf { it.target }/60).toInt()),style=MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.stages_format,workout.steps.size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun Profile(workout: Workout,current: Int=-1) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val step=size.width/workout.steps.size
        workout.steps.forEachIndexed { i,s ->
            val h=size.height*(.12f+(s.resistancePercent ?: 0)/100f*.88f)
            drawRoundRect(if(i==current) Yellow else color.copy(alpha=if(i<current) .25f else .6f),androidx.compose.ui.geometry.Offset(i*step+1,size.height-h),androidx.compose.ui.geometry.Size((step-3).coerceAtLeast(1f),h),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}
@Composable private fun Live(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    var finish by remember { mutableStateOf(false) }
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val fold by produceState<FoldingFeature?>(null,activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value=info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating || it.state==FoldingFeature.State.HALF_OPENED }
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin=it.positionInWindow() }) {
        val wide=maxWidth>=900.dp
        val feature=fold
        val left=with(density) { ((feature?.bounds?.left ?: 0)-origin.x).toDp() }
        val top=with(density) { ((feature?.bounds?.top ?: 0)-origin.y).toDp() }
        val gapX=with(density) { (feature?.bounds?.width() ?: 0).toDp() }
        val gapY=with(density) { (feature?.bounds?.height() ?: 0).toDp() }
        if(feature?.orientation==FoldingFeature.Orientation.VERTICAL && left>180.dp && maxWidth-left-gapX>180.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(left).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) { Dashboard(state,imperial); Stages(state) }
                Spacer(Modifier.width(gapX))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) { Controls(c,state,link); LiveActions(activity,c,state) { finish=true } }
            }
        } else if(feature?.orientation==FoldingFeature.Orientation.HORIZONTAL && top>120.dp && maxHeight-top-gapY>180.dp) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.height(top).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { Dashboard(state,imperial) }
                Spacer(Modifier.height(gapY))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { Controls(c,state,link); LiveActions(activity,c,state) { finish=true }; Stages(state) }
            }
        } else if(wide) Row(Modifier.fillMaxSize().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.5f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) { Dashboard(state,imperial); Controls(c,state,link) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) { LiveActions(activity,c,state) { finish=true }; Stages(state) }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Dashboard(state,imperial); Controls(c,state,link); LiveActions(activity,c,state) { finish=true }; Stages(state)
        }
    }
    if(finish) AlertDialog(onDismissRequest={ finish=false },title={ Text(stringResource(R.string.finish)) },text={ Text(stringResource(R.string.finish_note)) },confirmButton={ TextButton(onClick={ c.finish(); finish=false }) { Text(stringResource(R.string.finish)) } },dismissButton={ TextButton(onClick={ finish=false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun Dashboard(state: ExerciseState,imperial: Boolean) {
    Surface(color=Ink,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(if(state.paused) R.string.paused else R.string.active),Modifier.weight(1f),color=Color(0xFFFFA3CA),style=MaterialTheme.typography.labelLarge)
                Text(if(state.demo) stringResource(R.string.demo) else state.session?.device.orEmpty(),color=Color(0xFFD0CED8),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=160.dp))
            }
            BasicText(WorkoutService.elapsed(state.session?.elapsedMs ?: 0),style=LocalTextStyle.current.copy(color=Color.White,fontWeight=FontWeight.Light,letterSpacing=1.sp),maxLines=1,
                autoSize=TextAutoSize.StepBased(minFontSize=18.sp,maxFontSize=46.sp,stepSize=1.sp))
            val m=state.metrics
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.resistance),num(m.resistance),"",Modifier.weight(1f),true)
                Metric(stringResource(R.string.cadence),num(m.cadence),"rpm",Modifier.weight(1f),true)
            }
            HorizontalDivider(color=Color.White.copy(alpha=.12f))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp),maxItemsInEachRow=if(LocalDensity.current.fontScale>1.2f) 2 else 3) {
                val distance=state.session?.distanceM
                Metric(stringResource(R.string.distance),distance?.let { "%.2f".format(it/(if(imperial) 1609.344 else 1000.0)) } ?: "—",if(imperial) "mi" else "km",Modifier.weight(1f),true)
                Metric(stringResource(R.string.heart_rate),m.heartBpm?.toString() ?: "—","bpm",Modifier.weight(1f),true)
                Metric(stringResource(R.string.power),num(m.powerW),"W",Modifier.weight(1f),true)
            }
        }
    }
}
@Composable private fun Metric(label: String,value: String,unit: String,modifier: Modifier=Modifier,onDark: Boolean=false) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,color=if(onDark) Color(0xFFBBB9C8) else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
        BasicText(value,style=LocalTextStyle.current.copy(color=if(onDark) Color.White else MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold),maxLines=1,
            autoSize=TextAutoSize.StepBased(minFontSize=16.sp,maxFontSize=28.sp,stepSize=1.sp))
        if(unit.isNotEmpty()) Text(unit,color=if(onDark) Color(0xFFBBB9C8) else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun Controls(c: Controller,state: ExerciseState,link: LinkState) {
    val can=state.demo || (link.writable && link.range!=null && link.metrics.resistance!=null && !link.busy && link.phase=="ready")
    Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick={ c.adjust(-1) },enabled=can,modifier=Modifier.size(56.dp),contentPadding=PaddingValues(0.dp)) { Text("−",fontSize=32.sp) }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) { Text(stringResource(R.string.resistance),style=MaterialTheme.typography.labelLarge); Text(num(state.metrics.resistance),fontSize=36.sp,fontWeight=FontWeight.Bold) }
                Button(onClick={ c.adjust(1) },enabled=can,modifier=Modifier.size(56.dp),contentPadding=PaddingValues(0.dp)) { Text("+",fontSize=32.sp) }
            }
            if(!can) Text(stringResource(R.string.control_unavailable),style=MaterialTheme.typography.bodySmall)
            link.requested?.let { Text(stringResource(R.string.control_pending,num(it)),style=MaterialTheme.typography.bodySmall) }
            if(state.selected!=null) Column {
                Text(stringResource(if(state.automatic) R.string.auto else R.string.manual),style=MaterialTheme.typography.labelMedium)
                if(!state.automatic) TextButton(onClick=c::resumeAutomatic,enabled=can,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_auto)) }
            }
        }
    }
}
@Composable private fun LiveActions(activity: MainActivity,c: Controller,state: ExerciseState,finish: ()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Button(onClick=c::pauseResume,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text(stringResource(if(state.paused) R.string.resume else R.string.pause)) }
        OutlinedButton(onClick=activity::minimize,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text(stringResource(R.string.minimize)) }
        TextButton(onClick=finish,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish),color=MaterialTheme.colorScheme.error) }
    }
}
@Composable private fun Stages(state: ExerciseState) {
    state.selected?.let { workout ->
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(workoutTitle(workout),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Profile(workout,state.stage)
            if(state.done) Text(stringResource(R.string.workout_done)) else {
                Text(stringResource(R.string.stage_format,state.stage+1,workout.steps.size))
                LinearProgressIndicator(progress={ state.progress },modifier=Modifier.fillMaxWidth(),color=Pink)
            }
            workout.steps.forEachIndexed { i,step ->
                Surface(color=if(i==state.stage) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("${i+1}",fontWeight=FontWeight.Bold)
                        Text("${num(step.target)} ${when(step.condition) { Condition.TIME -> "s"; Condition.DISTANCE -> "m"; Condition.STROKES -> stringResource(R.string.strokes) }}",Modifier.weight(1f))
                        Text(step.resistancePercent?.let { "$it%" } ?: "—")
                    }
                }
            }
        }
    }
}
@Composable private fun Devices(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val found by c.ble.found.collectAsStateWithLifecycle(); val scanning by c.ble.scanning.collectAsStateWithLifecycle()
    var diagnostics by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { SectionTitle(stringResource(R.string.devices),stringResource(R.string.scan_help)) }
        item { ConnectionCard(state,link) {} }
        if(link.phase=="ready") item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${link.protocol} · ${stringResource(if(link.writable) R.string.unverified else R.string.read_only)}")
                link.range?.let { Text(stringResource(R.string.range_format,num(it.min),num(it.max))) }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick=activity::disconnect) { Text(stringResource(R.string.disconnect)) }
                    TextButton(onClick={ diagnostics=true }) { Text(stringResource(R.string.diagnostics)) }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick=activity::scan,enabled=!scanning) { Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text(stringResource(if(scanning) R.string.scanning else R.string.scan)) }
                if(c.prefs.contains("last_address") && link.phase=="disconnected") OutlinedButton(onClick=activity::reconnect) { Text(stringResource(R.string.reconnect)) }
            }
        }
        items(found,key={ it.address }) { device ->
            OutlinedCard(onClick={ activity.connect(device) },shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(device.name.ifEmpty { stringResource(R.string.unknown_device) },fontWeight=FontWeight.Bold); Text(if(device.heart) stringResource(R.string.heart_rate) else "${device.rssi} dBm",style=MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.connect),color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(found.isEmpty()) item { Text(stringResource(R.string.no_devices),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.permission_help),style=MaterialTheme.typography.bodySmall) }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.demo),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.demo_description))
                    OutlinedButton(onClick={ c.setDemo(!state.demo); if(!state.demo) activity.page.value=0 },enabled=state.session==null) { Text(stringResource(if(state.demo) R.string.exit_demo else R.string.start_demo)) }
                }
            }
        }
        item { TextButton(onClick={ diagnostics=true }) { Text(stringResource(R.string.diagnostics)) } }
    }
    if(diagnostics) AlertDialog(onDismissRequest={ diagnostics=false },title={ Text(stringResource(R.string.diagnostics)) },text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) { Text(stringResource(R.string.diagnostic_help)); Text(link.diagnostic.joinToString("\n"),style=MaterialTheme.typography.bodySmall) } },confirmButton={ TextButton(onClick={ activity.export("diagnostics") }) { Text(stringResource(R.string.export_diagnostics)) } },dismissButton={ TextButton(onClick={ diagnostics=false }) { Text(stringResource(R.string.close)) } })
}
@Composable private fun History(activity: MainActivity,c: Controller) {
    val sessions by c.repo.sessions.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<Session?>(null) }; var deleting by remember { mutableStateOf<Session?>(null) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { SectionTitle(stringResource(R.string.history)) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ activity.export("sessions") }) { Text(stringResource(R.string.export_sessions)) }
            OutlinedButton(onClick={ activity.export("backup") }) { Text(stringResource(R.string.export_backup)) }
            TextButton(onClick=activity::importFile) { Text(stringResource(R.string.import_file)) }
        } }
        if(sessions.isEmpty()) item { Text(stringResource(R.string.records_empty),Modifier.padding(vertical=60.dp),style=MaterialTheme.typography.headlineSmall) }
        items(sessions,key={ it.id }) { session ->
            Card(onClick={ detail=session },colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(date(session.start),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        if(session.demo) BadgeText(stringResource(R.string.demo),Yellow)
                    }
                    Text(session.device,style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                        Text(WorkoutService.elapsed(session.elapsedMs),style=MaterialTheme.typography.headlineSmall)
                        Text(session.distanceM?.let { "%.2f %s".format(it/(if(imperial) 1609.344 else 1000.0),if(imperial) "mi" else "km") } ?: "—",style=MaterialTheme.typography.headlineSmall)
                    }
                    Text(status(session.status),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    detail?.let { session ->
        var samples by remember(session.id) { mutableStateOf<List<Sample>>(emptyList()) }
        LaunchedEffect(session.id) { runCatching { c.repo.archive(session.id).samples }.onSuccess { samples=it }.onFailure { c.error(R.string.storage_failed) } }
        AlertDialog(onDismissRequest={ detail=null },title={ Text(date(session.start)) },text={ Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("${session.device}\n${WorkoutService.elapsed(session.elapsedMs)}\n${status(session.status)}")
            if(samples.any { it.metrics.cadence!=null }) { Text(stringResource(R.string.cadence)); Sparkline(samples.mapNotNull { it.metrics.cadence }) } else Text(stringResource(R.string.no_samples))
            TextButton(onClick={ activity.export("samples",session.id) }) { Text(stringResource(R.string.export_samples)) }
            TextButton(onClick={ activity.export("backup",session.id) }) { Text(stringResource(R.string.export_backup)) }
        } },confirmButton={ TextButton(onClick={ detail=null }) { Text(stringResource(R.string.close)) } },dismissButton={ if(session.status!="active") TextButton(onClick={ deleting=session; detail=null }) { Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) } })
    }
    deleting?.let { session -> AlertDialog(onDismissRequest={ deleting=null },title={ Text(stringResource(R.string.delete)) },text={ Text(stringResource(R.string.delete_confirmation)) },confirmButton={ TextButton(onClick={ c.scope.launch { runCatching { c.repo.deleteSession(session.id) }.onFailure { c.error(R.string.storage_failed) } }; deleting=null }) { Text(stringResource(R.string.delete)) } },dismissButton={ TextButton(onClick={ deleting=null }) { Text(stringResource(R.string.cancel)) } }) }
}
@Composable private fun Sparkline(values: List<Double>) { Canvas(Modifier.fillMaxWidth().height(100.dp)) {
    if(values.size>1) {
        val sampled=values.filterIndexed { i,_ -> i%maxOf(1,values.size/120)==0 }; val max=(sampled.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val path=Path(); sampled.forEachIndexed { i,v -> val x=size.width*i/(sampled.size-1); val y=size.height*(1-v/max).toFloat(); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
        drawPath(path,Blue,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
    }
} }
@Composable private fun SettingsPage(activity: MainActivity,c: Controller) {
    val theme by c.theme.collectAsStateWithLifecycle(); val imperial by c.imperial.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }; var updateMessage by remember { mutableStateOf<Int?>(null) }; var release by remember { mutableStateOf<Release?>(null) }
    val scope=rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
        item { SectionTitle(stringResource(R.string.settings),stringResource(R.string.local_only)) }
        item { SettingGroup(stringResource(R.string.language)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("" to stringResource(R.string.system),"zh-Hans" to "简体中文","zh-Hant" to "繁體中文","en" to "English","ja" to "日本語","ko" to "한국어","de" to "Deutsch").forEach { (tag,name) ->
                    FilterChip(selected=activity.currentLanguage()==tag,onClick={ activity.setLanguage(tag) },label={ Text(name) })
                }
            }
        } }
        item { SettingGroup(stringResource(R.string.theme)) { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to R.string.system,"light" to R.string.light,"dark" to R.string.dark).forEach { (value,label) -> FilterChip(selected=theme==value,onClick={ c.setTheme(value) },label={ Text(stringResource(label)) }) }
        } } }
        item { SettingGroup(stringResource(R.string.units)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!imperial,onClick={ c.setImperial(false) },label={ Text(stringResource(R.string.metric)) })
            FilterChip(selected=imperial,onClick={ c.setImperial(true) },label={ Text(stringResource(R.string.imperial)) })
        } } }
        item { SettingGroup(stringResource(R.string.history)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ activity.export("backup") }) { Text(stringResource(R.string.export_backup)) }
                OutlinedButton(onClick={ activity.export("workouts") }) { Text(stringResource(R.string.export_workouts)) }
                TextButton(onClick=activity::importFile) { Text(stringResource(R.string.import_file)) }
            }
        } }
        item { SettingGroup(stringResource(R.string.updates)) {
            Text("OpenMobi ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodyMedium)
            Button(enabled=!checking,onClick={ scope.launch {
                checking=true; updateMessage=null
                try { release=Updates.check(BuildConfig.VERSION_NAME); if(release==null) updateMessage=R.string.up_to_date }
                catch(e: Exception) { updateMessage=R.string.network_unavailable }
                finally { checking=false }
            } }) { Text(stringResource(if(checking) R.string.checking else R.string.check_updates)) }
            updateMessage?.let { Text(stringResource(it)) }
            release?.let { found -> Text(stringResource(R.string.update_available,found.name)); TextButton(onClick={ activity.openUrl(found.url) }) { Text(stringResource(R.string.open_release)) } }
            TextButton(onClick={ activity.openUrl(Updates.REPOSITORY) }) { Text(stringResource(R.string.source_code)) }
        } }
        item { Text(stringResource(R.string.overlay_info),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SettingGroup(title: String,content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); content() }
}
private data class Draft(val condition: Condition,val target: String,val resistance: String)
@Composable private fun WorkoutEditor(workout: Workout,onDismiss: ()->Unit,onSave: (Workout)->Unit) {
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
@Composable private fun workoutTitle(workout: Workout): String {
    val id=when(workout.id) { "warmup" -> R.string.warmup; "recovery" -> R.string.recovery; "steady20" -> R.string.steady20; "steady30" -> R.string.steady30; "endurance" -> R.string.endurance; "interval10" -> R.string.interval10; "interval20" -> R.string.interval20; "interval30" -> R.string.interval30; "pyramid20" -> R.string.pyramid20; "pyramid30" -> R.string.pyramid30; "progressive" -> R.string.progressive; "cooldown" -> R.string.cooldown; else -> null }
    return if(workout.builtin && id!=null) stringResource(id) else workout.title
}
@Composable private fun phase(value: String) = stringResource(when(value) { "ready" -> R.string.connected; "connecting" -> R.string.connecting; "discovering" -> R.string.discovering; else -> R.string.disconnected })
@Composable private fun status(value: String) = stringResource(when(value) { "completed" -> R.string.completed; "interrupted" -> R.string.interrupted; "active" -> R.string.active; else -> R.string.stopped })
private fun num(value: Double?)=WorkoutService.number(value)
private fun date(value: String)=runCatching { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault(value)
