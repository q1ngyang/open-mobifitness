@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.core.*

internal fun connectionStatus(link: LinkState)=when(link.phase) {
    "ready"->R.string.connected
    "disconnected"->R.string.disconnected
    "awaiting_data"->R.string.waiting_data_help
    "subscription_failed"->R.string.subscription_failed
    else->R.string.connecting
}
@Composable internal fun DevicesScreen(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val saved by c.local.devices.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavedDevice?>(null) }
    val found by c.ble.found.collectAsStateWithLifecycle()
    val scanning by c.ble.scanning.collectAsStateWithLifecycle()
    val heart by c.heart.state.collectAsStateWithLifecycle()
    var help by rememberSaveable { mutableStateOf(false) }
    val now by produceState(SystemClock.elapsedRealtime()) { while(true) { delay(1000); value=SystemClock.elapsedRealtime() } }
    val fresh=link.motionAt>0 && now-link.motionAt<5000
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        PageTitle(stringResource(R.string.devices))
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth>=780.dp || (maxWidth>=480.dp && maxHeight<440.dp)
            val connection: @Composable ()->Unit = {
                SettingsGroup(stringResource(R.string.equipment_connection)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer) { MachineGlyph(link.machine.takeUnless { it==Machine.UNKNOWN } ?: Machine.ELLIPTICAL,Modifier.padding(14.dp)) }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(saved.firstOrNull { it.address==link.address }?.note?.takeIf { it.isNotBlank() } ?: if(link.name.isBlank()) stringResource(R.string.connect_equipment) else link.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(stringResource(connectionStatus(link))+(if(link.protocol==Protocol.UNKNOWN) "" else " · ${link.protocol}"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        saved.firstOrNull { it.address==link.address }?.let { device ->
                            TextButton(onClick={ editing=device },modifier=Modifier.align(Alignment.End)) { Text(stringResource(R.string.device_note)) }
                        }
                        if(link.phase!="disconnected") {
                            HorizontalDivider()
                            CapabilityLine(stringResource(R.string.motion_data),stringResource(if(fresh) R.string.receiving_data else R.string.waiting_motion),fresh)
                            CapabilityLine(stringResource(R.string.resistance_control),stringResource(if(link.controlTimedOut) R.string.feedback_timeout else if(link.writable && link.range!=null) R.string.control_available else R.string.read_only),link.writable && link.range!=null && !link.controlTimedOut)
                            if(link.metrics.heartBpm!=null && link.heartAt>0 && now-link.heartAt<10000) CapabilityLine(stringResource(R.string.heart_rate),stringResource(R.string.heart_from_equipment,link.metrics.heartBpm!!),true)
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(link.range?.let { stringResource(R.string.range_format,org.openmobifitness.app.service.WorkoutService.number(it.min),org.openmobifitness.app.service.WorkoutService.number(it.max)) }.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick=activity::disconnect) { Text(stringResource(R.string.disconnect)) }
                            }
                        } else {
                            Text(stringResource(R.string.scan_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick=activity::scan,enabled=!scanning) { Icon(Icons.Default.Search,null); Spacer(Modifier.width(6.dp)); Text(stringResource(if(scanning) R.string.scanning else R.string.scan)) }
                                if(c.prefs.contains("last_address")) OutlinedButton(onClick=activity::reconnect,enabled=state.session==null || !state.demo) { Text(stringResource(R.string.reconnect)) }
                            }
                        }
                    }
                }
                if(saved.any { it.address!=link.address && !it.heart }) SettingsGroup(stringResource(R.string.saved_devices)) {
                    saved.filter { it.address!=link.address && !it.heart }.forEach { device -> SavedDeviceRow(activity,c,state,device) { editing=device } }
                }
                if(heart.phase=="disconnected") SettingsGroup(stringResource(R.string.heart_device)) {
                    if(saved.any { it.heart }) saved.filter { it.heart }.forEach { device -> SavedDeviceRow(activity,c,state,device) { editing=device } }
                    else SettingsRow(Icons.Default.Favorite,stringResource(R.string.add_heart),stringResource(R.string.optional_heart)) { activity.scan() }
                }
                if(heart.phase!="disconnected") SettingsGroup(stringResource(R.string.heart_device)) {
                    Row(Modifier.fillMaxWidth().testTag("heart-accessory").padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Favorite,null,tint=MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(heart.name.ifBlank { stringResource(R.string.heart_device) },fontWeight=FontWeight.Medium)
                            Text(stringResource(connectionStatus(heart)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(heart.metrics.heartBpm?.takeIf { heart.heartAt>0 && now-heart.heartAt<10000 }?.let { "$it bpm" } ?: stringResource(R.string.waiting_heart),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(heart.phase!="disconnected") IconButton(onClick={ c.heart.disconnect() }) { Icon(Icons.Default.Close,stringResource(R.string.disconnect)) }
                    }
                }
            }
            val support: @Composable ()->Unit = {
                SettingsGroup("") {
                    SettingsRow(Icons.Default.Info,stringResource(R.string.connection_help),null,"connection-help") { help=true }
                }
                Text(stringResource(R.string.hardware_validation),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(BuildConfig.DEBUG) DemoPanel(activity,c,state)
            }
            val nearbyHeader: @Composable ()->Unit = {
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.nearby_devices),Modifier.align(Alignment.CenterVertically),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    TextButton(onClick={ if(scanning) c.ble.stopScan() else activity.scan() }) { Icon(if(scanning) Icons.Default.Close else Icons.Default.Refresh,null,Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(if(scanning) R.string.stop_scan else R.string.scan)) }
                }
                if(scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val entries=found.filter { it.address!=link.address && it.address!=heart.address }
            val nearby: androidx.compose.foundation.lazy.LazyListScope.()->Unit = {
                item { nearbyHeader() }
                if(entries.isEmpty()) item { Text(stringResource(if(scanning) R.string.scanning else R.string.no_devices),Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                items(entries,key={ it.address }) { device ->
                    OutlinedCard(onClick={ activity.connect(device) },enabled=state.session==null || device.heart || device.address==link.address,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(device.heart) Icon(Icons.Default.Favorite,null) else MachineGlyph(Protocols.machine(device.name))
                            Column(Modifier.weight(1f)) { Text(device.name.ifBlank { stringResource(R.string.unknown_device) },fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis); Text(if(device.heart) stringResource(R.string.heart_rate) else "${device.rssi} dBm",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.connect),tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Text(stringResource(R.string.permission_help),Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if(wide) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("device-connections").padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { connection(); support() }
                LazyColumn(Modifier.weight(1f).testTag("device-list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=nearby)
            } else LazyColumn(Modifier.fillMaxSize().testTag("device-list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { item { Column(verticalArrangement=Arrangement.spacedBy(16.dp)) { connection() } }; nearby(); item { Column(verticalArrangement=Arrangement.spacedBy(16.dp)) { support() } } }
        }
    }
    editing?.let { device ->
        var note by remember(device.address) { mutableStateOf(device.note) }
        FormDialog(stringResource(R.string.device_note),{ editing=null },{ c.local.rename(device.address,note); editing=null }) {
                Text(device.name)
                OutlinedTextField(note,{ if(it.length<=80) note=it },label={ Text(stringResource(R.string.device_note)) },modifier=Modifier.fillMaxWidth().testTag("device-note-input"))
                TextButton(onClick={ c.local.remove(device.address); editing=null }) { Text(stringResource(R.string.remove_device)) }
        }
    }
    if(help) ModalBottomSheet(onDismissRequest={ help=false },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_help),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            TroubleshootingSteps(false)
            Text(stringResource(R.string.heart_source_help_title),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.heart_source_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.check_feedback),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={ help=false; activity.openDiagnostics() },modifier=Modifier.fillMaxWidth().testTag("open-diagnostics")) { Text(stringResource(R.string.open_diagnostics)) }
            OutlinedButton(onClick=activity::feedback,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable private fun CapabilityLine(title: String,value: String,available: Boolean) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(if(available) Icons.Default.Check else Icons.Default.Info,null,Modifier.size(18.dp),tint=if(available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun TroubleshootingSteps(diagnostic: Boolean) {
    val steps=if(diagnostic) listOf(R.string.capture_step1,R.string.capture_step2,R.string.capture_step3) else listOf(R.string.connect_step1,R.string.connect_step2,R.string.connect_step3)
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        steps.forEachIndexed { i,id -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp)) { Box(Modifier.size(28.dp),contentAlignment=Alignment.Center) { Text((i+1).toString(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold) } }
            Text(stringResource(id),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        } }
    }
}
@Composable private fun DemoPanel(activity: MainActivity,c: Controller,state: ExerciseState) {
    SettingsGroup(stringResource(R.string.demo)+" · Debug") {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.demo_description),style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL).forEach { m -> FilterChip(state.demo && state.demoMachine==m,onClick={ c.setDemo(true,m) },enabled=state.session==null,label={ Text(machineName(m)) }) } }
            OutlinedButton(onClick={ val enabled=!state.demo; c.setDemo(enabled); if(enabled) activity.page.value=0 },enabled=state.session==null) { Text(stringResource(if(state.demo) R.string.exit_demo else R.string.start_demo)) }
        }
    }
}

@Composable private fun SavedDeviceRow(activity: MainActivity,c: Controller,state: ExerciseState,device: SavedDevice,edit: ()->Unit) {
    val link by c.ble.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(device.heart) Icon(Icons.Default.Favorite,null) else MachineGlyph(device.machine)
            Column(Modifier.weight(1f)) {
                Text(device.note.ifBlank { device.name },style=MaterialTheme.typography.titleSmall)
                if(device.note.isNotBlank()) Text(device.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=edit,modifier=Modifier.testTag("device-note-${device.address}")) { Icon(Icons.Default.MoreVert,stringResource(R.string.device_note)) }
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(device.heart) R.string.optional_heart else R.string.replace_equipment),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={ activity.connect(org.openmobifitness.app.ble.FoundDevice(device.address,device.name,0,device.heart)) },enabled=device.heart || state.session==null && !state.starting || link.address==device.address && !state.demo) { Text(stringResource(R.string.connect)) }
        }
    }
}
