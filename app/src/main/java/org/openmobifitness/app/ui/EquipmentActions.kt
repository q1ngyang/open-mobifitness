package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*

@Composable internal fun PlanCompletionDialog(c: Controller,state: ExerciseState,link: LinkState) {
    val session=state.session ?: return
    if(!session.completionPending) return
    val context=LocalContext.current
    val writable=(state.demo || link.writable) && c.range()!=null
    Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false,usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),contentAlignment=Alignment.Center) {
            val height=maxHeight
            val horizontal=maxWidth>=600.dp && height/LocalDensity.current.fontScale<500.dp
            Surface(Modifier.widthIn(max=if(horizontal) 760.dp else 440.dp).fillMaxWidth().heightIn(max=height).testTag("plan-completion"),shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface) {
                val summary: @Composable (Modifier)->Unit = { modifier ->
                    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        // The symbol is beside the combined title/subtitle block, centered vertically.
                        Row(Modifier.fillMaxWidth().testTag("completion-heading"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f).testTag("completion-heading-text"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.plan_finished),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                Text(stringResource(R.string.plan_finished_subtitle,session.workoutTitle,state.selected?.steps?.size ?: 0),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(48.dp).testTag("completion-check")) {
                                Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Check,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary) }
                            }
                        }
                        HorizontalDivider()
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val metrics=listOf(MetricId.TIME,MetricId.CALORIES,when(session.machine) { Machine.JUMP_ROPE->MetricId.JUMPS; Machine.DUMBBELL->MetricId.REPETITIONS; else->MetricId.DISTANCE })
                            val narrow=maxWidth/LocalDensity.current.fontScale<260.dp
                            val item: @Composable (MetricId,Modifier)->Unit = { metric,modifier ->
                                val reading=context.reading(metric,c)
                                Column(modifier.testTag("completion-stat-${metric.name}"),verticalArrangement=Arrangement.spacedBy(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    NumberText(reading.value,28)
                                    Text(reading.label+(if(reading.estimated) " ≈" else "")+(if(reading.unit.isNotEmpty()) " ${reading.unit}" else ""),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                                }
                            }
                            if(narrow) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                item(metrics.first(),Modifier.fillMaxWidth())
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { metrics.drop(1).forEach { item(it,Modifier.weight(1f)) } }
                            }
                            else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { metrics.forEach { item(it,Modifier.weight(1f)) } }
                        }
                    }
                }
                val actions: @Composable (Modifier)->Unit = { modifier ->
                    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(if(state.error==R.string.storage_failed) R.string.plan_save_error else R.string.plan_choice_help),color=if(state.error==R.string.storage_failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                        if(!c.canStart()) Text(stringResource(R.string.connection_lost),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        Button(onClick={ c.finish() },enabled=!state.finishing,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("plan-finish")) {
                            Text(stringResource(if(state.finishing) R.string.plan_saving else R.string.plan_view_details),textAlign=TextAlign.Center)
                        }
                        OutlinedButton(onClick={ c.continueManually() },enabled=!state.finishing && c.canStart(),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("plan-continue")) {
                            Text(stringResource(if(writable) R.string.plan_continue_manual else R.string.plan_continue),textAlign=TextAlign.Center)
                        }
                        Text(stringResource(R.string.plan_same_record),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                        Text(stringResource(R.string.plan_pause_equipment),Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                    }
                }
                if(horizontal) Row(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically) {
                    summary(Modifier.weight(1f))
                    actions(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                } else Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    summary(Modifier.weight(1f,false))
                    HorizontalDivider()
                    actions(Modifier)
                }
            }
        }
    }
}

@Composable internal fun ManualContinuation(state: ExerciseState,modifier: Modifier=Modifier) {
    val session=state.session ?: return
    InstrumentPanel(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Portrait is already inside a scrolling column. Only fixed-height
        // landscape/tablet cards need their own bounded scrolling viewport.
        val scrolling=if(constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.padding(18.dp).then(scrolling),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.manual_continuation),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            NumberText(WorkoutService.elapsed(session.elapsedMs),60)
            Text(session.workoutTitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(stringResource(R.string.plan_time)+"  "+WorkoutService.elapsed(session.planCompletedMs ?: 0),style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.manual_time)+"  "+WorkoutService.elapsed((session.elapsedMs-(session.planCompletedMs ?: 0)).coerceAtLeast(0)),style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.plan_same_record),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}

@Composable internal fun EquipmentFold(c: Controller,state: ExerciseState,link: LinkState,now: Long) {
    if(link.phase!="ready" || !link.deviceDetails.supportFold) return
    var reviewed by remember(link.address) { mutableStateOf<Pair<Int,Long>?>(null) }
    val allowed=remember(state,link,now) { c.foldAllowed() }
    val pending=link.foldPhase in setOf("sending","waiting","reading")
    val retry=link.foldPhase in setOf("timeout","error") || link.deviceDetails.foldedState !in 0..1
    val label=when {
        link.foldPhase=="timeout" -> R.string.fold_timeout
        link.foldPhase=="error" -> R.string.fold_failed
        pending -> if(link.foldPhase=="reading") R.string.fold_unknown else R.string.fold_pending
        !allowed && !retry -> R.string.fold_blocked
        retry -> R.string.fold_unknown
        else -> R.string.fold_ready
    }
    Surface(Modifier.fillMaxWidth().testTag("equipment-fold"),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Settings,null,tint=MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.fold_title),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            }
            Text(stringResource(label),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={ if(retry) c.ble.refreshFoldState() else link.deviceDetails.foldedState?.let { reviewed=it to c.ble.connectionId } },enabled=!pending && (retry || allowed),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("fold-action")) {
                Text(stringResource(if(retry) R.string.fold_refresh else R.string.fold_action),textAlign=TextAlign.Center)
            }
        }
    }
    reviewed?.let { (expected,connection) ->
        AlertDialog(onDismissRequest={ reviewed=null },title={ Text(stringResource(R.string.fold_confirm)) },text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(link.name,fontWeight=FontWeight.SemiBold)
                Text(stringResource(R.string.fold_help))
            }
        },confirmButton={ TextButton(onClick={ reviewed=null; c.foldEquipment(expected,connection) },enabled=allowed && link.deviceDetails.foldedState==expected && connection==c.ble.connectionId && link.foldPhase=="idle",modifier=Modifier.testTag("fold-confirm")) { Text(stringResource(R.string.fold_action)) } },dismissButton={ TextButton(onClick={ reviewed=null }) { Text(stringResource(R.string.cancel)) } })
    }
}
