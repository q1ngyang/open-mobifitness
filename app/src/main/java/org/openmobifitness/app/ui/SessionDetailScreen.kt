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

@Composable internal fun SessionDetailScreen(activity: MainActivity,c: Controller,id: String,onBack: ()->Unit) {
    BackHandler(onBack=onBack)
    val revision by c.repo.revision.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    var data by remember(id) { mutableStateOf<Pair<Session,SessionStats>?>(null) }
    var overflow by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var quality by rememberSaveable(id) { mutableStateOf(false) }
    var selected by rememberSaveable(id) { mutableStateOf(SeriesMetric.HEART.name) }
    LaunchedEffect(id,revision) {
        try { data=if(data==null) c.repo.history.detail(id) else c.repo.history.session(id)?.let { it to data!!.second } }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { c.error(R.string.io_failed) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp).testTag("session-detail")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,modifier=Modifier.testTag("detail-back")) { Icon(Icons.Default.ArrowBack,stringResource(R.string.history)) }
            Text(stringResource(R.string.workout_detail),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Box { IconButton(onClick={ overflow=true }) { Icon(Icons.Default.MoreVert,stringResource(R.string.more_actions)) }
                DropdownMenu(overflow,onDismissRequest={ overflow=false }) {
                    DropdownMenuItem(text={ Text(stringResource(R.string.export_report)) },onClick={ overflow=false; activity.exportReport(sessionId=id) })
                    DropdownMenuItem(text={ Text(stringResource(R.string.export_backup)) },onClick={ overflow=false; activity.export("backup",id) })
                    DropdownMenuItem(text={ Text(stringResource(R.string.export_samples)) },onClick={ overflow=false; activity.export("samples",id) })
                    DropdownMenuItem(text={ Text(stringResource(R.string.delete),color=MaterialTheme.colorScheme.error) },onClick={ overflow=false; deleting=true })
                }
            }
        }
        val value=data
        if(value==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }; return@Column }
        val (s,stats)=value
        val summary: @Composable ()->Unit = { DetailCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)) { MachineGlyph(s.machine,Modifier.padding(12.dp)) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(machineName(s.machine)+(if(s.demo) " · "+stringResource(R.string.demo) else ""),fontWeight=FontWeight.Bold)
                    Text(s.workoutTitle.ifBlank { stringResource(R.string.plan_unrecorded) },style=MaterialTheme.typography.bodySmall)
                    Text(sessionDate(s),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                StatCell(stringResource(R.string.calories)+(if(s.caloriesEstimated) " ≈" else ""),"${displayNumber(s.caloriesKcal)} kcal",Modifier.weight(1f))
                StatCell(stringResource(R.string.duration),shortDuration(s.elapsedMs),Modifier.weight(1f))
                StatCell(stringResource(R.string.distance)+(if(s.distanceEstimated) " ≈" else ""),"${displayNumber(s.distanceM?.div(if(imperial) 1609.344 else 1000.0),2)} ${if(imperial) "mi" else "km"}",Modifier.weight(1f))
            }
            HorizontalDivider()
            val cells=listOf(SeriesMetric.HEART to false,SeriesMetric.HEART to true,SeriesMetric.CADENCE to false,SeriesMetric.CADENCE to true,SeriesMetric.POWER to false,SeriesMetric.POWER to true)
            cells.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach { (m,peak) -> val metric=stats.metrics.getValue(m); val label=stringResource(seriesResource(m))+(if(m==SeriesMetric.POWER && stats.powerEstimated) " ≈" else "")
                    StatCell(stringResource(if(peak) R.string.metric_maximum else R.string.metric_average,label),"${displayNumber(if(peak) metric.maximum else metric.average)} ${seriesUnit(m,s.machine)}",Modifier.weight(1f))
                }
            } }
        } }
        val chart: @Composable ()->Unit = { DetailCard {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { SeriesMetric.entries.forEach { m -> FilterChip(selected=selected==m.name,onClick={ selected=m.name },label={ Text(stringResource(seriesResource(m))) }) } }
            val m=SeriesMetric.valueOf(selected); val scale=if(m==SeriesMetric.SPEED && imperial) 1/1.609344 else 1.0
            val metric=stats.metrics.getValue(m); val label=stringResource(seriesResource(m)); val unit=seriesUnit(m,s.machine,imperial)
            DetailChart(metric.points.map { it.copy(value=it.value*scale) },s.elapsedMs,label,unit,if(m==SeriesMetric.HEART) Color(0xFFE63D77) else MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                StatCell(stringResource(R.string.average_value),"${displayNumber(metric.average?.times(scale),1)} $unit",Modifier.weight(1f))
                StatCell(stringResource(R.string.maximum_value),"${displayNumber(metric.maximum?.times(scale),1)} $unit",Modifier.weight(1f))
            }
            Text(stringResource(R.string.coverage_format,shortDuration(metric.coverageMs),shortDuration(s.elapsedMs)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val additional: @Composable ()->Unit = { DetailCard {
            Text(stringResource(R.string.motion_data),fontWeight=FontWeight.Bold)
            DataLine(stringResource(R.string.steps_total),stats.steps?.toString() ?: "—")
            DataLine(stringResource(R.string.strokes),stats.strokes?.toString() ?: "—")
            val speedScale=if(imperial) 2.236936 else 3.6
            DataLine(stringResource(R.string.average_speed)+(if(s.distanceEstimated) " ≈" else ""),displayNumber(s.distanceM?.takeIf { s.elapsedMs>0 }?.div(s.elapsedMs/1000.0)?.times(speedScale),1)+" "+if(imperial) "mph" else "km/h")
            listOf(SeriesMetric.SPEED,SeriesMetric.RESISTANCE).forEach { m -> val v=stats.metrics.getValue(m); val scale=if(m==SeriesMetric.SPEED && imperial) 1/1.609344 else 1.0
                DataLine(stringResource(R.string.metric_maximum,stringResource(seriesResource(m))),displayNumber(v.maximum?.times(scale),1)+" "+seriesUnit(m,s.machine,imperial))
                if(m==SeriesMetric.RESISTANCE) DataLine(stringResource(R.string.metric_average,stringResource(seriesResource(m))),displayNumber(v.average,1))
            }
            DataLine(stringResource(R.string.status_label),stringResource(sessionStatus(s.status)))
        } }
        val sources: @Composable ()->Unit = { DetailCard {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { quality=!quality },verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.source_quality),fontWeight=FontWeight.SemiBold); Text(s.device,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(if(quality) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null) }
            if(quality) {
                Text(stringResource(R.string.quality_note),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.estimated_values),style=MaterialTheme.typography.bodySmall)
                DataLine(stringResource(R.string.sample_count),stats.samples.toString())
                DataLine(stringResource(R.string.power_source),if(stats.metrics.getValue(SeriesMetric.POWER).average==null) "—" else stringResource(if(stats.powerEstimated) R.string.estimated_label else R.string.measured_label))
                Text("${s.protocol.name} · ${s.zone}",style=MaterialTheme.typography.bodySmall)
            }
        } }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth>=800.dp
            LazyColumn(Modifier.fillMaxSize().testTag("detail-scroll"),contentPadding=PaddingValues(top=8.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(wide) {
                    item { Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) { Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) { summary(); additional() }; Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) { chart(); sources(); ReportActions(activity,c,s) } } }
                } else {
                    item { summary() }; item { chart() }; item { additional() }; item { sources() }; item { ReportActions(activity,c,s) }
                }
            }
        }
        if(deleting) AlertDialog(onDismissRequest={ deleting=false },title={ Text(stringResource(R.string.delete)) },text={ Text(stringResource(R.string.delete_confirmation)) },confirmButton={ TextButton(onClick={ deleting=false; c.scope.launch { runCatching { c.repo.deleteSession(id) }.onSuccess { onBack() }.onFailure { c.error(R.string.io_failed) } } }) { Text(stringResource(R.string.delete)) } },dismissButton={ TextButton(onClick={ deleting=false }) { Text(stringResource(R.string.cancel)) } })
    }
}
@Composable private fun ReportActions(activity: MainActivity,c: Controller,s: Session) {
    var busy by remember(s.id) { mutableStateOf(false) }
    LaunchedEffect(s.archived) { busy=false }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={ activity.exportReport(sessionId=s.id) },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_report)) }
        // A confirmed write must outlive a lazy item being scrolled out of composition.
        FilledTonalButton(enabled=!busy,onClick={ busy=true; c.scope.launch { runCatching { c.repo.setArchived(s.id,!s.archived) }.onFailure { busy=false; c.error(R.string.io_failed) } } },modifier=Modifier.fillMaxWidth().testTag("archive-record")) { Text(stringResource(if(s.archived) R.string.restore_record else R.string.archive_action)) }
        Text(stringResource(R.string.export_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun DetailCard(content: @Composable ColumnScope.()->Unit) { Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) } }
