@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.rangeText
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

internal val brandColors = org.openmobifitness.app.BrandPalette.accents.map { Color(it) }
@Composable internal fun BrandSignature(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        brandColors.forEach { color ->
            Box(Modifier.width(6.dp).height(15.dp).rotate(16f).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

/** The workout owns a fixed action dock; only the instrument panels may scroll. */
@Composable internal fun TrainingScreen(activity: MainActivity, c: Controller, state: ExerciseState, link: LinkState) {
    if(state.selected==null) { FreeDashboard(activity,c,state,link); return }
    var picker by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var stages by remember { mutableStateOf(false) }
    var editPresets by rememberSaveable { mutableStateOf(false) }
    val focused by activity.focusTraining
    val floating by c.display.floatingEnabled.collectAsStateWithLifecycle()
    DisposableEffect(activity) {
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    fun back() { activity.focusTraining.value = false; activity.page.value = 1 }
    BackHandler(focused) { back() }
    val fold by produceState<FoldingFeature?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
                it.isSeparating || it.state == FoldingFeature.State.HALF_OPENED
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < 480.dp
        val tablet = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            WorkoutHeader(state, link, tablet && !short, short, ::back) { picker = true }
            state.error?.let { id -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.errorRow != null) stringResource(id, state.errorRow) else stringResource(id), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { c.error(null) }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
            } }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current
            val fontExtra = (density.fontScale - 1f).coerceAtLeast(0f)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { origin = it.positionInWindow() }) {
                val feature = fold
                val splitX = with(density) { ((feature?.bounds?.left ?: 0) - origin.x).toDp() }
                val splitY = with(density) { ((feature?.bounds?.top ?: 0) - origin.y).toDp() }
                val gapX = with(density) { (feature?.bounds?.width() ?: 0).toDp() }
                val gapY = with(density) { (feature?.bounds?.height() ?: 0).toDp() }
                val vertical = feature?.orientation == FoldingFeature.Orientation.VERTICAL && splitX >= 240.dp && maxWidth - splitX - gapX >= 240.dp
                val horizontal = feature?.orientation == FoldingFeature.Orientation.HORIZONTAL && splitY >= 140.dp && maxHeight - splitY - gapY >= 160.dp
                val square = !vertical && maxWidth >= 700.dp && maxWidth < 1000.dp && maxHeight >= 550.dp && maxHeight < 900.dp
                val wide = vertical || maxWidth >= 840.dp || (maxWidth >= 600.dp && short)
                val outer = if (short) 8.dp else if (tablet) 16.dp else 10.dp
                val gap = if (tablet && !short) 12.dp else 8.dp
                val available = maxHeight - outer * 2
                val portraitTablet = maxWidth >= 600.dp
                when {
                    horizontal -> Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxWidth().height(splitY).verticalScroll(rememberScrollState()).padding(outer)) {
                            TrainingProgress(c, state, Modifier.fillMaxWidth(), false, short) { stages = true }
                        }
                        Spacer(Modifier.height(gapY))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(outer), verticalArrangement = Arrangement.spacedBy(gap)) {
                            MetricPages(c, state, Modifier.fillMaxWidth(), false, short)
                            ResistanceControls(activity, c, state, link, Modifier.fillMaxWidth(), false, short) { editPresets=true }
                        }
                    }
                    square -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(outer), verticalArrangement = Arrangement.spacedBy(gap)) {
                        val progressHeight = ((available - gap) * .44f).coerceAtLeast(250.dp + (fontExtra * 150).dp)
                        TrainingProgress(c, state, Modifier.fillMaxWidth().height(progressHeight), true, true) { stages = true }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            MetricPages(c, state, Modifier.weight(1f), true, true)
                            ResistanceControls(activity, c, state, link, Modifier.weight(1f), true, false) { editPresets=true }
                        }
                    }
                    wide -> Row(Modifier.fillMaxSize().padding(vertical = outer)) {
                        val progressHeight = available.coerceAtLeast((if (short) 198 else 490).dp + (fontExtra * 220).dp)
                        Column((if (vertical) Modifier.width(splitX) else Modifier.weight(1.42f)).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = outer, end = if (vertical) outer else gap / 2)) {
                            TrainingProgress(c, state, Modifier.fillMaxWidth().height(progressHeight), !short, short) { stages = true }
                        }
                        if (vertical) Spacer(Modifier.width(gapX))
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = if (vertical) outer else gap / 2, end = outer), verticalArrangement = Arrangement.spacedBy(gap)) {
                            MetricPages(c, state, Modifier.fillMaxWidth(), !short, short || available<630.dp && density.fontScale<=1.1f)
                            ResistanceControls(activity, c, state, link, Modifier.fillMaxWidth(), !short, short) { editPresets=true }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(outer), verticalArrangement = Arrangement.spacedBy(gap)) {
                        val progressHeight = if (portraitTablet) ((available - gap * 2) * .38f).coerceAtLeast(390.dp + (fontExtra * 180).dp) else Dp.Unspecified
                        TrainingProgress(c, state, if (portraitTablet) Modifier.height(progressHeight) else Modifier, portraitTablet, false) { stages = true }
                        MetricPages(c, state, Modifier.fillMaxWidth(), portraitTablet, false)
                        ResistanceControls(activity, c, state, link, Modifier.fillMaxWidth(), portraitTablet, false) { editPresets=true }
                    }
                }
            }
            WorkoutDock(state, floating, tablet && !short, short, c.canStart(), c::pauseResume, { finish = true }, activity::minimize)
        }
    }
    if (picker) MetricPicker(c) { picker = false }
    if(editPresets) ResistancePresetEditor(c,state) { editPresets=false }
    if (stages) StageDialog(state, c) { stages = false }
    if(finish) FormDialog(stringResource(R.string.finish),{ finish=false },{ c.finish(); finish=false },confirmLabel=stringResource(R.string.finish)) { Text(stringResource(R.string.finish_note)) }
}

