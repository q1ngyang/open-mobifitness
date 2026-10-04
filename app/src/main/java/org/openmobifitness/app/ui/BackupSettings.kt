@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*

private fun groupLabel(group: PreferenceGroup)=when(group) {
    PreferenceGroup.APPEARANCE -> R.string.backup_appearance
    PreferenceGroup.METRICS -> R.string.backup_metrics
    PreferenceGroup.HINTS -> R.string.personal_hints
    PreferenceGroup.DEVICES -> R.string.backup_devices
    PreferenceGroup.OVERLAY -> R.string.floating_settings
}
@Composable internal fun BackupSettings(activity: MainActivity,c: Controller) {
    var include by rememberSaveable { mutableStateOf(true) }
    val state by c.state.collectAsStateWithLifecycle()
    SettingsGroup(stringResource(R.string.export_backup)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_all_users),style=MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable { include=!include },verticalAlignment=Alignment.CenterVertically) {
                Checkbox(include,{ include=it }); Text(stringResource(R.string.backup_shared_optional),Modifier.weight(1f))
            }
            if(include) Text(stringResource(R.string.user_shared_settings),Modifier.padding(start=16.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.session!=null) Text(stringResource(R.string.backup_active),style=MaterialTheme.typography.bodySmall)
            Button(onClick={ activity.includeBackupPreferences=include; activity.export("backup") },enabled=state.session==null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("export-backup")) { Text(stringResource(R.string.export_backup)) }
        }
    }
    OutlinedButton(onClick={ activity.export("workouts") },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_workouts)) }
    OutlinedButton(onClick=activity::importFile,enabled=state.session==null,modifier=Modifier.fillMaxWidth().testTag("import-backup")) { Text(stringResource(R.string.import_file)) }
}
@Composable internal fun BackupDialogs(activity: MainActivity,c: Controller) {
    val exercise by c.state.collectAsStateWithLifecycle()
    val preview by c.backup.preview.collectAsStateWithLifecycle()
    val status by c.backup.status.collectAsStateWithLifecycle()
    preview?.let { staged ->
        var overwriteProfiles by remember(staged) { mutableStateOf(false) }
        var groups by remember(staged) { mutableStateOf(emptySet<PreferenceGroup>()) }
        val p=staged.preview
        Dialog(onDismissRequest=c.backup::discard,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.widthIn(max=620.dp).fillMaxWidth(.94f).heightIn(max=800.dp).safeDrawingPadding(),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.import_preview),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()).padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.import_summary,p.sessions,p.samples,p.workouts),style=MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.backup_profile_summary,p.users,p.removedUsers),style=MaterialTheme.typography.bodyMedium)
                        if(p.users>0) Row(Modifier.fillMaxWidth().clickable { overwriteProfiles=!overwriteProfiles },verticalAlignment=Alignment.CenterVertically) { Checkbox(overwriteProfiles,{ overwriteProfiles=it }); Text(stringResource(R.string.backup_overwrite_profiles),Modifier.weight(1f)) }
                        Text(stringResource(R.string.backup_removed_priority),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            listOf(R.string.backup_new to p.added,R.string.backup_duplicate to p.duplicates,R.string.backup_conflict to p.conflicts).forEach { (label,count) ->
                                Column { Text(stringResource(label),style=MaterialTheme.typography.labelLarge); Text(count.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
                            }
                        }
                        Text(stringResource(if(p.conflicts>0) R.string.backup_conflict_help else R.string.backup_duplicates_help),color=if(p.conflicts>0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(staged.preferences!=null) {
                            HorizontalDivider()
                            Text(stringResource(R.string.restore_preferences),style=MaterialTheme.typography.titleMedium)
                            (if(PortablePreferences.validate(staged.preferences).getInt("version")==2) listOf(PreferenceGroup.DEVICES) else PreferenceGroup.entries).forEach { group ->
                                Row(Modifier.fillMaxWidth().clickable { groups=if(group in groups) groups-group else groups+group },verticalAlignment=Alignment.CenterVertically) {
                                    Checkbox(group in groups,{ groups=if(it) groups+group else groups-group })
                                    Text(stringResource(groupLabel(group)),Modifier.weight(1f))
                                }
                            }
                            if(groups.isNotEmpty()) Text(stringResource(R.string.backup_overwrite),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick=c.backup::discard,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                        Button(onClick={ c.backup.restore(groups,overwriteProfiles) },enabled=p.conflicts==0L && c.dataActionsAllowed,modifier=Modifier.weight(1f).testTag("confirm-restore")) { Text(stringResource(R.string.confirm)) }
                    }
                }
            }
        }
    }
    status?.let { current ->
        val busy=current.cancellable || current.phase==TransferPhase.FINALIZING
        val title=when(current.phase) {
            TransferPhase.READING -> R.string.backup_reading; TransferPhase.EXPORTING -> R.string.backup_exporting; TransferPhase.RESTORING -> R.string.backup_restoring
            TransferPhase.FINALIZING -> R.string.backup_finalizing; TransferPhase.COMPLETE -> R.string.backup_complete; TransferPhase.CANCELLED -> R.string.backup_cancelled; TransferPhase.FAILED -> R.string.backup_failed_title
        }
        AlertDialog(onDismissRequest={ if(!busy) c.backup.dismiss() },title={ Text(stringResource(title)) },text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(current.phase in setOf(TransferPhase.READING,TransferPhase.EXPORTING)) Text("%.1f MB".format(current.progress/1048576.0))
                if(current.phase==TransferPhase.RESTORING) Text(current.progress.toString())
                if(current.phase==TransferPhase.FAILED) Text(stringResource(when(current.error) { "preferences" -> R.string.backup_preferences_pending; "conflict" -> R.string.backup_conflict_help; "capacity" -> R.string.backup_capacity; "space" -> R.string.backup_space; else -> R.string.import_failed }))
                if(current.restoredPreferences) Text(stringResource(R.string.backup_preferences_done))
            }
        },confirmButton={
            if(current.cancellable) TextButton(onClick=c.backup::cancel) { Text(stringResource(R.string.cancel)) }
            else if(!busy) TextButton(onClick=c.backup::dismiss) { Text(stringResource(R.string.close)) }
        })
    }
}
