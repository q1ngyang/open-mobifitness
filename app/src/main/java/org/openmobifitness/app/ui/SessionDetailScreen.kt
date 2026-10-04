@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import kotlin.math.abs

@Composable internal fun SessionDetailScreen(activity: MainActivity,c: Controller,id: String,saved: Boolean=false,onBack: ()->Unit) {
    BackHandler(onBack=onBack)
    val context=LocalContext.current; val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val revision by c.repo.revision.collectAsStateWithLifecycle(); val users by c.repo.users.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle(); val exercise by c.state.collectAsStateWithLifecycle()
    var data by remember(id) { mutableStateOf<Pair<Session,SessionStats>?>(null) }; var loaded by remember(id) { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf(false) }
    var correction by remember { mutableStateOf(false) }; var quality by rememberSaveable(id) { mutableStateOf(false) }
    var selected by rememberSaveable(id) { mutableStateOf("") }
    LaunchedEffect(id,revision) {
        try { data=c.repo.history.detail(id); loaded=true }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { c.error(R.string.io_failed); loaded=true }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp).testTag("session-detail")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,modifier=Modifier.testTag("detail-back")) { Icon(if(saved) Icons.Default.Close else Icons.Default.ArrowBack,stringResource(if(saved) R.string.close else R.string.history)) }
            Text(stringResource(if(saved) R.string.workout_saved else R.string.workout_detail),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Box { IconButton(onClick={ overflow=true }) { Icon(Icons.Default.MoreVert,stringResource(R.string.more_actions)) }
                DropdownMenu(overflow,onDismissRequest={ overflow=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.export_report)) },onClick={ overflow=false; activity.exportReport(sessionId=id) })
                    DropdownMenuItem(text={ Text(stringResource(R.string.export_samples)) },onClick={ overflow=false; activity.export("samples",id) })
                    DropdownMenuItem(text={ Text(stringResource(R.string.report_change_owner)) },enabled=c.dataActionsAllowed,onClick={ overflow=false; correction=true })
                    DropdownMenuItem(text={ Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) },enabled=c.dataActionsAllowed,onClick={ overflow=false; deleting=true })
                }
            }
        }
        val value=data
        if(value==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { if(loaded) Text(stringResource(R.string.no_matches)) else CircularProgressIndicator() }; return@Column }
        val (s,stats)=value
        val owner=users.firstOrNull { it.id==s.ownerUserId }
        val descriptors=remember(s,stats) { ReportMetrics.descriptors(s,stats,s.reportEvidence()) }
        val mainKeys=setOf("time","calories",when(s.machine) { Machine.JUMP_ROPE -> "jumps"; Machine.DUMBBELL -> "repetitions"; else -> "distance" })
        val performance=descriptors.filter { it.key !in mainKeys && (it.value!=null || it.metric==MetricId.HEART && it.aggregation==ReportAggregation.AVERAGE) }
        val availableSeries=ReportMetrics.series(s.machine,s.reportEvidence()).filter { stats.metrics.getValue(it).maximum!=null || it==SeriesMetric.HEART }.sortedBy { if(it==SeriesMetric.POWER) 0 else 1 }
        val chosen=availableSeries.firstOrNull { it.name==selected } ?: availableSeries.firstOrNull { it==SeriesMetric.POWER && stats.metrics.getValue(it).maximum!=null } ?: availableSeries.firstOrNull { stats.metrics.getValue(it).maximum!=null } ?: SeriesMetric.HEART
        val summary: @Composable ()->Unit = { DetailCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) { MachineGlyph(s.machine,Modifier.padding(12.dp).size(28.dp)) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(machineName(s.machine)+(if(s.demo) " · "+stringResource(R.string.demo) else ""),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(s.workoutTitle.ifBlank { stringResource(R.string.plan_unrecorded) },style=MaterialTheme.typography.bodyMedium)
                    Text(sessionDate(s),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                UserAvatar(c,owner?.name.orEmpty(),owner?.avatar.orEmpty(),30.dp)
                Text(owner?.name ?: stringResource(R.string.history_unassigned),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                TextButton(enabled=c.dataActionsAllowed,onClick={ correction=true },modifier=Modifier.testTag("change-owner")) { Text(stringResource(R.string.report_change_owner)) }
            }
            HorizontalDivider()
            ReportGrid(descriptors.filter { it.key in mainKeys },imperial,true)
        } }
        val measurements: @Composable ()->Unit = { DetailCard {
            Text(stringResource(R.string.report_performance),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            ReportGroups(performance,imperial)
            if(s.machine==Machine.DUMBBELL) Text(stringResource(R.string.report_no_sets),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            DataLine(stringResource(R.string.status_label),stringResource(sessionStatus(s.status)))
        } }
        val chart: @Composable ()->Unit = { DetailCard {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { availableSeries.forEach { m -> FilterChip(selected=chosen==m,onClick={ selected=m.name },label={ Text(stringResource(seriesResource(m,s.machine))) }) } }
            val metric=stats.metrics.getValue(chosen)
            val representative=descriptors.first { it.series==chosen }
            val unit=representative.reportValue(imperial,locale).second
            val scale=if(imperial) when(chosen) { SeriesMetric.SPEED -> 1/1.609344; SeriesMetric.STRIDE -> 3.280839895; SeriesMetric.LOAD -> 2.204622622; else -> 1.0 } else 1.0
            DetailChart(metric.points.map { it.copy(value=it.value*scale) },s.elapsedMs,stringResource(seriesResource(chosen,s.machine)),if(chosen==SeriesMetric.PACE) "s / 500m" else unit,if(chosen==SeriesMetric.HEART) Color(0xFFE63D77) else MaterialTheme.colorScheme.primary)
            ReportGroups(descriptors.filter { it.series==chosen },imperial)
            Text(stringResource(R.string.coverage_format,shortDuration(metric.coverageMs),shortDuration(s.elapsedMs)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(chosen==SeriesMetric.PACE) Text(stringResource(R.string.report_pace_basis),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val sources: @Composable ()->Unit = { DetailCard {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { quality=!quality },verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.source_quality),fontWeight=FontWeight.SemiBold); Text(s.device,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(if(quality) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null)
            }
            if(quality) {
                Text(stringResource(R.string.quality_note),style=MaterialTheme.typography.bodySmall)
                DataLine(stringResource(R.string.report_started_user),s.startedUserName.ifBlank { stringResource(R.string.plan_unrecorded) })
                DataLine(stringResource(R.string.energy_source),stringResource(energySourceResource(s)))
                DataLine(stringResource(R.string.user_weight_optional),"${displayNumber(s.weightKg,1)} kg · ${stringResource(sourceResource(s.weightSource))}")
                DataLine(stringResource(R.string.user_met),"${displayNumber(s.met,1)} · ${stringResource(sourceResource(s.metSource))}")
                DataLine(stringResource(R.string.sample_count),stats.samples.toString())
                if(SeriesMetric.POWER in ReportMetrics.series(s.machine,s.reportEvidence()) && stats.metrics.getValue(SeriesMetric.POWER).maximum!=null) DataLine(stringResource(R.string.power_source),stringResource(if(stats.powerMixed) R.string.energy_mixed else if(stats.powerEstimated) R.string.estimated_label else R.string.measured_label))
                Text("${s.protocol.name} · ${s.zone}",style=MaterialTheme.typography.bodySmall)
            }
        } }
        val plan: @Composable ()->Unit = { PlanSnapshot(s) }
        val actions: @Composable ()->Unit = { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ activity.exportReport(sessionId=s.id) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.export_report)) }
            Text(stringResource(R.string.export_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val fontScale=LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth/fontScale>=840.dp
            LazyColumn(Modifier.fillMaxSize().testTag("detail-scroll"),contentPadding=PaddingValues(top=8.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(wide) item { Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) { summary(); measurements() }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) { chart(); plan(); sources(); actions() }
                } } else { item { summary() }; item { measurements() }; item { chart() }; item { plan() }; item { sources() }; item { actions() } }
            }
        }
        if(deleting) AlertDialog(onDismissRequest={ deleting=false },title={ Text(stringResource(R.string.delete)) },text={ Text(stringResource(R.string.delete_confirmation)) },confirmButton={ TextButton(onClick={ deleting=false; c.scope.launch { runCatching { c.idleOperation { c.repo.deleteSession(id) } }.onSuccess { onBack() }.onFailure { c.error(R.string.io_failed) } } }) { Text(stringResource(R.string.delete)) } },dismissButton={ TextButton(onClick={ deleting=false }) { Text(stringResource(R.string.cancel)) } })
        if(correction) OwnerCorrection(c,s,{ correction=false })
    }
}

/** A metric family never shares a row with an unrelated statistic. */
@Composable private fun ReportGroups(entries: List<ReportMetricDescriptor>,imperial: Boolean) {
    val context=LocalContext.current
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val scalars=entries.filter { it.series==null }
    if(scalars.isNotEmpty()) ReportGrid(scalars,imperial)
    entries.filter { it.series!=null }.groupBy { it.series }.values.forEach { family ->
        val first=family.first()
        val unit=first.reportValue(imperial,locale).second
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("report-group-${first.series}")) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(3.dp).height(14.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(2.dp)))
                    Text(stringResource(first.metric?.let(::metricLabel) ?: R.string.steps_total)+(if(first.source in setOf(ReportSource.ESTIMATED,ReportSource.MIXED)) " ≈" else "")+if(unit.isBlank()) "" else " · $unit",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxWithConstraints {
                    val columns=if(maxWidth/LocalDensity.current.fontScale>=260.dp) 3 else 1
                    val cell: @Composable (ReportMetricDescriptor,Modifier)->Unit = { d,modifier ->
                        val label=if(d.metric==MetricId.PACE && d.aggregation in setOf(ReportAggregation.MINIMUM,ReportAggregation.MAXIMUM)) context.reportLabel(d) else stringResource(when(d.aggregation) { ReportAggregation.AVERAGE -> R.string.average_value; ReportAggregation.MAXIMUM -> R.string.maximum_value; ReportAggregation.MINIMUM -> R.string.minimum_value; else -> R.string.status_label })
                        Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            NumberText(d.reportValue(imperial,locale).first,26,Modifier.fillMaxWidth().height(34.dp*LocalDensity.current.fontScale))
                        }
                    }
                    if(columns==3) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        family.forEachIndexed { index,d ->
                            if(index>0) VerticalDivider(Modifier.fillMaxHeight())
                            cell(d,Modifier.weight(1f))
                        }
                    } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { family.forEach { cell(it,Modifier.fillMaxWidth()) } }
                }
            }
        }
    }
}