@Composable internal fun WorkoutHeader(state: ExerciseState, link: LinkState, tablet: Boolean, short: Boolean, back: () -> Unit, display: () -> Unit) {
    val displayDescription=stringResource(R.string.choose_metrics)
    Row(Modifier.fillMaxWidth().heightIn(min=if(short) 48.dp else if(tablet) 64.dp else 56.dp).padding(horizontal=if(tablet) 12.dp else 2.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=back) { Icon(Icons.Default.ArrowBack,stringResource(R.string.back_to_app)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("OpenMOBI",Modifier.weight(1f,false),style=if(tablet) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                BrandSignature(Modifier.padding(start=8.dp))
            }
            state.session?.startedUserName?.takeIf { it.isNotBlank() }?.let { name ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Lock,null,Modifier.size(12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        if(short || !tablet && LocalDensity.current.fontScale>1.15f) IconButton(onClick=display,modifier=Modifier.semantics { contentDescription=displayDescription }) { WorkoutIcon(WorkoutGlyph.DISPLAY,Modifier.size(24.dp)) }
        else TextButton(onClick=display,modifier=Modifier.semantics { contentDescription=displayDescription }) {
            WorkoutIcon(WorkoutGlyph.DISPLAY,Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.configure_display),style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun WorkoutDock(state: ExerciseState, floating: Boolean, large: Boolean, short: Boolean, ready: Boolean, pause: () -> Unit, finish: () -> Unit, minimize: () -> Unit) {
    val minimizeDescription = stringResource(R.string.minimize)
    val finishDescription = stringResource(if(state.controlOnly) R.string.disconnect_exit else R.string.finish_save)
    val density = LocalDensity.current
    val compactLabels = density.fontScale>=1.2f && (short || LocalWindowInfo.current.containerSize.width / density.density < 600f)
    Surface(modifier=Modifier.testTag("workout-dock"),color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = if (large) 20.dp else 10.dp, vertical = if(short) 2.dp else if (large) 10.dp else 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp)) {
                val height = if (large) 60.dp else 48.dp
                val shape = RoundedCornerShape(if (large) 12.dp else 9.dp)
                Button(onClick = pause, enabled=ready && !state.starting, modifier = Modifier.weight(if(state.controlOnly) 1.4f else 1f).fillMaxHeight().heightIn(min=height).testTag("pause"), shape = shape, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    DockContent(if (state.controlOnly || state.paused) WorkoutGlyph.PLAY else WorkoutGlyph.PAUSE, stringResource(if(state.controlOnly) R.string.start_recording else if (state.paused) R.string.resume_recording else R.string.pause_short), large)
                }
                if(!state.controlOnly || large || short) OutlinedButton(onClick = finish, modifier = Modifier.weight(1.15f).fillMaxHeight().heightIn(min=height).testTag("finish").semantics { contentDescription = finishDescription }, shape = shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    DockContent(WorkoutGlyph.STOP, if(compactLabels && !state.controlOnly) stringResource(R.string.finish_compact) else finishDescription, large)
                }
                if (floating) OutlinedButton(onClick = minimize, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min=height).testTag("float").semantics { contentDescription = minimizeDescription }, shape = shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    DockContent(WorkoutGlyph.FLOAT, stringResource(R.string.float_short), large)
                }
            }
        }
    }
}
@Composable private fun RowScope.DockContent(glyph: WorkoutGlyph, label: String, large: Boolean) {
    if(LocalDensity.current.fontScale<1.2f) { WorkoutIcon(glyph,Modifier.size(if(large) 24.dp else 16.dp)); Spacer(Modifier.width(if(large) 10.dp else 4.dp)) }
    Text(label,Modifier.weight(1f,false).padding(vertical=6.dp),style=if(large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable private fun InstrumentPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, content = content)
}
@Composable internal fun NumberText(value: String, maxSp: Int, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.SemiBold, color: Color = MaterialTheme.colorScheme.onSurface) {
    BasicText(value, modifier, maxLines = 1, style = TextStyle(color = color, fontWeight = weight, fontFeatureSettings = "tnum", letterSpacing = (-.5).sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = maxSp.sp, stepSize = 1.sp))
}
private fun workoutClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds < 3600) "%02d:%02d".format(seconds / 60, seconds % 60) else WorkoutService.elapsed(ms)
}

@Composable private fun TrainingProgress(c: Controller, state: ExerciseState, modifier: Modifier, spacious: Boolean, dense: Boolean, stages: () -> Unit) {
    val context = LocalContext.current
    val stageDescription = stringResource(R.string.stage_overview)
    val workout = state.selected
    InstrumentPanel(modifier.testTag("training-progress")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val broad = spacious && maxWidth >= 500.dp
            // The larger stage cards need room for both timer and remaining time.
            // A small tablet can be wide enough while its progress panel is short.
            val large = broad && maxHeight >= 500.dp + ((LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * 180).dp
            val showAllStages = spacious && maxHeight >= 400.dp
            val pad = if (large) 18.dp else if (dense) 8.dp else 10.dp
            Column(Modifier.padding(pad).then(if (spacious) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.spacedBy(if (large) 10.dp else if (dense) 4.dp else 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (state.paused) R.string.paused else R.string.workout_progress), Modifier.weight(1f), fontSize = if (large) 17.sp else 13.sp, lineHeight = if (large) 22.sp else 18.sp)
                    if (large) Text(workout?.let { workoutTitle(it) } ?: stringResource(R.string.free_training), Modifier.widthIn(max = 210.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth().then(if (spacious) Modifier.weight(1f) else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 24.dp else 12.dp)) {
                    Column(Modifier.weight(if (large) 2.1f else 1.9f).then(if (spacious) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.spacedBy(if (large) 4.dp else 0.dp, Alignment.CenterVertically)) {
                        NumberText(workoutClock(state.session?.elapsedMs ?: 0), if (large) 152 else if (broad) 132 else if (spacious) 96 else if (dense) 42 else 60, Modifier.fillMaxWidth().then(if (spacious) Modifier.weight(1f, false) else Modifier), FontWeight.Bold)
                        if (!dense || workout != null) {
                            Text(stringResource(R.string.current_target), fontSize = if (large) 16.sp else 11.sp, lineHeight = if (large) 20.sp else 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicText(if (state.done) stringResource(R.string.completed) else if (state.automatic || c.displayMachine() in setOf(Machine.ROWER,Machine.TREADMILL)) workout?.steps?.getOrNull(state.stage)?.let { stageTarget(it, c) } ?: stringResource(R.string.manual) else stringResource(R.string.manual), maxLines = 1,
                                style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (large) 30.sp else if (dense) 16.sp else 20.sp, stepSize = 1.sp))
                        }
                    }
                    if (workout != null) {
                        VerticalDivider(Modifier.height(if (large) 180.dp else if (dense) 68.dp else 92.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (large) 16.dp else 6.dp)) {
                            Column {
                                Text(stringResource(R.string.stage_label), fontSize = if (large) 16.sp else 11.sp, lineHeight = if (large) 20.sp else 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                NumberText("${(state.stage + 1).coerceAtMost(workout.steps.size)} / ${workout.steps.size}", if (large) 52 else if (broad) 40 else if (dense) 22 else 28)
                            }
                            Column {
                                Text(stringResource(if (state.remainingMs != null) R.string.stage_remaining else R.string.stage_progress), fontSize = if (large) 14.sp else 10.sp, lineHeight = if (large) 18.sp else 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                NumberText(if (state.done) stringResource(R.string.completed) else state.remainingMs?.let { context.minutesSeconds(it) } ?: "${(state.progress * 100).toInt()}%", if (large) 36 else if (broad) 30 else if (dense) 18 else 24)
                            }
                        }
                    }
                }
                if (workout != null) {
                    Column(Modifier.fillMaxWidth().testTag("stage-preview").clickable(onClick = stages).semantics { contentDescription = stageDescription }, verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else if (dense) 4.dp else 6.dp)) {
                        StageTimeline(state, large, dense)
                        val first = (state.stage - 1).coerceIn(0, (workout.steps.size - 3).coerceAtLeast(0))
                        Row(horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else 5.dp)) {
                            (first until minOf(first + 3, workout.steps.size)).forEach { index ->
                                StagePreview(workout.steps[index], index, state.stage, state.done, c, Modifier.weight(1f), large, dense)
                            }
                        }
                        if (showAllStages && !dense) Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = Color.Transparent) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                WorkoutIcon(WorkoutGlyph.LIST, Modifier.size(22.dp)); Spacer(Modifier.width(14.dp))
                                Text(stringResource(R.string.view_all_stages, workout.steps.size), Modifier.weight(1f), fontSize = 16.sp)
                                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(22.dp))
                            }
                        }
                    }
                } else Text(stringResource(R.string.free_training_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun StageTimeline(state: ExerciseState, large: Boolean, dense: Boolean) {
    val steps = state.selected?.steps ?: return
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(if (large) 6.dp else 3.dp)) {
        Canvas(Modifier.fillMaxWidth().height(if (large) 14.dp else 8.dp).clip(RoundedCornerShape(4.dp))) {
            val cell = size.width / steps.size
            steps.forEachIndexed { index, _ ->
                val color = if (state.done || index < state.stage) primary else if (index == state.stage) primary.copy(alpha = .75f) else muted
                drawRect(color, Offset(index * cell, 0f), Size((cell - 2.dp.toPx()).coerceAtLeast(1f), size.height))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            steps.indices.forEach { index ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Keep the full strip; label a sparse set when there are many short stages.
                    val stride = if (steps.size > 16 && !large) 4 else if (steps.size > 24) 2 else 1
                    Text(if (index == 0 || index == steps.lastIndex || index == state.stage || (index + 1) % stride == 0) "${index + 1}" else "", fontSize = if (large) 11.sp else if (dense) 8.sp else 9.sp, lineHeight = if (large) 14.sp else 11.sp, fontWeight = if (index == state.stage) FontWeight.Bold else FontWeight.Normal, color = if (index == state.stage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
@Composable private fun stageTarget(step: Step, c: Controller): String {
    val imperial by c.imperial.collectAsStateWithLifecycle()
    step.speedTargetMps?.let { speed ->
        val text=WorkoutService.number(speed*if(imperial) 2.236936292 else 3.6)+if(imperial) " mph" else " km/h"
        return text+(step.inclineTargetPercent?.let { " · ${WorkoutService.number(it)}%" } ?: "")
    }
    step.resistancePercent?.let { percent ->
        return c.range()?.let { range -> val level=range.percent(percent); stringResource(R.string.stage_level,WorkoutService.number(level),range.percentage(level)) } ?: stringResource(R.string.stage_target,percent)
    }
    val range=step.frequency.resolve(c.state.value.selected?.hints?.frequency ?: PersonalRange())
    if(range.enabled) return LocalContext.current.rangeText(range,if(c.displayMachine()==Machine.ROWER) "spm" else "rpm")
    return stringResource(stageKindLabel(step.kind))
}
@Composable private fun stepDuration(step: Step): String = when (step.condition) {
    Condition.TIME -> workoutClock((step.target * 1000).toLong())
    Condition.DISTANCE -> "${step.target.toInt()} m"
    Condition.STROKES -> "${step.target.toInt()} ${stringResource(R.string.strokes)}"
}
@Composable private fun StagePreview(step: Step, index: Int, current: Int, done: Boolean, c: Controller, modifier: Modifier, large: Boolean, dense: Boolean = false) {
    val active = index == current && !done
    Surface(modifier, shape = RoundedCornerShape(7.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = .48f) else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(if (large) 12.dp else 7.dp), verticalArrangement = Arrangement.spacedBy(if (large) 4.dp else 1.dp)) {
            if (large) Text(stringResource(if (active) R.string.stage_now else if (index < current) R.string.stage_previous else R.string.stage_next), fontSize = 14.sp, lineHeight = 18.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (large || dense) {
                    NumberText("${index + 1}", if (large) 28 else 16); Spacer(Modifier.width(if (large) 12.dp else 6.dp))
                    NumberText(stepDuration(step), if (large) 19 else 13, Modifier.weight(1f))
                } else Column(Modifier.weight(1f)) {
                    NumberText("${index + 1}", if (dense) 15 else 18)
                    NumberText(stepDuration(step), if (dense) 13 else 15, weight = FontWeight.Medium)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(if (large) 20.dp else 15.dp))
            }
            BasicText(stageTarget(step, c), modifier=Modifier.testTag("stage-preview-target"),maxLines = if(LocalDensity.current.fontScale>1.3f) 2 else 1, style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant), autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = if (large) 14.sp else 11.sp, stepSize = 1.sp))
        }
    }
}

@Composable private fun MetricPages(c: Controller, state: ExerciseState, modifier: Modifier, spacious: Boolean, dense: Boolean) {
    val selections by c.display.selections.collectAsStateWithLifecycle()
    val machineSelections by c.display.byMachine.collectAsStateWithLifecycle()
    val link by c.ble.state.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chosen = remember(selections,machineSelections,link.machine,state.demo,state.demoMachine,state.session?.machine,state.controlOnly) { c.display.selected(DisplayScope.TRAINING,c.displayMachine(),state.controlOnly) }
    InstrumentPanel(modifier) {
        BoxWithConstraints {
            val columns = if(maxWidth/LocalDensity.current.fontScale<260.dp) 1 else if(maxWidth/LocalDensity.current.fontScale>=420.dp) 3 else 2
            val pageSize = if(spacious && !dense && columns==2) 6 else columns * 2
            val pages = chosen.chunked(pageSize)
            val pager = rememberPagerState { pages.size }
            val scope = rememberCoroutineScope()
            val rows = (minOf(chosen.size, pageSize) + columns - 1) / columns
            // Android scales large and small text differently. Multiplying the entire
            // row by fontScale leaves excessive blank space at accessibility sizes.
            // Reserve two label lines plus the actual scaled reading and its padding.
            val textHeight = with(LocalDensity.current) {
                (if(spacious) 36f else 34f).times(1.15f).sp.toDp() +
                    (if(dense) 18 else 20).sp.toDp() * 2
            } + (if(dense) 18 else 22).dp
            val rowHeight = maxOf((if (dense) 96 else if(spacious) 112 else 104).dp, textHeight)
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = if (dense) 24.dp else if (spacious) 40.dp else 30.dp).padding(start = if (spacious) 18.dp else 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workout_metrics), Modifier.weight(1f), fontSize = if (spacious) 17.sp else 13.sp, lineHeight = if (spacious) 22.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowLeft, stringResource(R.string.previous_metrics), Modifier.size(18.dp)) }
                            Text("${pager.currentPage + 1} / ${pages.size}", modifier=Modifier.testTag("metric-page-count"),fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex)) } }, enabled = pager.currentPage < pages.lastIndex, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowRight, stringResource(R.string.next_metrics), Modifier.size(18.dp)) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HorizontalPager(state = pager, modifier = Modifier.height(rowHeight * rows), verticalAlignment = Alignment.Top) { page ->
                    Column(Modifier.fillMaxSize()) {
                        repeat(rows) { row ->
                            if (row > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().weight(1f)) { repeat(columns) { column ->
                                if (column > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val id = pages[page].getOrNull(row * columns + column)
                                if (id != null) key(id, state.session?.id) { MetricTile(remember(id, state, imperial) { context.reading(id, c) }, Modifier.weight(1f).fillMaxHeight().testTag("live-metric-${id.name}"), spacious, dense) } else Spacer(Modifier.weight(1f))
                            } }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(28.dp).testTag("metric-page-indicators"),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                    repeat(pages.size) { index -> Box(Modifier.size(5.dp).background(if(index==pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,CircleShape)) }
                }

            }
        }
    }
}
@Composable private fun MetricTile(r: MetricReading, modifier: Modifier, spacious: Boolean, dense: Boolean) {
    Column(modifier.padding(horizontal=12.dp,vertical=if(dense) 6.dp else 8.dp),verticalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterVertically)) {
        Text(r.label+(if(r.estimated) " ≈" else ""),style=MaterialTheme.typography.bodyMedium,lineHeight=if(dense) 18.sp else 20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        MetricValueLine(r.value,r.unit,if(spacious) 36 else 34)
    }
}

@Composable internal fun ResistanceControls(activity: MainActivity, c: Controller, state: ExerciseState, link: LinkState, modifier: Modifier, spacious: Boolean, dense: Boolean,editPresets: ()->Unit) {
    val range = c.range()
    val machine=c.displayMachine()
    if(range==null && machine in setOf(Machine.ROWER,Machine.TREADMILL)) {
        InstrumentPanel(modifier) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.current_target),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                state.selected?.steps?.getOrNull(state.stage)?.let { Text(stageTarget(it,c),style=MaterialTheme.typography.headlineSmall) }
                Text(stringResource(if(machine==Machine.ROWER) R.string.plan_rower_help else R.string.plan_treadmill_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val can = !state.starting && (state.demo && range!=null || (link.writable && range != null && c.controlResistance() != null && !link.busy && link.phase == "ready"))
    var sliding by remember { mutableStateOf<Float?>(null) }
    var help by remember { mutableStateOf(false) }
    val pending = state.pendingResistance ?: link.requested
    val automatic = state.automatic && state.selected != null && !state.done
    InstrumentPanel(modifier) {
        Column(Modifier.padding(horizontal = if (spacious) 18.dp else 12.dp, vertical = if (spacious) 10.dp else 4.dp), verticalArrangement = Arrangement.Top) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if(link.resistanceFeedback || state.demo) R.string.resistance_control else R.string.resistance_commanded), Modifier.align(Alignment.CenterVertically).padding(end=8.dp), fontSize=if(spacious) 17.sp else 15.sp, fontWeight=FontWeight.Medium)
                BoxWithConstraints {
                val labelWidth=(maxWidth-128.dp).coerceAtLeast(32.dp)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(state.selected!=null && !state.done) Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            BasicText(stringResource(R.string.automatic_mode),style=MaterialTheme.typography.labelMedium.copy(color=MaterialTheme.colorScheme.onPrimaryContainer),modifier=Modifier.widthIn(max=labelWidth).testTag("automatic-mode-label"),maxLines=1,autoSize=TextAutoSize.StepBased(10.sp,12.sp,1.sp))
                            Switch(checked=automatic,onCheckedChange=c::automaticControl,enabled=can,modifier=Modifier.width(52.dp).heightIn(min=48.dp).semantics { contentDescription=activity.getString(R.string.auto) })
                        }
                    }
                    IconButton(onClick={ help=true },modifier=Modifier.size(48.dp)) { Icon(Icons.Default.Info,stringResource(if(link.resistanceFeedback) R.string.resistance_feedback_help else R.string.resistance_commanded_help),Modifier.size(18.dp)) }
                }
                }
            }
            if (dense && range != null && range.max > range.min) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ResistanceButton(false, can, false, activity) { c.adjust(-1) }
                    Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        NumberText(WorkoutService.number(c.controlResistance()), 20)
                        Text(c.controlResistance()?.let { "${range.percentage(it)}%" } ?: "—", fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        val value = (sliding ?: (pending ?: c.controlResistance() ?: range.min).toFloat()).coerceIn(range.min.toFloat(), range.max.toFloat())
                        ResistanceSlider(range, value, can, false, change = { sliding = it }, finish = { sliding?.let { c.adjustTo(it.toDouble()) }; sliding = null })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(WorkoutService.number(range.min), fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(WorkoutService.number(range.max), fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ResistanceButton(true, can, false, activity) { c.adjust(1) }
                }
            } else {
            Row(Modifier.fillMaxWidth().heightIn(min = if (spacious) 82.dp else if (dense) 52.dp else 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                ResistanceButton(false, can, spacious, activity) { c.adjust(-1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(stringResource(R.string.resistance_level, WorkoutService.number(c.controlResistance())), Modifier.padding(horizontal = 6.dp), maxLines = 1,
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = if (spacious) 38.sp else 32.sp, stepSize = 1.sp))
                    Text((if(LocalDensity.current.fontScale<=1.3f) stringResource(if(link.resistanceFeedback || state.demo) R.string.actual_resistance else R.string.set_resistance)+" · " else "")+(c.controlResistance()?.let { range?.percentage(it)?.let { p -> "$p%" } } ?: "—"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                ResistanceButton(true, can, spacious, activity) { c.adjust(1) }
            }
            if(LocalDensity.current.fontScale>1.3f) Text(stringResource(if(link.resistanceFeedback || state.demo) R.string.actual_resistance else R.string.set_resistance),Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if (range != null && range.max > range.min) {
                val value = (sliding ?: (pending ?: c.controlResistance() ?: range.min).toFloat()).coerceIn(range.min.toFloat(), range.max.toFloat())
                ResistanceSlider(range, value, can, spacious, change = { sliding = it }, finish = { sliding?.let { c.adjustTo(it.toDouble()) }; sliding = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(WorkoutService.number(range.min), fontSize = if (spacious) 12.sp else 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val previewTarget = sliding?.let { range.next(it.toDouble(), 0) }
                    Text(previewTarget?.let { stringResource(R.string.slider_target, WorkoutService.number(it), range.percentage(it)) } ?: "", fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(WorkoutService.number(range.max), fontSize = if (spacious) 12.sp else 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
            if (link.controlTimedOut) Text(stringResource(R.string.control_no_feedback), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (!can || pending != null) Text(if (pending != null) stringResource(R.string.control_pending, WorkoutService.number(pending)) else stringResource(R.string.control_unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResistancePresets(c,state,link,editPresets)
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.resistance_control)) }, text = { Text(stringResource(if(link.resistanceFeedback) R.string.resistance_feedback_help else R.string.resistance_commanded_help)) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.close)) } })
}
@Composable private fun ResistanceSlider(range: ResistanceRange, value: Float, enabled: Boolean, spacious: Boolean, modifier: Modifier = Modifier, change: (Float) -> Unit, finish: () -> Unit) {
    val label = stringResource(R.string.resistance_slider)
    val fraction = ((value - range.min) / (range.max - range.min)).toFloat()
    val segments = (((range.max - range.min) / range.increment).toInt() + 1).coerceIn(2, 100)
    Slider(value = value, onValueChange = change, onValueChangeFinished = finish, valueRange = range.min.toFloat()..range.max.toFloat(), steps = (segments - 2).coerceAtLeast(0), enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min=48.dp).semantics { contentDescription = label },
        thumb = {
            Box(Modifier.size(if (spacious) 30.dp else 24.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(4.dp).background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }, track = {
            val blue = MaterialTheme.colorScheme.primary
            val gray = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(if (spacious) 14.dp else 8.dp).clip(RoundedCornerShape(2.dp))) {
                val cell = size.width / segments
                repeat(segments) { index -> drawRect(if (enabled && index.toFloat() / (segments - 1) <= fraction) blue else gray, Offset(index * cell, 0f), Size((cell - 2.dp.toPx()).coerceAtLeast(1f), size.height)) }
            }
        })
}
@Composable private fun ResistanceButton(increase: Boolean, enabled: Boolean, spacious: Boolean, activity: MainActivity, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.size(if (spacious) 76.dp else 48.dp).semantics { contentDescription = activity.getString(if (increase) R.string.increase else R.string.decrease) }) {
        Box(Modifier.size(if (spacious) 66.dp else 40.dp).background(if (increase && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (increase) "+" else "−", fontSize = if (spacious) 36.sp else 26.sp, fontWeight = FontWeight.Light, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f) else if (increase) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
@Composable private fun StageDialog(state: ExerciseState, c: Controller, close: () -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (state.stage - 1).coerceAtLeast(0))
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.stage_overview)) }, text = {
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.selected?.steps.orEmpty().size) { index -> StagePreview(state.selected!!.steps[index], index, state.stage, state.done, c, Modifier.fillMaxWidth(), true) }
        }
    }, confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } })
}
@Composable internal fun MetricPicker(c: Controller,initialScope: DisplayScope=DisplayScope.TRAINING,close: ()->Unit) {
    val state by c.state.collectAsStateWithLifecycle()
    val link by c.ble.state.collectAsStateWithLifecycle()
    val machine=if(state.demo) state.demoMachine else link.machine.takeIf { it!=Machine.UNKNOWN } ?: state.session?.machine ?: Machine.UNKNOWN
    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }
    val listState=remember(machine,selectedScope) { LazyListState() }
    val draft=remember(machine) { mutableStateMapOf<DisplayScope,List<MetricId>>().apply { DisplayScope.entries.forEach { put(it,c.display.selected(it,machine,state.controlOnly,freeRecording=state.session!=null && state.selected==null)) } } }
    val labels=listOf(R.string.training_screen,R.string.compact_panel,R.string.expanded_panel)
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).fillMaxHeight(.9f).testTag("metric-picker"),shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.choose_metrics),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                val chosen=draft.getValue(selectedScope)
                LazyColumn(Modifier.weight(1f).testTag("metric-choices"),state=listState) {
                    item(key="context") {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(machineName(machine),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { DisplayScope.entries.forEachIndexed { i,scope ->
                                FilterChip(selectedScope==scope,{ selectedScope=scope },label={ Text(stringResource(labels[i]),style=MaterialTheme.typography.labelMedium) })
                            } }
                            Text(stringResource(R.string.metric_selection_help,selectedScope.limit),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(chosen,key={ it.name }) { id -> val i=chosen.indexOf(id)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(true,{ if(chosen.size>1) draft[selectedScope]=chosen-id },enabled=chosen.size>1)
                            Text(stringResource(metricLabel(id)),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            IconButton(onClick={ draft[selectedScope]=chosen.toMutableList().apply { add(i-1,removeAt(i)) } },enabled=i>0) { Icon(Icons.Default.KeyboardArrowUp,stringResource(R.string.move_up)) }
                            IconButton(onClick={ draft[selectedScope]=chosen.toMutableList().apply { add(i+1,removeAt(i)) } },enabled=i<chosen.lastIndex) { Icon(Icons.Default.KeyboardArrowDown,stringResource(R.string.move_down)) }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical=8.dp)) }
                    items(MetricCatalog.forMachine(machine).filter { it !in chosen },key={ it.name }) { id ->
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=chosen.size<selectedScope.limit) { draft[selectedScope]=chosen+id },verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(false,{ draft[selectedScope]=chosen+id },enabled=chosen.size<selectedScope.limit)
                            Text(stringResource(metricLabel(id)),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=close) { Text(stringResource(R.string.cancel)) }
                    Button(onClick={ draft.forEach { (scope,values) -> c.display.save(scope,values,machine) }; close() }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
@Composable internal fun EstimateDialog(c: Controller,close: ()->Unit) {
    var mass by remember { mutableStateOf(WorkoutService.number(c.display.weight.value)) }
    var met by remember { mutableStateOf(WorkoutService.number(c.display.met.value)) }
    var cadence by remember { mutableStateOf(c.display.targetCadence.value.toString()) }; var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=close,title={ Text(stringResource(R.string.estimation_settings)) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.estimate_help),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(mass,{ mass=it },label={ Text(stringResource(R.string.body_mass)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            OutlinedTextField(met,{ met=it },label={ Text(stringResource(R.string.estimate_met)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            OutlinedTextField(cadence,{ cadence=it },label={ Text(stringResource(R.string.target_cadence)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(invalid) Text(stringResource(R.string.invalid_input),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={ TextButton(onClick={ runCatching { c.display.estimates(mass.replace(',','.').toDouble(),met.replace(',','.').toDouble(),cadence.toInt()) }.onSuccess { close() }.onFailure { invalid=true } }) { Text(stringResource(R.string.save)) } },dismissButton={ TextButton(onClick=close) { Text(stringResource(R.string.cancel)) } })
}
