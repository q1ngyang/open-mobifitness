@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.*
import java.time.LocalDate
import java.util.Locale
import kotlin.math.sqrt

private val HeroInk=Color(0xFF14263A)
private val HeroSecondary=Color(0xFFCCD5E1)

@Composable internal fun FreeTrainingCard(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState,compact: Boolean) {
    val user by c.currentUser.collectAsStateWithLifecycle()
    val revision by c.repo.revision.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val day by produceState(LocalDate.now()) { while(true) { delay(60000); value=LocalDate.now() } }
    var summary by remember(day) { mutableStateOf<HistoryOverview?>(null) }
    var failed by remember(day) { mutableStateOf(false) }
    LaunchedEffect(day,revision,state.ready,user?.id) {
        if(state.ready) runCatching { val dates=RecordDates.today(day); c.repo.history.overview(HistoryQuery(dates.first,dates.second,source=1,owner=user?.id ?: "none")) }
            .onSuccess { summary=it; failed=false }.onFailure { summary=null; failed=true; AppLog.exception("home_summary",it) }
    }
    Surface(color=HeroInk,contentColor=Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().testTag("free-training-card")) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val wide=maxWidth/LocalDensity.current.fontScale>=600.dp || (compact && maxWidth/LocalDensity.current.fontScale>=460.dp)
            val metricWidth=if(wide) (maxWidth-49.dp)/2 else maxWidth
            val ringRadius=(metricWidth*(if(wide) .20f else .28f)).coerceIn(if(compact) 48.dp else 54.dp,if(compact) 58.dp else 82.dp)
            val ringInset=if(wide) ringRadius+8.dp else 18.dp
            // Anchor the center above the card, rather than measuring an artwork
            // column in the content. On tablets the arcs stay inside the intro half.
            val ringLift=16.dp+if(compact) 10.dp else 4.dp
            val titleLineHeight=if(compact) 28.sp else 34.sp
            val safeRadius=ringRadius+8.dp
            val hintTop=with(LocalDensity.current) { titleLineHeight.toDp() }+8.dp
            val hintDistance=(hintTop+ringLift).value
            val hintInset=if(hintDistance>=safeRadius.value) 0.dp else
                (ringInset.value+sqrt(safeRadius.value*safeRadius.value-hintDistance*hintDistance)).dp
            val intro: @Composable ()->Unit = {
                Box(Modifier.fillMaxWidth()) {
                    Canvas(Modifier.matchParentSize()) {
                        clipRect(left=0f,top=-16.dp.toPx(),right=size.width+if(wide) 0f else 16.dp.toPx(),bottom=size.height) {
                            val center=Offset(size.width-ringInset.toPx(),-ringLift.toPx())
                            brandColors.forEachIndexed { i,color ->
                                drawCircle(color,ringRadius.toPx()*(28+18*i)/82f,center,style=Stroke(4.dp.toPx()))
                            }
                        }
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.free_training),Modifier.fillMaxWidth().padding(end=ringInset+safeRadius),fontSize=if(compact) 22.sp else 27.sp,lineHeight=titleLineHeight,fontWeight=FontWeight.Bold)
                            if(!compact && LocalDensity.current.fontScale<1.5f) Text(stringResource(R.string.free_training_help),Modifier.fillMaxWidth().padding(end=hintInset),style=MaterialTheme.typography.bodySmall,color=HeroSecondary)
                        }
                        Surface(onClick={ activity.page.value=2 },modifier=Modifier.fillMaxWidth().testTag("hero-device"),color=Color.White.copy(alpha=.08f),contentColor=Color.White,shape=RoundedCornerShape(12.dp)) {
                            Row(Modifier.heightIn(min=48.dp).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MachineGlyph(c.displayMachine())
                                Text((if(state.demo) stringResource(R.string.demo) else link.name.takeIf { it.isNotBlank() } ?: machineName(link.machine))+" · "+stringResource(if(state.demo) R.string.connected else connectionStatus(link)),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                                Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                            }
                        }
                        val buttonMeasurer=rememberTextMeasurer()
                        val buttonStyle=MaterialTheme.typography.labelLarge
                        val startLabel=stringResource(if(c.canStart()) R.string.start_recording else R.string.connect)
                        val controlLabel=stringResource(R.string.control_only)
                        val minimumButton=with(LocalDensity.current) { maxOf(buttonMeasurer.measure(startLabel,buttonStyle,maxLines=1).size.width.toDp()+45.dp,buttonMeasurer.measure(controlLabel,buttonStyle,maxLines=1).size.width.toDp()+20.dp) }
                        FlowRow(maxItemsInEachRow=if(metricWidth>=minimumButton*2+10.dp) 2 else 1,horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Button(onClick={ if(c.canStart()) { c.select(null); activity.startTraining() } else activity.page.value=2 },enabled=!state.starting,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("hero-start"),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0062CC),contentColor=Color.White),contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp)) {
                                Icon(if(c.canStart()) Icons.Default.PlayArrow else Icons.Default.Search,null,Modifier.size(20.dp)); Spacer(Modifier.width(5.dp))
                                Text(stringResource(if(c.canStart()) R.string.start_recording else R.string.connect),style=MaterialTheme.typography.labelLarge)
                            }
                            if(c.canStart()) OutlinedButton(onClick=activity::startControl,enabled=!state.starting,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("hero-control"),border=BorderStroke(1.dp,HeroSecondary),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White),contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp)) {
                                Text(stringResource(R.string.control_only),style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            val today: @Composable ()->Unit = {
                Column(Modifier.clickable { activity.page.value=1 }.testTag("hero-history"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.today_activity),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.history),Modifier.size(18.dp))
                        if(failed) Text(stringResource(R.string.summary_unavailable),Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelSmall,color=HeroSecondary)
                    }
                    val stats=summary
                    val empty=stats?.count==0
                    val values=listOf(stats?.let { (it.elapsedMs/60000).toString() } ?: "—",stats?.calories?.takeIf { stats.caloriesPresent==stats.count }?.let { String.format(Locale.getDefault(),"%.0f",it) } ?: if(empty) "0" else "—",stats?.count?.toString() ?: "—",stats?.distanceM?.takeIf { stats.distancePresent==stats.count }?.let { String.format(Locale.getDefault(),"%.1f",it/if(imperial) 1609.344 else 1000.0) } ?: if(empty) "0.0" else "—")
                    val labels=listOf(R.string.today_time,R.string.calories,R.string.today_count,R.string.distance)
                    val units=listOf(stringResource(R.string.minute_unit),"kcal",stringResource(R.string.session_unit),if(imperial) "mi" else "km")
                    val shownUnits=units.mapIndexed { i,unit -> (if(i==1 && stats?.estimated==true || i==3 && stats?.distanceEstimated==true) "≈ " else "")+unit }
                    val measurer=rememberTextMeasurer()
                    val numberStyle=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.Bold,fontFeatureSettings="tnum",color=Color.White)
                    val density=LocalDensity.current
                    // Keep all four statistics on one line, including accumulated totals.
                    // Larger accessibility fonts may scroll horizontally; they never wrap
                    // the card into a second metric row or truncate a number.
                    val labelStyle=MaterialTheme.typography.labelSmall
                    val minimumNumberStyle=numberStyle.copy(fontSize=15.sp)
                    val valueWidth=with(density) { values.maxOf { measurer.measure(it,minimumNumberStyle,maxLines=1).size.width }.toDp() }+8.dp
                    val unitWidth=with(density) { shownUnits.maxOf { measurer.measure(it,labelStyle,maxLines=1).size.width }.toDp() }+6.dp
                    val cellWidth=maxOf((metricWidth-3.dp)/4,valueWidth,unitWidth)
                    // Keep each localized word intact. When an icon and its label
                    // cannot share a line, align all four icons above the labels.
                    // The four metrics still occupy one horizontal row.
                    val labelTexts=labels.map { stringResource(it) }
                    val inlineLabels=with(density) { labelTexts.all { measurer.measure(it,labelStyle,maxLines=1).size.width.toDp()+26.dp<=cellWidth } }
                    val labelConstraints=Constraints(maxWidth=with(density) { (cellWidth-if(inlineLabels) 26.dp else 6.dp).roundToPx().coerceAtLeast(1) })
                    val labelTextHeight=with(density) { labelTexts.maxOf { measurer.measure(it,labelStyle,maxLines=2,constraints=labelConstraints).size.height }.toDp() }
                    val labelHeight=if(inlineLabels) maxOf(20.dp,labelTextHeight) else labelTextHeight+20.dp
                    // Android's enlarged fonts scale nonlinearly. Measure each
                    // candidate size instead of applying a linear pixel ratio.
                    val numberSize=remember(values,cellWidth,numberStyle,density) {
                        val available=with(density) { (cellWidth-8.dp).toPx() }
                        ((24 downTo 15).firstOrNull { size -> values.all { measurer.measure(it,numberStyle.copy(fontSize=size.sp),maxLines=1,softWrap=false).size.width<=available } } ?: 15).sp
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min)) {
                            values.indices.forEach { i ->
                                if(i>0) VerticalDivider(Modifier.fillMaxHeight().padding(vertical=8.dp),color=Color.White.copy(alpha=.14f))
                                Column(Modifier.width(cellWidth).padding(horizontal=3.dp,vertical=2.dp).testTag("today-metric-$i").semantics(mergeDescendants=true) {},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    if(inlineLabels) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterHorizontally),modifier=Modifier.fillMaxWidth().height(labelHeight)) {
                                        TodayIcon(i,brandColors[i])
                                        Text(labelTexts[i],Modifier.weight(1f,fill=false),style=labelStyle,color=HeroSecondary,textAlign=TextAlign.Center,maxLines=1)
                                    } else Column(Modifier.fillMaxWidth().height(labelHeight),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        TodayIcon(i,brandColors[i])
                                        Text(labelTexts[i],style=labelStyle,color=HeroSecondary,textAlign=TextAlign.Center,maxLines=2)
                                    }
                                    Text(values[i],Modifier.fillMaxWidth().testTag("today-value-$i"),style=numberStyle.copy(fontSize=numberSize,lineHeight=28.sp,textAlign=TextAlign.Center),maxLines=1,softWrap=false)
                                    Text(shownUnits[i],style=labelStyle,color=HeroSecondary,textAlign=TextAlign.Center,maxLines=1)
                                }
                            }
                    }
                }
            }
            if(wide) Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f).align(Alignment.Top)) { intro() }
                VerticalDivider(Modifier.height(120.dp),color=Color.White.copy(alpha=.15f))
                Box(Modifier.weight(1f)) { today() }
            } else Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { intro(); HorizontalDivider(color=Color.White.copy(alpha=.15f)); today() }
        }
    }
}

