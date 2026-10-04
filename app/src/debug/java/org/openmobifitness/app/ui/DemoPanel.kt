@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.core.Machine

@Composable internal fun DemoPanel(activity: MainActivity,c: Controller,state: ExerciseState) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val samples=remember(c) { DebugSamples(c) }
    val progress by samples.progress.collectAsStateWithLifecycle()
    val revision by c.repo.revision.collectAsStateWithLifecycle()
    val maintenance by c.repo.maintenance.collectAsStateWithLifecycle()
    val pending by c.pendingStart.collectAsStateWithLifecycle()
    val transfer by c.backup.status.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf(DebugSampleInfo()) }
    var preview by remember { mutableStateOf<DebugClearPreview?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(revision) { runCatching { samples.info() }.onSuccess { info=it } }
    fun failed(e: Throwable) { error=when(e.message) { "debug_sample_conflict","debug_user_deleted" -> R.string.debug_samples_conflict; "duplicate_user_name" -> R.string.user_duplicate; "identity_locked" -> R.string.debug_samples_locked; else -> R.string.debug_samples_failed } }
    val enabled=!busy && progress==null && !maintenance && pending==null && c.dataActionsAllowed
    SettingsGroup(stringResource(R.string.demo)+" · Debug") {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.demo_description),style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL).forEach { machine -> FilterChip(state.demo && state.demoMachine==machine,onClick={ c.setDemo(true,machine) },enabled=enabled,label={ Text(machineName(machine)) }) } }
            OutlinedButton(onClick={ val value=!state.demo; c.setDemo(value); if(value) activity.page.value=0 },enabled=enabled) { Text(stringResource(if(state.demo) R.string.exit_demo else R.string.start_demo)) }
            HorizontalDivider()
            Text(stringResource(R.string.debug_samples_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.debug_samples_count,info.profiles,info.sessions),style=MaterialTheme.typography.labelLarge)
            if(progress!=null) { LinearProgressIndicator(progress={ (progress ?: 0)/100f },modifier=Modifier.fillMaxWidth()); Text(stringResource(R.string.debug_samples_working,progress ?: 0)) }
            else if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            if(!enabled && !busy && progress==null) Text(stringResource(R.string.debug_samples_locked),style=MaterialTheme.typography.bodySmall)
            error?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error) }
            FilledTonalButton(enabled=enabled,onClick={
                busy=true; error=null
                val names=listOf(R.string.debug_user_a,R.string.debug_user_b,R.string.debug_user_c,R.string.debug_user_d).map(context::getString)
                scope.launch { try { info=samples.load(names) } catch(e: Exception) { failed(e) } finally { busy=false } }
            },modifier=Modifier.fillMaxWidth().testTag("debug-load-samples")) { Text(stringResource(R.string.debug_samples_load)) }
            OutlinedButton(enabled=enabled && (info.sessions>0 || info.profiles>0),onClick={ busy=true; error=null; scope.launch { try { preview=samples.preview() } catch(e: Exception) { failed(e) } finally { busy=false } } },modifier=Modifier.fillMaxWidth().testTag("debug-clear-samples")) { Text(stringResource(R.string.debug_samples_clear)) }
            if(info.sessions>0) TextButton(onClick={ c.historyScope.value="all"; c.historyRequest.value=HistoryQuery(from=info.from,until=info.until,source=2,owner="all"); activity.page.value=1 },modifier=Modifier.testTag("debug-view-samples")) { Text(stringResource(R.string.debug_samples_view)) }
        }
    }
    preview?.let { value -> AlertDialog(onDismissRequest={ if(!busy) preview=null },title={ Text(stringResource(R.string.debug_samples_clear)) },text={ Text(stringResource(R.string.debug_samples_preview,value.ids.size,value.removeUsers.size,value.keptUsers.size)) },confirmButton={ TextButton(enabled=enabled,onClick={ busy=true; error=null; scope.launch { try { info=samples.clear(value); preview=null } catch(e: Exception) { failed(e); preview=null } finally { busy=false } } }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(enabled=!busy,onClick={ preview=null }) { Text(stringResource(R.string.cancel)) } }) }
}