@Composable private fun ReportGrid(entries: List<ReportMetricDescriptor>,imperial: Boolean,summary: Boolean=false) {
    val context=LocalContext.current; val scale=LocalDensity.current.fontScale; val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    BoxWithConstraints {
        val columns=if(summary && maxWidth/scale>=280.dp) 3 else if(maxWidth/scale>=220.dp) 2 else 1
        Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
            entries.chunked(columns).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                row.forEach { d ->
                    val (number,unit)=d.reportValue(imperial,locale)
                    StatCell(context.reportLabel(d)+(if(d.source in setOf(ReportSource.ESTIMATED,ReportSource.MIXED) && d.value!=null) " ≈" else ""),number+if(unit.isBlank()) "" else " $unit",Modifier.weight(1f))
                }
                repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
            } }
        }
    }
}
@Composable private fun PlanSnapshot(session: Session) {
    val context=LocalContext.current
    val plan=remember(session.workoutSnapshot) { runCatching { Exchange.parse(session.workoutSnapshot).workouts.single() }.getOrNull() }
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    DetailCard {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { expanded=!expanded },verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.report_plan_snapshot),Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null)
        }
        if(expanded) {
            if(plan==null) Text(stringResource(R.string.report_no_snapshot),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text(plan.title,style=MaterialTheme.typography.titleMedium)
                plan.steps.forEachIndexed { i,step ->
                    if(i>0) HorizontalDivider()
                    Text(stringResource(R.string.stage_format,i+1,plan.steps.size),fontWeight=FontWeight.SemiBold)
                    Text(when(step.condition) { Condition.TIME -> context.minutesSeconds((step.target*1000).toLong()); Condition.DISTANCE -> "${displayNumber(step.target)} m"; Condition.STROKES -> "${displayNumber(step.target)} ${stringResource(R.string.strokes)}" },style=MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.report_frequency)+" · "+context.rangeText(step.frequency.resolve(plan.hints.frequency),if(session.machine==Machine.ROWER || session.machine==Machine.TREADMILL) "spm" else "rpm"),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.heart_rate)+" · "+context.rangeText(step.heart.resolve(plan.hints.heart),"bpm"),style=MaterialTheme.typography.bodySmall)
                    step.resistancePercent?.let { Text(stringResource(R.string.resistance_percent)+" · $it%",style=MaterialTheme.typography.bodySmall) }
                    step.speedTargetMps?.let { Text(stringResource(R.string.target)+" · ${displayNumber(it*3.6,1)} km/h",style=MaterialTheme.typography.bodySmall) }
                    step.inclineTargetPercent?.let { Text(stringResource(R.string.incline)+" · ${displayNumber(it,1)}%",style=MaterialTheme.typography.bodySmall) }
                }
                Text(stringResource(R.string.hint_sound)+": "+stringResource(if(plan.hints.sound) R.string.enabled else R.string.disabled)+" · "+stringResource(R.string.hint_vibration)+": "+stringResource(if(plan.hints.vibration) R.string.enabled else R.string.disabled),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable private fun OwnerCorrection(c: Controller,session: Session,onClose: ()->Unit) {
    val users by c.repo.users.collectAsStateWithLifecycle(); var selected by rememberSaveable { mutableStateOf(session.ownerUserId) }
    var busy by remember { mutableStateOf(false) }; val scope=rememberCoroutineScope()
    UserPanel(stringResource(R.string.report_change_owner),{ if(!busy) onClose() },footer={
        Button(enabled=!busy && users.any { it.available && it.id==selected } && selected!=session.ownerUserId,onClick={ scope.launch {
            busy=true
            runCatching { c.idleOperation { c.repo.changeOwners(listOf(session.id),selected!!,mapOf(session.id to session.ownerUserId)) } }.onSuccess { onClose() }.onFailure { c.error(R.string.storage_failed); onClose() }
            busy=false
        } },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm)) }
    }) {
        Text(stringResource(R.string.report_owner_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        users.filter { it.available }.forEach { user -> Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable { selected=user.id },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            UserAvatar(c,user.name,user.avatar); Text(profileLabel(user,users),Modifier.weight(1f)); RadioButton(selected==user.id,null)
        } }
        if(users.none { it.available }) Text(stringResource(R.string.user_empty))
    }
}
@Composable private fun DetailCard(content: @Composable ColumnScope.()->Unit) { Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) } }
@Composable private fun DataLine(label: String,value: String) {
    BoxWithConstraints { if(maxWidth/LocalDensity.current.fontScale<280.dp) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(value,style=MaterialTheme.typography.bodyMedium) }
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium) }
    }
}
@Composable private fun DetailChart(points: List<PlotPoint>,elapsed: Long,label: String,unit: String,color: Color) {
    var selected by remember(points) { mutableStateOf<PlotPoint?>(null) }
    val line=MaterialTheme.colorScheme.outlineVariant; val textColor=MaterialTheme.colorScheme.onSurfaceVariant
    Text(selected?.let { "${shortDuration(it.ms)} · ${displayNumber(it.value,1)} $unit" } ?: (label+if(unit.isEmpty()) "" else " ($unit)"),Modifier.testTag("detail-chart-readout"),style=MaterialTheme.typography.labelMedium,color=textColor)
    if(points.isEmpty()) { Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) { Text(stringResource(R.string.no_samples),style=MaterialTheme.typography.bodyMedium,color=textColor) }; return }
    val maximum=(points.maxOf { it.value }*1.1).coerceAtLeast(1.0); val minimum=points.minOf { it.value }.coerceAtMost(0.0)
    val description="$label: ${displayNumber(points.minOf { it.value })}–${displayNumber(points.maxOf { it.value })} $unit"
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("detail-chart").semantics { contentDescription=description }.pointerInput(points,elapsed) { detectTapGestures { tap -> val target=(tap.x-34.dp.toPx())/(size.width-34.dp.toPx())*elapsed; selected=points.minByOrNull { abs(it.ms-target) } } }) {
        val left=34.dp.toPx(); val top=8.dp.toPx(); val bottom=size.height-24.dp.toPx(); val width=size.width-left; val height=bottom-top
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color=textColor.toArgb(); textSize=10.sp.toPx() }
        repeat(4) { n->val y=bottom-height*n/3; drawLine(line,Offset(left,y),Offset(size.width,y),1.dp.toPx()); drawContext.canvas.nativeCanvas.drawText(displayNumber(minimum+(maximum-minimum)*n/3),0f,y+4.dp.toPx(),paint) }
        fun point(p: PlotPoint): Offset {
            val fraction=p.ms.toDouble()/elapsed.coerceAtLeast(1)
            return Offset(left+(fraction*width).toFloat(),bottom-((p.value-minimum)/(maximum-minimum)*height).toFloat())
        }
        val path=Path(); var previous: PlotPoint?=null
        points.forEach { p -> val pt=point(p); if(previous==null || p.segment!=previous!!.segment) path.moveTo(pt.x,pt.y) else path.lineTo(pt.x,pt.y); previous=p }
        drawPath(path,color,style=Stroke(2.dp.toPx()))
        points.forEachIndexed { i,p -> if((i==0 || points[i-1].segment!=p.segment) && (i==points.lastIndex || points[i+1].segment!=p.segment)) drawCircle(color,2.5.dp.toPx(),point(p)) }
        selected?.let { val p=point(it); drawLine(color.copy(alpha=.4f),Offset(p.x,top),Offset(p.x,bottom),1.dp.toPx()); drawCircle(color,4.dp.toPx(),p) }
        listOf(0L,elapsed/2,elapsed).forEachIndexed { i,ms -> paint.textAlign=when(i) { 0->android.graphics.Paint.Align.LEFT; 2->android.graphics.Paint.Align.RIGHT; else->android.graphics.Paint.Align.CENTER }; drawContext.canvas.nativeCanvas.drawText(shortDuration(ms),left+width*i/2,size.height-3.dp.toPx(),paint) }
    }
}
