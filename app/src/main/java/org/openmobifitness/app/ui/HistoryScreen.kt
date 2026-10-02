@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun machineName(machine: Machine)=stringResource(machineResource(machine))
@Composable internal fun HistoryScreen(activity: MainActivity,c: Controller) {
    val revision by c.repo.revision.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var machine by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var allRecords by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableIntStateOf(0) }
    var yearPicker by rememberSaveable { mutableStateOf(false) }
    var years by remember { mutableStateOf(listOf(LocalDate.now().year)) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.MONTH.name) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var filterDialog by rememberSaveable { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(HistoryPage(emptyList(),0)) }
    var overview by remember { mutableStateOf(HistoryOverview()) }
    // Keep list position while the detail branch temporarily leaves composition.
    val listState=rememberLazyListState()
    val selectedPeriod=HistoryPeriod.valueOf(period); val selectedDate=LocalDate.parse(date)
    val window=PeriodWindow.of(selectedPeriod,selectedDate,ZoneId.systemDefault())
    val locale=androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val resources=LocalResources.current
    val words=if(search.isBlank()) "" else Machine.entries.filter { resources.getString(machineResource(it)).contains(search.trim(),true) }.joinToString(",") { it.name }
    val dates=if(allRecords) RecordDates.selected(year,month) else RecordDates.recent()
    val query=HistoryQuery(dates.first,dates.second,machine,search,source,2,words)
    val statsQuery=if(allRecords) query else query.copy(from=window.from,until=window.until)
    LaunchedEffect(revision) { years=c.repo.history.years() }
    var previousQuery by rememberSaveable { mutableStateOf(query.encode()) }
    LaunchedEffect(query) { if(previousQuery!=query.encode()) { previousQuery=query.encode(); page=0 } }
    var previousLocation by rememberSaveable { mutableStateOf(query.encode()+"#$page") }
    LaunchedEffect(query,page) {
        val location=query.encode()+"#$page"
        if(previousLocation!=location) { previousLocation=location; listState.scrollToItem(0) }
    }
    LaunchedEffect(query,statsQuery,page,revision) {
        loaded=false
        if(search.isNotEmpty()) delay(220)
        try {
            val next=c.repo.history.page(query,page*20,20)
            if(page>0 && next.rows.isEmpty()) { page=((next.count-1).coerceAtLeast(0)/20) }
            else { results=next; overview=c.repo.history.overview(statsQuery); loaded=true }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { c.error(R.string.io_failed); loaded=true }
    }
    if(detail!=null) { SessionDetailScreen(activity,c,detail!!) { detail=null }; return }
    BackHandler(allRecords) { allRecords=false; page=0 }
    val loadingLabel=stringResource(R.string.loading)
    val overviewContent: @Composable (Boolean)->Unit = { compact ->
        if(!allRecords) PeriodPicker(selectedPeriod,selectedDate,{ period=it.name },{ date=it.toString() })
        if(!loaded) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("history-loading").semantics { contentDescription=loadingLabel })
        OverviewCard(overview,if(allRecords) (if(year==0) HistoryPeriod.ALL else if(month==0) HistoryPeriod.YEAR else HistoryPeriod.MONTH) else selectedPeriod,
            if(allRecords && year!=0) LocalDate.of(year,month.coerceAtLeast(1),1) else selectedDate,chart=!allRecords,compact=compact)
    }
    val recordsHeader: @Composable ()->Unit = { Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(if(allRecords) R.string.all_records else R.string.recent_records)+" · ${results.count}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        if(!allRecords) TextButton(onClick={ allRecords=true; page=0 },modifier=Modifier.testTag("all-records")) { Text(stringResource(R.string.all_records)); Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp)) }
    } }
    val actions: @Composable ()->Unit = {
                Box { IconButton(onClick={ overflow=true }) { Icon(Icons.Default.MoreVert,stringResource(R.string.more_actions)) }
                    DropdownMenu(expanded=overflow,onDismissRequest={ overflow=false }) {
                        DropdownMenuItem(text={ Text(stringResource(R.string.export_report)) },onClick={ overflow=false; activity.exportReport(query) })
                        HorizontalDivider()
                        DropdownMenuItem(text={ Text(stringResource(R.string.export_backup)) },onClick={ overflow=false; activity.export("backup") })
                        DropdownMenuItem(text={ Text(stringResource(R.string.export_exchange)) },onClick={ overflow=false; activity.export("sessions") })
                        DropdownMenuItem(text={ Text(stringResource(R.string.import_file)) },onClick={ overflow=false; activity.importFile() })
                    }
                }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=880.dp || (maxWidth>=520.dp && maxHeight<440.dp)
        val compactHeight=maxHeight<400.dp
        val compactOverview=maxWidth<600.dp
        Column(Modifier.widthIn(max=if(allRecords) 840.dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxSize().align(Alignment.TopCenter).padding(horizontal=16.dp)) {
            if(!compactHeight) Row(verticalAlignment=Alignment.CenterVertically) {
                if(allRecords) IconButton(onClick={ allRecords=false; page=0 },modifier=Modifier.testTag("all-records-back")) { Icon(Icons.Default.ArrowBack,stringResource(R.string.history)) }
                Text(stringResource(if(allRecords) R.string.all_records else R.string.history),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                actions()
            }
            if(allRecords) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(compactHeight) IconButton(onClick={ allRecords=false; page=0 }) { Icon(Icons.Default.ArrowBack,stringResource(R.string.history)) }
                    TextButton(onClick={ yearPicker=true },modifier=Modifier.testTag("record-year")) { Text(if(year==0) stringResource(R.string.all_years) else year.toString(),fontWeight=FontWeight.Bold); Icon(Icons.Default.ArrowDropDown,null) }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        (0..12).forEach { m -> FilterChip(selected=month==m,onClick={ month=m; if(year==0 && m!=0) year=LocalDate.now().year },label={ Text(if(m==0) stringResource(R.string.whole_year) else java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT,locale)) },modifier=Modifier.testTag("record-month-$m")) }
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(compactHeight) actions()
                SearchBox(search,{ search=it },stringResource(R.string.search_history),Modifier.weight(1f),"history-search")
                FilledTonalIconButton(onClick={ filterDialog=true },modifier=Modifier.size(48.dp).testTag("history-filter")) { FilterGlyph(stringResource(R.string.filters)) }
            }
            if(machine.isNotEmpty() || source!=0) Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                Text(listOfNotNull(machine.takeIf { it.isNotEmpty() }?.let { machineName(Machine.valueOf(it)) },if(source==0) null else stringResource(if(source==1) R.string.real_data else R.string.demo)).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={ machine=""; source=0; search="" }) { Text(stringResource(R.string.reset_filters)) }
            }
            if(wide && !allRecords) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(.95f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) { overviewContent(false); Spacer(Modifier.height(16.dp)) }
                LazyColumn(Modifier.weight(1.05f).fillMaxHeight().testTag("history-list"),state=listState,contentPadding=PaddingValues(vertical=12.dp)) {
                    item { recordsHeader() }
                    if(!loaded) item { LoadingRow() } else if(results.rows.isEmpty()) item { EmptyHistory() }
                    itemsIndexed(results.rows,key={ _,s -> s.id }) { index,s ->
                        if(index==0 || sessionDate(results.rows[index-1],"yyyy-MM")!=sessionDate(s,"yyyy-MM")) Text(sessionDate(s,"yyyy / MM"),Modifier.padding(top=12.dp,bottom=6.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HistoryRecord(s,imperial) { detail=s.id }
                    }
                }
            } else LazyColumn(Modifier.weight(1f).testTag("history-list"),state=listState,contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                item { overviewContent(compactOverview) }
                item { recordsHeader() }
                if(!loaded) item { LoadingRow() } else if(results.rows.isEmpty()) item { EmptyHistory() }
                itemsIndexed(results.rows,key={ _,s -> s.id }) { index,s ->
                        if(index==0 || sessionDate(results.rows[index-1],"yyyy-MM")!=sessionDate(s,"yyyy-MM")) Text(sessionDate(s,"yyyy / MM"),Modifier.padding(top=6.dp,bottom=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HistoryRecord(s,imperial) { detail=s.id }
                    }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                IconButton(onClick={ page-- },enabled=page>0 && loaded) { Icon(Icons.Default.KeyboardArrowLeft,stringResource(R.string.previous_page)) }
                Text(stringResource(R.string.page_count,page+1,((results.count+19)/20).coerceAtLeast(1),results.count),style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick={ page++ },enabled=(page+1)*20<results.count && loaded) { Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.next_page)) }
            }
        }
    }
    if(filterDialog) AlertDialog(onDismissRequest={ filterDialog=false },title={ Text(stringResource(R.string.filters)) },text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.machine_type),fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=machine.isEmpty(),onClick={ machine="" },label={ Text(stringResource(R.string.all_items)) })
            listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL).forEach { m -> FilterChip(selected=machine==m.name,onClick={ machine=m.name },label={ Text(machineName(m)) }) }
        }
        Text(stringResource(R.string.source_data),fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(R.string.all_items,R.string.real_data,R.string.demo).forEachIndexed { i,id -> FilterChip(selected=source==i,onClick={ source=i },label={ Text(stringResource(id)) }) } }

    } },confirmButton={ TextButton(onClick={ filterDialog=false }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ machine=""; source=0; search=""; filterDialog=false }) { Text(stringResource(R.string.reset_filters)) } })
    if(yearPicker) AlertDialog(onDismissRequest={ yearPicker=false },title={ Text(stringResource(R.string.select_year)) },text={
        LazyColumn(Modifier.heightIn(max=360.dp)) {
            item { TextButton(onClick={ year=0; month=0; yearPicker=false },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.all_years)) } }
            itemsIndexed(years) { _,y -> TextButton(onClick={ year=y; yearPicker=false },modifier=Modifier.fillMaxWidth()) { Text(y.toString()) } }
        }
    },confirmButton={ TextButton(onClick={ yearPicker=false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun LoadingRow() { Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
@Composable private fun EmptyHistory() { Text(stringResource(R.string.no_matches),Modifier.fillMaxWidth().padding(vertical=24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun PeriodPicker(period: HistoryPeriod,date: LocalDate,onPeriod: (HistoryPeriod)->Unit,onDate: (LocalDate)->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top=10.dp).background(MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(10.dp))) {
            HistoryPeriod.entries.forEach { p -> Surface(onClick={ onPeriod(p) },modifier=Modifier.weight(1f).padding(3.dp),shape=RoundedCornerShape(8.dp),color=if(p==period) MaterialTheme.colorScheme.primary else Color.Transparent,contentColor=if(p==period) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) { Box(Modifier.heightIn(min=40.dp),contentAlignment=Alignment.Center) { Text(stringResource(when(p) { HistoryPeriod.WEEK->R.string.week; HistoryPeriod.MONTH->R.string.month; HistoryPeriod.YEAR->R.string.year; else->R.string.all_items }),style=MaterialTheme.typography.labelLarge) } } }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            fun shifted(n: Long)=when(period) { HistoryPeriod.WEEK->date.plusWeeks(n); HistoryPeriod.MONTH->date.withDayOfMonth(1).plusMonths(n); else->date.withDayOfYear(1).plusYears(n) }
            IconButton(onClick={ onDate(shifted(-1)) },enabled=period!=HistoryPeriod.ALL) { Icon(Icons.Default.KeyboardArrowLeft,stringResource(R.string.previous_period)) }
            val title=when(period) { HistoryPeriod.ALL->stringResource(R.string.all_items); HistoryPeriod.YEAR->date.year.toString(); HistoryPeriod.MONTH->date.format(DateTimeFormatter.ofPattern("yyyy / MM")); else-> { val start=Instant.ofEpochMilli(PeriodWindow.of(period,date,ZoneId.systemDefault()).from).atZone(ZoneId.systemDefault()).toLocalDate(); "${start.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("MM/dd"))}" } }
            TextButton(onClick={ onDate(LocalDate.now()) },modifier=Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
            IconButton(onClick={ onDate(shifted(1)) },enabled=period!=HistoryPeriod.ALL && !shifted(1).isAfter(LocalDate.now())) { Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.next_period)) }
        }
    }
}
@Composable private fun OverviewCard(o: HistoryOverview,period: HistoryPeriod,date: LocalDate,chart: Boolean=true,compact: Boolean=false) {
    val points=remember(o.days,period,date) {
        val grouped=o.days.groupBy { when(period) { HistoryPeriod.YEAR->it.first.withDayOfMonth(1); HistoryPeriod.ALL->it.first.withDayOfYear(1); else->it.first } }.mapValues { it.value.sumOf { d->d.second } }
        val keys=when(period) {
            HistoryPeriod.MONTH->(1..date.lengthOfMonth()).map { date.withDayOfMonth(it) }
            HistoryPeriod.WEEK->{ val start=date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); (0..6).map { start.plusDays(it.toLong()) } }
            HistoryPeriod.YEAR->(1..12).map { date.withDayOfMonth(1).withMonth(it) }
            HistoryPeriod.ALL->grouped.keys.sorted()
        }
        keys.map { d -> (when(period) { HistoryPeriod.YEAR->d.monthValue.toString(); HistoryPeriod.ALL->d.year.toString(); else->d.dayOfMonth.toString() }) to ((grouped[d] ?: 0L)/60000.0) }
    }
    Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(if(compact) 10.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatCell(stringResource(R.string.duration),shortDuration(o.elapsedMs),Modifier.weight(1.4f))
                StatCell(stringResource(R.string.calories)+(if(o.estimated) " ≈" else ""),displayNumber(o.calories)+" kcal",Modifier.weight(1.1f))
                StatCell(stringResource(R.string.workout_count),"${o.count}",Modifier.weight(.7f))
            }
            if(chart) HistoryBars(points,compact,if(compact && o.demoCount>0) stringResource(R.string.stats_demo_count,o.demoCount) else null)
            if(o.demoCount>0 && (!compact || !chart)) Text(stringResource(R.string.stats_demo_count,o.demoCount),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(o.caloriesPresent<o.count) Text(stringResource(R.string.stats_partial),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun StatCell(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        val unit=value.substringAfterLast(' ',"").takeIf { it in setOf("kcal","km","mi","bpm","rpm","spm","W","km/h","mph") }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom) {
            BasicText(if(unit==null) value else value.substringBeforeLast(' '),modifier=Modifier.weight(1f,fill=false).alignByBaseline(),maxLines=1,style=MaterialTheme.typography.titleLarge.copy(color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold),autoSize=TextAutoSize.StepBased(13.sp,24.sp,1.sp))
            if(unit!=null) Text(unit,modifier=Modifier.alignByBaseline(),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun HistoryBars(points: List<Pair<String,Double>>,compact: Boolean=false,note: String?=null) {
    val color=MaterialTheme.colorScheme.primary; val line=MaterialTheme.colorScheme.outlineVariant; val textColor=MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember(points) { mutableIntStateOf(-1) }
    val max=(points.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    // Include both endpoints without adding an adjacent final label (e.g. 29/31),
    // which overlaps when a narrow window uses a larger system font.
    val labelIndices=if(points.size<=5) points.indices.toSet() else (0..4).map { it*points.lastIndex/4 }.toSet()
    val description=stringResource(R.string.chart_minutes)+": "+points.filter { it.second>0 }.joinToString { "${it.first}: ${displayNumber(it.second,1)}" }
    val caption=if(selected in points.indices) "${points[selected].first} · ${displayNumber(points[selected].second,1)} min" else stringResource(R.string.chart_minutes)
    Text(listOfNotNull(caption,note).joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=textColor)
    Canvas(Modifier.fillMaxWidth().height(if(compact) 88.dp else 112.dp).semantics { contentDescription=description }.pointerInput(points) { detectTapGestures { tap -> selected=(((tap.x-32.dp.toPx())/(size.width-32.dp.toPx()))*points.size).toInt().coerceIn(0,(points.size-1).coerceAtLeast(0)) } }) {
        val left=32.dp.toPx(); val bottom=size.height-22.dp.toPx(); val top=8.dp.toPx(); val height=bottom-top
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color=textColor.toArgb(); textSize=10.sp.toPx() }
        repeat(3) { n -> val y=bottom-height*n/2; drawLine(line,Offset(left,y),Offset(size.width,y),1.dp.toPx()); drawContext.canvas.nativeCanvas.drawText(displayNumber(max*n/2),0f,y+4.dp.toPx(),paint) }
        val width=(size.width-left)/points.size.coerceAtLeast(1)
        points.forEachIndexed { i,p ->
            val h=(p.second/max*height).toFloat(); if(h>0) drawRoundRect(color.copy(alpha=if(selected<0 || selected==i) 1f else .35f),Offset(left+i*width+width*.15f,bottom-h),Size(width*.7f,h),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            if(i in labelIndices) { paint.textAlign=android.graphics.Paint.Align.CENTER; drawContext.canvas.nativeCanvas.drawText(p.first,left+(i+.5f)*width,size.height-3.dp.toPx(),paint); paint.textAlign=android.graphics.Paint.Align.LEFT }
        }
    }
}
@Composable private fun HistoryRecord(s: Session,imperial: Boolean,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag("record-${s.id}"),color=MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.padding(vertical=12.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer) { MachineGlyph(s.machine,Modifier.padding(10.dp).size(24.dp)) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(machineName(s.machine),modifier=Modifier.weight(1f,fill=false),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(s.demo) Text(" · "+stringResource(R.string.demo),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary,maxLines=1)
                    }
                    Text(s.workoutTitle.ifBlank { stringResource(R.string.plan_unrecorded) },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    BasicText("${shortDuration(s.elapsedMs)} · ${if(s.caloriesEstimated) "≈" else ""}${displayNumber(s.caloriesKcal)} kcal · ${displayNumber(s.distanceM?.div(if(imperial) 1609.344 else 1000.0),2)} ${if(imperial) "mi" else "km"}",maxLines=1,style=MaterialTheme.typography.labelMedium.copy(color=MaterialTheme.colorScheme.onSurfaceVariant),autoSize=TextAutoSize.StepBased(10.sp,12.sp,1.sp))
                }
                Column(horizontalAlignment=Alignment.End) { Text(sessionDate(s,"MM/dd"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(20.dp)) }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))
        }
    }
}