@Composable private fun DataLine(label: String,value: String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(value,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium) } }
@Composable private fun DetailChart(points: List<PlotPoint>,elapsed: Long,label: String,unit: String,color: Color) {
    var selected by remember(points) { mutableStateOf<PlotPoint?>(null) }
    val line=MaterialTheme.colorScheme.outlineVariant; val textColor=MaterialTheme.colorScheme.onSurfaceVariant
    Text(selected?.let { "${shortDuration(it.ms)} · ${displayNumber(it.value,1)} $unit" } ?: (label+if(unit.isEmpty()) "" else " ($unit)"),style=MaterialTheme.typography.labelMedium,color=textColor)
    if(points.isEmpty()) { Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) { Text(stringResource(R.string.no_samples),style=MaterialTheme.typography.bodyMedium,color=textColor) }; return }
    val maximum=(points.maxOf { it.value }*1.1).coerceAtLeast(1.0); val minimum=points.minOf { it.value }.coerceAtMost(0.0)
    val description="$label: ${displayNumber(points.minOf { it.value })}–${displayNumber(points.maxOf { it.value })} $unit"
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription=description }.pointerInput(points,elapsed) { detectTapGestures { tap -> val target=(tap.x-34.dp.toPx())/(size.width-34.dp.toPx())*elapsed; selected=points.minByOrNull { abs(it.ms-target) } } }) {
        val left=34.dp.toPx(); val top=8.dp.toPx(); val bottom=size.height-24.dp.toPx(); val width=size.width-left; val height=bottom-top
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color=textColor.toArgb(); textSize=10.sp.toPx() }
        repeat(4) { n->val y=bottom-height*n/3; drawLine(line,Offset(left,y),Offset(size.width,y),1.dp.toPx()); drawContext.canvas.nativeCanvas.drawText(displayNumber(minimum+(maximum-minimum)*n/3),0f,y+4.dp.toPx(),paint) }
        fun point(p: PlotPoint)=Offset(left+(p.ms.toDouble()/elapsed.coerceAtLeast(1)*width).toFloat(),bottom-((p.value-minimum)/(maximum-minimum)*height).toFloat())
        val path=Path(); var previous: PlotPoint?=null
        points.forEach { p -> val pt=point(p); if(previous==null || p.segment!=previous!!.segment) path.moveTo(pt.x,pt.y) else path.lineTo(pt.x,pt.y); previous=p }
        drawPath(path,color,style=Stroke(2.dp.toPx()))
        if(points.size==1) drawCircle(color,3.dp.toPx(),point(points[0]))
        selected?.let { val p=point(it); drawLine(color.copy(alpha=.4f),Offset(p.x,top),Offset(p.x,bottom),1.dp.toPx()); drawCircle(color,4.dp.toPx(),p) }
        listOf(0L,elapsed/2,elapsed).forEachIndexed { i,ms -> paint.textAlign=when(i) { 0->android.graphics.Paint.Align.LEFT; 2->android.graphics.Paint.Align.RIGHT; else->android.graphics.Paint.Align.CENTER }; drawContext.canvas.nativeCanvas.drawText(shortDuration(ms),left+width*i/2,size.height-3.dp.toPx(),paint) }
    }
}
