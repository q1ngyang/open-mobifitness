package org.openmobifitness.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmobifitness.app.R
import org.openmobifitness.app.data.reportLabel
import org.openmobifitness.app.data.reportValue
import org.openmobifitness.app.data.performanceValue
import org.openmobifitness.app.metricLabel
import org.openmobifitness.core.*

private data class PerformanceRow(
    val key: String, val label: String, val average: Pair<String,String>,
    val maximum: Pair<String,String>, val minimum: Pair<String,String>, val pace: Boolean
)

/** Report-only presentation: chart summaries and the underlying statistics remain independent. */
@Composable internal fun ReportPerformanceCard(entries: List<ReportMetricDescriptor>, imperial: Boolean, status: String, noSets: Boolean, resistanceRange: ResistanceRange?) {
    val context=LocalContext.current
    val locale=LocalConfiguration.current.locales[0]
    val resources=LocalResources.current
    val colors=MaterialTheme.colorScheme
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val labelStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp,letterSpacing=0.sp,lineBreak=LineBreak.Paragraph,hyphens=Hyphens.Auto)
    val unitStyle=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,letterSpacing=0.sp,color=colors.onSurfaceVariant)
    val headingStyle=MaterialTheme.typography.labelMedium.copy(fontSize=12.sp,letterSpacing=0.sp,color=colors.onSurfaceVariant)
    val secondaryStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp,fontWeight=FontWeight.Medium,fontFeatureSettings="tnum",letterSpacing=0.sp,color=colors.onSurfaceVariant)
    val tint=if(colors.surface.luminance()<.5f) colors.primary.copy(alpha=.10f).compositeOver(colors.surface) else colors.primaryContainer
    fun width(text: String, style: TextStyle)=with(density) { measurer.measure(AnnotatedString(text),style,softWrap=false).size.width.toDp() }
    fun estimated(d: ReportMetricDescriptor)=if(d.value!=null && d.source in setOf(ReportSource.ESTIMATED,ReportSource.MIXED)) " ≈" else ""
    val rows=entries.filter { it.series!=null }.groupBy { it.series!! }.map { (series,family) ->
        val first=family.first()
        val average=family.firstOrNull { it.aggregation==ReportAggregation.AVERAGE }
        fun extreme(kind: ReportAggregation): Pair<String,String> {
            val value=family.firstOrNull { it.aggregation==kind }?.performanceValue(imperial,locale,resistanceRange) ?: ("—" to "")
            return value.first to if(series==SeriesMetric.RESISTANCE) value.second else ""
        }
        PerformanceRow(series.name,resources.getString(first.metric?.let(::metricLabel) ?: R.string.steps_total)+estimated(first),
            average?.performanceValue(imperial,locale,resistanceRange) ?: ("—" to first.reportValue(imperial,locale).second),
            extreme(ReportAggregation.MAXIMUM),extreme(ReportAggregation.MINIMUM),first.metric==MetricId.PACE)
    }
    val headings=listOf(stringResource(R.string.report_metric_heading),stringResource(R.string.average_value),stringResource(R.string.maximum_value),stringResource(R.string.minimum_value))
    val slowest=stringResource(R.string.report_slowest_short)
    val fastest=stringResource(R.string.report_fastest_short)
    Surface(Modifier.fillMaxWidth().testTag("report-performance"),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,colors.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val title=stringResource(R.string.report_performance)
                val titleStyle=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold)
                if(width(title,titleStyle)+width(status,headingStyle)+24.dp<=maxWidth) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(title,Modifier.weight(1f),style=titleStyle)
                        Text(status,style=headingStyle)
                    }
                } else Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=titleStyle); Text(status,style=headingStyle)
                }
            }
            entries.filter { it.series==null }.forEach { d ->
                PerformanceScalar(context.reportLabel(d)+estimated(d),d.reportValue(imperial,locale),labelStyle,unitStyle,
                    MaterialTheme.typography.headlineSmall.copy(fontSize=26.sp,fontWeight=FontWeight.SemiBold,fontFeatureSettings="tnum",letterSpacing=0.sp),"performance-scalar-${d.key}")
            }
            if(rows.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val averageStyle=MaterialTheme.typography.headlineMedium.copy(fontSize=if(maxWidth/density.fontScale>=420.dp) 30.sp else 26.sp,
                    fontWeight=FontWeight.SemiBold,fontFeatureSettings="tnum",letterSpacing=0.sp,color=colors.onSurface)
                fun valueWidth(value: Pair<String,String>,style: TextStyle)=width(value.first,style)+if(value.second.isBlank()) 0.dp else 5.dp+width(value.second,unitStyle)
                // Measure real localized text, units and digits at the user's font size. No autoshrink.
                val needed=listOf(
                    maxOf(width(headings[0],headingStyle),rows.maxOf { width(it.label,labelStyle) })+12.dp,
                    maxOf(width(headings[1],headingStyle),rows.maxOf { valueWidth(it.average,averageStyle) })+20.dp,
                    maxOf(width(headings[2],headingStyle),rows.maxOf { valueWidth(it.maximum,secondaryStyle) },if(rows.any { it.pace }) width(slowest,headingStyle) else 0.dp)+16.dp,
                    maxOf(width(headings[3],headingStyle),rows.maxOf { valueWidth(it.minimum,secondaryStyle) },if(rows.any { it.pace }) width(fastest,headingStyle) else 0.dp)+16.dp)
                val required=needed.fold(0.dp) { sum,v -> sum+v }
                if(required<=maxWidth) {
                    val extra=maxWidth-required
                    val columns=needed.mapIndexed { i,v -> v+extra*listOf(.25f,.35f,.20f,.20f)[i] }
                    Box(Modifier.fillMaxWidth().testTag("performance-layout-table")) {
                        Box(Modifier.matchParentSize().padding(start=columns[0],end=columns[2]+columns[3]).background(tint,RoundedCornerShape(8.dp)))
                        Column {
                            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                headings.forEachIndexed { i,label ->
                                    Box(Modifier.width(columns[i]),contentAlignment=if(i==0) Alignment.CenterStart else Alignment.Center) {
                                        Text(label,style=headingStyle,color=if(i==1) colors.primary else colors.onSurfaceVariant,fontWeight=if(i==1) FontWeight.SemiBold else FontWeight.Medium)
                                    }
                                }
                            }
                            rows.forEach { row ->
                                HorizontalDivider(color=colors.outlineVariant.copy(alpha=.75f))
                                Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(vertical=12.dp).testTag("performance-family-${row.key}"),verticalAlignment=Alignment.CenterVertically) {
                                    Text(row.label,Modifier.width(columns[0]).padding(end=8.dp),style=labelStyle)
                                    Box(Modifier.width(columns[1]),contentAlignment=Alignment.Center) { PerformanceValue(row.average,averageStyle,unitStyle,"performance-${row.key}-average") }
                                    PerformanceExtreme(row.maximum,if(row.pace) slowest else null,columns[2],secondaryStyle,unitStyle,headingStyle,"performance-${row.key}-maximum")
                                    PerformanceExtreme(row.minimum,if(row.pace) fastest else null,columns[3],secondaryStyle,unitStyle,headingStyle,"performance-${row.key}-minimum")
                                }
                            }
                        }
                    }
                } else Column(Modifier.testTag("performance-layout-stacked")) {
                    rows.forEachIndexed { index,row ->
                        if(index>0) HorizontalDivider(Modifier.padding(vertical=12.dp))
                        Column(Modifier.fillMaxWidth().testTag("performance-family-${row.key}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(row.label,style=labelStyle,fontWeight=FontWeight.Medium)
                            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp),color=tint) {
                                Box(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                                    PerformanceScalar(headings[1],row.average,labelStyle.copy(color=colors.primary),unitStyle,averageStyle,"performance-${row.key}-average")
                                }
                            }
                            PerformanceScalar(if(row.pace) slowest else headings[2],row.maximum,labelStyle,unitStyle,secondaryStyle,"performance-${row.key}-maximum")
                            PerformanceScalar(if(row.pace) fastest else headings[3],row.minimum,labelStyle,unitStyle,secondaryStyle,"performance-${row.key}-minimum")
                        }
                    }
                }
            }
            if(noSets) Text(stringResource(R.string.report_no_sets),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
}

