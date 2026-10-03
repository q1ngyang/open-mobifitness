@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

@Composable internal fun FreeDashboard(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    var picker by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var editPresets by rememberSaveable { mutableStateOf(false) }
    val floating by c.display.floatingEnabled.collectAsStateWithLifecycle()
    val focused by activity.focusTraining
    val fold by produceState<FoldingFeature?>(null,activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info -> value=info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating || it.state==FoldingFeature.State.HALF_OPENED } }
    }
    fun back() { activity.focusTraining.value=false; activity.page.value=2 }
    BackHandler(focused) { back() }
    DisposableEffect(activity) {
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("free-dashboard")) {
        val short=maxHeight<480.dp
        val wide=maxWidth>=700.dp && maxWidth>maxHeight || short && maxWidth>=520.dp
        val tablet=maxWidth>=600.dp && !short
        Column(Modifier.fillMaxSize()) {
            WorkoutHeader(state,link,tablet,short,::back) { picker=true }
            if(state.error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(state.error),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    IconButton(onClick={ c.error(null) }) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
                }
            }
            val metrics: @Composable ()->Unit = {
                ConnectionStrip(activity,c,state,link,short)
                LivePanel(c,state,short,tablet)
            }
            val controls: @Composable ()->Unit = { ResistanceControls(activity,c,state,link,Modifier.fillMaxWidth(),tablet,false) { editPresets=true } }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val density=LocalDensity.current
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { origin=it.positionInWindow() }) {
                val feature=fold
                val splitX=with(density) { ((feature?.bounds?.left ?: 0)-origin.x).toDp() }
                val splitY=with(density) { ((feature?.bounds?.top ?: 0)-origin.y).toDp() }
                val gapX=with(density) { (feature?.bounds?.width() ?: 0).toDp() }
                val gapY=with(density) { (feature?.bounds?.height() ?: 0).toDp() }
                val vertical=feature?.orientation==FoldingFeature.Orientation.VERTICAL && splitX>=240.dp && maxWidth-splitX-gapX>=240.dp
                val horizontal=feature?.orientation==FoldingFeature.Orientation.HORIZONTAL && splitY>=140.dp && maxHeight-splitY-gapY>=140.dp
                when {
                    horizontal -> Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxWidth().height(splitY).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { metrics() }
                        Spacer(Modifier.height(gapY))
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) { controls() }
                    }
                    wide || vertical -> Row(Modifier.fillMaxSize()) {
                        Column((if(vertical) Modifier.width(splitX) else Modifier.weight(1.35f)).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start=if(short) 8.dp else 16.dp,end=8.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(if(short) 6.dp else 12.dp)) { metrics() }
                        if(vertical) Spacer(Modifier.width(gapX))
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start=8.dp,end=if(short) 8.dp else 16.dp,bottom=12.dp)) { controls() }
                    }
                    else -> Column(Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max=720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { metrics(); controls(); Spacer(Modifier.height(2.dp)) }
                }
            }
            WorkoutDock(state,floating,tablet,short,c.canStart(),{ if(state.controlOnly) c.start() else c.pauseResume() },{ if(state.controlOnly) activity.exitControl() else finish=true },activity::minimize)
        }
    }
    if(picker) MetricPicker(c) { picker=false }
    if(editPresets) ResistancePresetEditor(c,state) { editPresets=false }
    if(finish) FormDialog(stringResource(R.string.finish_save),{ finish=false },{ c.finish(); finish=false },confirmLabel=stringResource(R.string.finish_save)) { Text(stringResource(R.string.finish_note)) }
}

@Composable private fun ConnectionStrip(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState,short: Boolean) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(!short) MachineGlyph(c.displayMachine(),Modifier.size(28.dp))
            Text(if(state.demo) stringResource(R.string.demo) else link.name.ifBlank { machineName(c.displayMachine()) },Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            if(!c.canStart()) TextButton(onClick=activity::reconnect) { Text(stringResource(R.string.reconnect)) }
            else Text(stringResource(R.string.connected),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Box {
                IconButton(onClick={ menu=true }) { Icon(Icons.Default.MoreVert,stringResource(R.string.devices)) }
                DropdownMenu(menu,{ menu=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.devices)) },onClick={ menu=false; activity.focusTraining.value=false; activity.page.value=2 })
                    if(state.controlOnly) DropdownMenuItem(text={ Text(stringResource(R.string.disconnect_exit)) },onClick={ menu=false; activity.exitControl() })
                }
            }
        }
    }
}

