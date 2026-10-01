@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

internal val brandColors=listOf(Color(0xFFECA2C5),Color(0xFFDF535D),Color(0xFFF0C84B),Color(0xFF5888DB))
@Composable internal fun BrandSignature(modifier: Modifier=Modifier) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        brandColors.forEach { color -> Surface(color=color,shape=RoundedCornerShape(3.dp),modifier=Modifier.width(16.dp).height(5.dp)) {} }
    }
}
@Composable internal fun TrainingScreen(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    var picker by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }; var stages by remember { mutableStateOf(false) }
    val focused by activity.focusTraining
    BackHandler(focused) { activity.focusTraining.value=false; activity.page.value=1 }
    val fold by produceState<FoldingFeature?>(null,activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info -> value=info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating || it.state==FoldingFeature.State.HALF_OPENED } }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ activity.focusTraining.value=false; activity.page.value=1 }) { Icon(Icons.Default.ArrowBack,stringResource(R.string.back_to_app)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.training_screen),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(state.demo) Text(stringResource(R.string.demo),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={ picker=true },contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.semantics { contentDescription=activity.getString(R.string.choose_metrics) }) {
                Icon(Icons.Default.List,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.configure_display))
            }
            Box {
                IconButton(onClick={ menu=true }) { Icon(Icons.Default.MoreVert,stringResource(R.string.more_actions)) }
                DropdownMenu(menu,{ menu=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.choose_metrics)) },onClick={ menu=false; picker=true })
                    DropdownMenuItem(text={ Text(stringResource(R.string.stage_overview)) },onClick={ menu=false; stages=true },enabled=state.selected!=null)
                    DropdownMenuItem(text={ Text(stringResource(R.string.finish),color=MaterialTheme.colorScheme.error) },onClick={ menu=false; finish=true })
                }
            }
        }
        state.error?.let { id -> Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(state.errorRow!=null) stringResource(id,state.errorRow) else stringResource(id),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={ c.error(null) }) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
            }
        } }
        var origin by remember { mutableStateOf(Offset.Zero) }; val density=LocalDensity.current
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { origin=it.positionInWindow() }) {
            val feature=fold
            val splitX=with(density) { ((feature?.bounds?.left ?: 0)-origin.x).toDp() }
            val splitY=with(density) { ((feature?.bounds?.top ?: 0)-origin.y).toDp() }
            val gapX=with(density) { (feature?.bounds?.width() ?: 0).toDp() }
            val gapY=with(density) { (feature?.bounds?.height() ?: 0).toDp() }
            val vertical=feature?.orientation==FoldingFeature.Orientation.VERTICAL && splitX>=200.dp && maxWidth-splitX-gapX>=200.dp
            val horizontal=feature?.orientation==FoldingFeature.Orientation.HORIZONTAL && splitY>=120.dp && maxHeight-splitY-gapY>=170.dp
            val shortWindow=maxHeight<420.dp
            val split=vertical || maxWidth>=840.dp || (maxWidth>=560.dp && maxHeight<420.dp)
            when {
                horizontal -> Column(Modifier.fillMaxSize()) {
                    MetricPages(c,state,Modifier.fillMaxWidth().height(splitY).padding(12.dp))
                    Spacer(Modifier.height(gapY))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        TrainingClock(state) { stages=true }; TrainingControls(activity,c,state,link)
                    }
                }
                split -> Row(Modifier.fillMaxSize()) {
                    Column((if(vertical) Modifier.width(splitX) else Modifier.weight(1.6f)).fillMaxHeight().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        TrainingClock(state,compact=shortWindow) { stages=true }; MetricPages(c,state,Modifier.weight(1f))
                    }
                    if(vertical) Spacer(Modifier.width(gapX))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        if(!shortWindow) {
                            BrandSignature(Modifier.padding(vertical=8.dp))
                            Text(state.selected?.let { workoutTitle(it) } ?: stringResource(R.string.free_training),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                        }
                        TrainingControls(activity,c,state,link)
                        if(!shortWindow) {
                            Text(stringResource(R.string.estimated_values),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick={ finish=true }) { Text(stringResource(R.string.finish)) }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(horizontal=16.dp).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    TrainingClock(state) { stages=true }
                    MetricPages(c,state,Modifier.weight(1f))
                    TrainingControls(activity,c,state,link)
                }
            }
        }
    }
    if(picker) MetricPicker(c) { picker=false }
    if(stages) StageDialog(state) { stages=false }
    if(finish) AlertDialog(onDismissRequest={ finish=false },title={ Text(stringResource(R.string.finish)) },text={ Text(stringResource(R.string.finish_note)) },confirmButton={ TextButton(onClick={ c.finish(); finish=false; activity.focusTraining.value=false }) { Text(stringResource(R.string.finish)) } },dismissButton={ TextButton(onClick={ finish=false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun TrainingClock(state: ExerciseState,compact: Boolean=false,stages: ()->Unit) {
    val context=LocalContext.current
    Surface(color=Color(0xFF191C22),contentColor=Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().clickable(enabled=state.selected!=null,onClick=stages)) {
        Column(Modifier.padding(horizontal=20.dp,vertical=if(compact) 10.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if(!compact) Text(stringResource(if(state.paused) R.string.paused else R.string.duration),style=MaterialTheme.typography.labelMedium,color=Color(0xFFC2C7CF))
                    BasicText(WorkoutService.elapsed(state.session?.elapsedMs ?: 0),style=LocalTextStyle.current.copy(color=Color.White,fontWeight=FontWeight.Medium,letterSpacing=1.sp),maxLines=1,
                        autoSize=TextAutoSize.StepBased(minFontSize=24.sp,maxFontSize=if(compact) 30.sp else 42.sp,stepSize=1.sp))
                }
                BrandSignature(Modifier.padding(start=8.dp))
            }
            state.selected?.let { workout ->
                Row(Modifier.fillMaxWidth().heightIn(min=if(compact) 36.dp else 48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if(state.done) stringResource(R.string.completed) else stringResource(R.string.stage_format,state.stage+1,workout.steps.size),style=MaterialTheme.typography.labelMedium,color=Color(0xFFC2C7CF))
                        val target=workout.steps[state.stage].resistancePercent
                        Text(if(state.done) stringResource(R.string.free_training) else stringResource(R.string.stage_target,target ?: 0),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Text(state.remainingMs?.let { context.minutesSeconds(it) } ?: "${(state.progress*100).toInt()}%",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.stage_overview),Modifier.size(20.dp))
                }
                LinearProgressIndicator(progress={ state.progress },modifier=Modifier.fillMaxWidth().height(3.dp),color=Color(0xFF8EAFE9),trackColor=Color(0xFF3D424D))
            }
        }
    }
}
@Composable private fun MetricPages(c: Controller,state: ExerciseState,modifier: Modifier) {
    val selections by c.display.selections.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val chosen=selections.getValue(DisplayScope.TRAINING)
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns=if(maxWidth>=560.dp) 3 else 2
        val rows=((maxHeight.value-48)/(88*fontScale)).toInt().coerceIn(1,3).coerceAtMost((chosen.size+columns-1)/columns)
        val count=columns*rows; val pages=chosen.chunked(count)
        val pager=rememberPagerState { pages.size }
        val scope=rememberCoroutineScope()
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager,modifier=Modifier.weight(1f),pageSpacing=12.dp,key={ it }) { p ->
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    repeat(rows) { row -> Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        repeat(columns) { col ->
                            val id=pages.getOrNull(p)?.getOrNull(row*columns+col)
                            if(id!=null) { // State keys intentionally invalidate all readings each sample.
                                val reading=remember(id,state,imperial) { context.reading(id,c) }
                                MetricTile(reading,Modifier.weight(1f).fillMaxHeight())
                            } else Spacer(Modifier.weight(1f))
                        }
                    } }
                }
            }
            if(pages.size>1) Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                // A single page button also makes paging accessible without a swipe gesture.
                TextButton(onClick={ scope.launch { pager.animateScrollToPage((pager.currentPage+1)%pages.size) } },contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp),modifier=Modifier.height(48.dp)) {
                    Text(stringResource(R.string.metric_page,pager.currentPage+1,pages.size),style=MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp))
                }
            }
        }
    }
}
@Composable private fun MetricTile(r: MetricReading,modifier: Modifier) {
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
        BoxWithConstraints {
        val numberSize=if(maxWidth>=170.dp && maxHeight>=130.dp) 48.sp else 32.sp
        Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.SpaceBetween) {
            BasicText(r.label,style=MaterialTheme.typography.labelMedium.copy(color=MaterialTheme.colorScheme.onSurfaceVariant),maxLines=2,
                autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=12.sp,stepSize=1.sp))
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                BasicText(r.value,modifier=Modifier.weight(1f,false),style=MaterialTheme.typography.headlineMedium.copy(color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold),maxLines=1,
                    autoSize=TextAutoSize.StepBased(minFontSize=16.sp,maxFontSize=numberSize,stepSize=1.sp))
                if(r.unit.isNotEmpty()) Text(r.unit,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
        }
        }
    }
}
@Composable private fun TrainingControls(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val can=state.demo || (link.writable && link.range!=null && link.metrics.resistance!=null && !link.busy && link.phase=="ready")
    val range=c.range()
    var sliding by remember { mutableStateOf<Float?>(null) }
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick={ c.adjust(-1) },enabled=can,modifier=Modifier.size(52.dp).semantics { contentDescription=activity.getString(R.string.decrease) },contentPadding=PaddingValues(0.dp)) { Text("−",fontSize=28.sp) }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.resistance),style=MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(WorkoutService.number(state.metrics.resistance),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                        Text(state.metrics.resistance?.let { c.range()?.percentage(it)?.let { percent -> "$percent%" } } ?: "—",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick={ c.adjust(1) },enabled=can,modifier=Modifier.size(52.dp).semantics { contentDescription=activity.getString(R.string.increase) },contentPadding=PaddingValues(0.dp)) { Text("+",fontSize=28.sp) }
            }
            if(range!=null && range.max>range.min) {
                val shown=range.next((sliding?.toDouble() ?: link.requested ?: state.metrics.resistance ?: range.min),0)
                Column {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.resistance_slider),style=MaterialTheme.typography.labelSmall)
                        Text(if(sliding!=null) stringResource(R.string.slider_target,WorkoutService.number(shown),range.percentage(shown)) else "${WorkoutService.number(range.min)}–${WorkoutService.number(range.max)}",style=MaterialTheme.typography.labelSmall)
                    }
                    Slider(value=(sliding ?: (link.requested ?: state.metrics.resistance ?: range.min).toFloat()).coerceIn(range.min.toFloat(),range.max.toFloat()),
                        onValueChange={ sliding=it },onValueChangeFinished={ sliding?.let { c.adjustTo(it.toDouble()) }; sliding=null },
                        valueRange=range.min.toFloat()..range.max.toFloat(),steps=(((range.max-range.min)/range.increment).toInt()-1).coerceIn(0,100),
                        enabled=can,modifier=Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription=activity.getString(R.string.resistance_slider) })
                }
            }
            if(link.controlTimedOut) Text(stringResource(R.string.control_no_feedback),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
            if(!can || link.requested!=null) Text(if(link.requested!=null) stringResource(R.string.control_pending,WorkoutService.number(link.requested)) else stringResource(R.string.control_unavailable),style=MaterialTheme.typography.labelSmall,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.selected!=null && !state.automatic) TextButton(onClick=c::resumeAutomatic,enabled=can,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)) { Text(stringResource(R.string.restore_auto),style=MaterialTheme.typography.labelMedium) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=c::pauseResume,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)) { Text(stringResource(if(state.paused) R.string.resume else R.string.pause),maxLines=2) }
                OutlinedButton(onClick=activity::minimize,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)) { Text(stringResource(R.string.minimize),maxLines=2) }
            }
        }
    }
}
@Composable private fun StageDialog(state: ExerciseState,close: ()->Unit) {
    val context=LocalContext.current
    AlertDialog(onDismissRequest=close,title={ Text(stringResource(R.string.stage_overview)) },text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(state.selected?.steps.orEmpty().size) { i -> val s=state.selected!!.steps[i]
                Surface(color=if(i==state.stage) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("${i+1}"); Text(when(s.condition) { Condition.TIME -> context.minutesSeconds((s.target*1000).toLong()); Condition.DISTANCE -> "${s.target.toInt()} m"; Condition.STROKES -> "${s.target.toInt()} ${stringResource(R.string.strokes)}" },Modifier.weight(1f)); Text("${s.resistancePercent ?: 0}%")
                    }
                }
            }
        }
    },confirmButton={ TextButton(onClick=close) { Text(stringResource(R.string.close)) } })
}
@Composable internal fun MetricPicker(c: Controller,initialScope: DisplayScope=DisplayScope.TRAINING,close: ()->Unit) {
    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }
    val draft=remember { mutableStateMapOf<DisplayScope,List<MetricId>>().apply { putAll(c.display.selections.value) } }
    val labels=listOf(R.string.training_screen,R.string.compact_panel,R.string.expanded_panel)
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).fillMaxHeight(.9f),shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.choose_metrics),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { DisplayScope.entries.forEachIndexed { i,scope ->
                    FilterChip(selectedScope==scope,{ selectedScope=scope },label={ Text(stringResource(labels[i]),style=MaterialTheme.typography.labelMedium) })
                } }
                Text(stringResource(R.string.metric_selection_help,selectedScope.limit),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val chosen=draft.getValue(selectedScope)
                LazyColumn(Modifier.weight(1f)) {
                    items(chosen,key={ it.name }) { id -> val i=chosen.indexOf(id)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(true,{ if(chosen.size>1) draft[selectedScope]=chosen-id },enabled=chosen.size>1)
                            Text(stringResource(metricLabel(id)),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            IconButton(onClick={ draft[selectedScope]=chosen.toMutableList().apply { add(i-1,removeAt(i)) } },enabled=i>0) { Icon(Icons.Default.KeyboardArrowUp,stringResource(R.string.move_up)) }
                            IconButton(onClick={ draft[selectedScope]=chosen.toMutableList().apply { add(i+1,removeAt(i)) } },enabled=i<chosen.lastIndex) { Icon(Icons.Default.KeyboardArrowDown,stringResource(R.string.move_down)) }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical=8.dp)) }
                    items(MetricId.entries.filter { it !in chosen },key={ it.name }) { id ->
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=chosen.size<selectedScope.limit) { draft[selectedScope]=chosen+id },verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(false,{ draft[selectedScope]=chosen+id },enabled=chosen.size<selectedScope.limit)
                            Text(stringResource(metricLabel(id)),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=close) { Text(stringResource(R.string.cancel)) }
                    Button(onClick={ draft.forEach { (scope,values) -> c.display.save(scope,values) }; close() }) { Text(stringResource(R.string.save)) }
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