@Composable private fun PerformanceExtreme(value: Pair<String,String>,label: String?,width: Dp,style: TextStyle,unitStyle: TextStyle,labelStyle: TextStyle,tag: String) {
    Column(Modifier.width(width).padding(horizontal=6.dp).testTag(tag),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        if(label!=null) Text(label,style=labelStyle)
        PerformanceValue(value,style,unitStyle)
    }
}

@Composable private fun PerformanceScalar(label: String,value: Pair<String,String>,labelStyle: TextStyle,unitStyle: TextStyle,valueStyle: TextStyle,tag: String) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    fun width(text: String,style: TextStyle)=with(density) { measurer.measure(AnnotatedString(text),style,softWrap=false).size.width.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val numberWidth=width(value.first,valueStyle)+if(value.second.isBlank()) 0.dp else 5.dp+width(value.second,unitStyle)
        if(width(label,labelStyle)+numberWidth+20.dp<=maxWidth) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f),style=labelStyle)
                PerformanceValue(value,valueStyle,unitStyle)
            }
        } else Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,style=labelStyle)
            PerformanceValue(value,valueStyle,unitStyle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PerformanceValue(value: Pair<String,String>,style: TextStyle,unitStyle: TextStyle,tag: String?=null) {
    // Units sit immediately beside the number; only genuinely narrow large-text layouts reflow.
    FlowRow(if(tag==null) Modifier else Modifier.testTag(tag),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(value.first,Modifier.alignByBaseline(),style=style)
        if(value.second.isNotBlank()) Text(value.second,Modifier.alignByBaseline(),style=unitStyle)
    }
}