@Composable private fun LivePanel(c: Controller,state: ExerciseState,short: Boolean,spacious: Boolean) {
    val context=LocalContext.current
    val selections by c.display.selections.collectAsStateWithLifecycle()
    val machineSelections by c.display.byMachine.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val hintPrefs by c.local.hints.collectAsStateWithLifecycle()
    val machine=c.displayMachine()
    val chosen=remember(selections,machineSelections,machine,state.controlOnly) { c.display.selected(DisplayScope.TRAINING,machine,state.controlOnly,freeRecording=!state.controlOnly) }
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column {
            Column(Modifier.padding(horizontal=16.dp,vertical=if(short) 8.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Text(stringResource(if(state.controlOnly) R.string.control_only else if(state.paused) R.string.record_paused else R.string.recording),Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    }
                    if(hintPrefs.heart.enabled || hintPrefs.frequency[machine]?.enabled==true) TextButton(onClick=c::muteHints,modifier=Modifier.testTag("mute-hints")) {
                        Icon(if(state.hintsMuted) Icons.Default.Close else Icons.Default.Check,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(stringResource(if(state.hintsMuted) R.string.hint_unmute else R.string.hint_mute),style=MaterialTheme.typography.labelMedium)
                    }
                }
                if(state.controlOnly) Text(stringResource(R.string.control_only_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    BasicText(WorkoutService.elapsed(state.session!!.elapsedMs),Modifier.fillMaxWidth().testTag("record-time"),style=MaterialTheme.typography.displayMedium.copy(color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontFeatureSettings="tnum"),maxLines=1,autoSize=TextAutoSize.StepBased(26.sp,if(short) 36.sp else 48.sp,1.sp))
                    Text(stringResource(R.string.current_record),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(state.frequencyHint!=RangePosition.UNAVAILABLE || state.heartHint!=RangePosition.UNAVAILABLE) FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    HintLabel(R.string.hint_frequency,state.frequencyHint)
                    HintLabel(R.string.heart_rate,state.heartHint)
                }
            }
            val grid: @Composable (List<MetricId>)->Unit = { items -> BoxWithConstraints {
                val font=LocalDensity.current.fontScale
                val columns=if(maxWidth/font<260.dp) 1 else 2
                val measurer=rememberTextMeasurer()
                val tileWidth=with(LocalDensity.current) { (maxWidth/columns-24.dp).toPx() }
                val unitStyle=MaterialTheme.typography.bodySmall
                Column {
                    items.chunked(columns).forEachIndexed { row,group ->
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            group.forEachIndexed { i,id ->
                                if(i>0) VerticalDivider(Modifier.fillMaxHeight())
                                val r=remember(id,state,imperial) { context.reading(id,c) }
                                val numberSize=if(short) 30.sp else if(state.controlOnly && row==0) { if(spacious) 56.sp else 42.sp } else if(spacious) 40.sp else 32.sp
                                val numberStyle=MaterialTheme.typography.displaySmall.copy(fontWeight=FontWeight.Bold,fontFeatureSettings="tnum",fontSize=numberSize,lineHeight=numberSize*1.15f)
                                Column(Modifier.weight(1f).padding(horizontal=12.dp,vertical=if(short) 6.dp else 10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    Text(r.label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(r.value==stringResource(R.string.not_recording)) Text(r.value,style=MaterialTheme.typography.titleMedium)
                                    else {
                                        val value=(if(r.estimated) "≈" else "")+r.value
                                        val unit=if(r.unit.isEmpty()) "" else "  ${r.unit}"
                                        val unitWidth=measurer.measure(unit,unitStyle,maxLines=1,softWrap=false).size.width
                                        val measured=measurer.measure(value,numberStyle,maxLines=1,softWrap=false).size.width
                                        val size=(numberSize.value*minOf(1f,(tileWidth-unitWidth).coerceAtLeast(0f)/measured.coerceAtLeast(1))).coerceAtLeast(16f).sp
                                        Text(buildAnnotatedString {
                                            withStyle(SpanStyle(fontSize=size,fontWeight=FontWeight.Bold)) { append(value) }
                                            withStyle(SpanStyle(fontSize=unitStyle.fontSize,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant)) { append(unit) }
                                        },Modifier.fillMaxWidth(),style=numberStyle.copy(lineHeight=size*1.15f),maxLines=1,softWrap=false)
                                    }
                                }
                            }
                            repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } }
            val pages=chosen.chunked(4)
            val pager=rememberPagerState { pages.size }
            val scope=rememberCoroutineScope()
            if(pages.size==1) grid(chosen) else {
                HorizontalPager(pager,Modifier.fillMaxWidth().wrapContentHeight(),beyondViewportPageCount=1) { page -> grid(pages[page]) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={ scope.launch { pager.animateScrollToPage(pager.currentPage-1) } },enabled=pager.currentPage>0) { Icon(Icons.Default.KeyboardArrowLeft,stringResource(R.string.previous_metrics)) }
                    Text("${pager.currentPage+1} / ${pages.size}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick={ scope.launch { pager.animateScrollToPage(pager.currentPage+1) } },enabled=pager.currentPage<pages.lastIndex) { Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.next_metrics)) }
                }
            }
        }
    }
}
@Composable private fun HintLabel(label: Int,position: RangePosition) {
    if(position==RangePosition.UNAVAILABLE) return
    Text(stringResource(label)+" · "+stringResource(when(position) { RangePosition.BELOW -> R.string.hint_below; RangePosition.ABOVE -> R.string.hint_above; else -> R.string.hint_within }),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
}