@Composable private fun TodayIcon(index: Int,color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val u=size.width/20
        when(index) {
            0->{ drawCircle(color); drawLine(HeroInk,Offset(10*u,4*u),Offset(10*u,10*u),2*u,StrokeCap.Round); drawLine(HeroInk,Offset(10*u,10*u),Offset(14*u,12*u),2*u,StrokeCap.Round) }
            1->{ val p=Path().apply { moveTo(11*u,0f); cubicTo(12*u,6*u,19*u,7*u,18*u,13*u); cubicTo(17*u,22*u,1*u,22*u,2*u,12*u); cubicTo(2*u,8*u,5*u,7*u,6*u,4*u); lineTo(8*u,10*u); cubicTo(11*u,8*u,9*u,4*u,11*u,0f); close() }; drawPath(p,color) }
            2->repeat(3) { i->drawRoundRect(color,Offset((2+6*i)*u,(11-5*i)*u),Size(4*u,(9+5*i)*u),androidx.compose.ui.geometry.CornerRadius(u,u)) }
            3->{ val p=Path().apply { moveTo(10*u,20*u); cubicTo(6*u,14*u,2*u,12*u,2*u,8*u); cubicTo(2*u,-2*u,18*u,-2*u,18*u,8*u); cubicTo(18*u,12*u,14*u,16*u,10*u,20*u); close() }; drawPath(p,color); drawCircle(HeroInk,3*u,Offset(10*u,8*u)) }
        }
    }
}
