@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*

@Composable internal fun SettingsScreen(activity: MainActivity,c: Controller) {
    val section by activity.settingsSection
    val user by c.currentUser.collectAsStateWithLifecycle()
    val policy by c.identityPolicy.collectAsStateWithLifecycle()
    var editUser by rememberSaveable { mutableStateOf(false) }
    val theme by c.theme.collectAsStateWithLifecycle()
    val imperial by c.imperial.collectAsStateWithLifecycle()
    val floating by c.display.floatingEnabled.collectAsStateWithLifecycle()
    val automatic by c.display.autoFloating.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    BackHandler(section.isNotEmpty()) { activity.settingsSection.value="" }
    val languages=listOf("" to stringResource(R.string.system),"zh-Hans" to "简体中文","zh-Hant" to "繁體中文","en" to "English","ja" to "日本語","ko" to "한국어","de" to "Deutsch")
    val title=when(section) { "users"->R.string.user_manage; "diagnostics"->R.string.diagnostics; "about"->R.string.about; "floating"->R.string.floating_settings; "display"->R.string.display_settings; "data"->R.string.data_management; else->R.string.settings }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        PageTitle(stringResource(title),if(section.isNotEmpty()) ({ activity.settingsSection.value="" }) else null)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide=maxWidth/androidx.compose.ui.platform.LocalDensity.current.fontScale>=800.dp
            Column(Modifier.widthIn(max=1080.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState()).testTag("settings-list").padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(section.isEmpty()) {
                    user?.let { current -> SettingsGroup("") {
                        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            UserAvatar(c,current.name,current.avatar,56.dp)
                            Column(Modifier.weight(1f)) { Text(current.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text(stringResource(R.string.user_current),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick={ editUser=true },enabled=c.dataActionsAllowed) { Icon(Icons.Default.Edit,stringResource(R.string.user_edit)) }
                        }
                    } }
                    val preferences: @Composable ()->Unit = {
                        SettingsGroup(stringResource(R.string.user_personal_settings)) {
                            SettingsRow(Icons.Default.Settings,stringResource(R.string.theme),stringResource(when(theme) { "light"->R.string.light; "dark"->R.string.dark; else->R.string.system }),"setting-theme",inlineValue=true,symbol=R.drawable.ic_theme) { dialog="theme" }
                            SettingsRow(Icons.Default.Settings,stringResource(R.string.units),stringResource(if(imperial) R.string.imperial else R.string.metric),"setting-units",inlineValue=true,symbol=R.drawable.ic_ruler) { dialog="units" }
                        }
                        SettingsGroup(stringResource(R.string.train)) {
                            SettingsRow(Icons.Default.List,stringResource(R.string.display_settings),stringResource(R.string.display_settings_summary),"setting-display",symbol=R.drawable.ic_display) { activity.settingsSection.value="display" }
                            SettingsRow(Icons.Default.Home,stringResource(R.string.floating_settings),stringResource(if(!floating) R.string.disabled else if(automatic) R.string.auto_floating else R.string.enabled),"setting-floating",symbol=R.drawable.ic_float) { activity.settingsSection.value="floating" }
                        }
                        SettingsGroup(stringResource(R.string.user_manage)) {
                            SettingsRow(Icons.Default.Person,stringResource(R.string.user_manage),tag="setting-users") { activity.settingsSection.value="users" }
                            SettingsRow(Icons.Default.Lock,stringResource(R.string.user_identity_policy),stringResource(when(policy) { IdentityPolicy.EACH_RECORDING -> R.string.user_policy_recording; IdentityPolicy.STARTUP -> R.string.user_policy_startup; IdentityPolicy.REMEMBER -> R.string.user_policy_remember })) { dialog="identity" }
                        }
                    }
                    val support: @Composable ()->Unit = {
                        SettingsGroup(stringResource(R.string.user_shared_settings)) {
                            SettingsRow(Icons.Default.Settings,stringResource(R.string.language),languages.firstOrNull { it.first==activity.currentLanguage() }?.second ?: stringResource(R.string.system),"setting-language",inlineValue=true,symbol=R.drawable.ic_language) { dialog="language" }
                        }
                        SettingsGroup(stringResource(R.string.support_group)) {
                            SettingsRow(Icons.Default.List,stringResource(R.string.data_management),stringResource(R.string.data_management_summary),"setting-data") { activity.settingsSection.value="data" }
                            SettingsRow(Icons.Default.Settings,stringResource(R.string.diagnostics),stringResource(R.string.diagnostics_summary),"setting-diagnostics",onClick=activity::openDiagnostics)
                            SettingsRow(Icons.Default.Email,stringResource(R.string.feedback),stringResource(R.string.feedback_summary),"setting-feedback",onClick=activity::feedback)
                        }
                        SettingsGroup(stringResource(R.string.about)) {
                            SettingsRow(Icons.Default.Info,"OpenMOBI",BuildConfig.VERSION_NAME,"setting-about") { activity.settingsSection.value="about" }
                        }
                    }
                    if(wide) Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) { preferences() }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) { support() }
                    } else { preferences(); support() }
                } else when(section) {
                    "users"->UserManagement(c)
                    "diagnostics"->DiagnosticsSettings(activity,c)
                    "about"->AboutSettings(activity,c)
                    "floating"->SettingsGroup(stringResource(R.string.floating_settings)) {
                        ToggleRow(stringResource(R.string.floating_enabled),floating,c.display::floating)
                        ToggleRow(stringResource(R.string.auto_floating),automatic,c.display::automaticFloating,enabled=floating)
                        HelpText(stringResource(R.string.auto_floating_help))
                        SettingsRow(Icons.Default.Refresh,stringResource(R.string.reset_overlay_position),tag="reset-position") {
                            c.local.resetPositions()
                            if(c.serviceStarted) activity.startService(android.content.Intent(activity,org.openmobifitness.app.service.WorkoutService::class.java).setAction(org.openmobifitness.app.service.WorkoutService.RESET_POSITION))
                        }
                        val allowed by activity.overlayAllowed
                        if(floating && !allowed) OutlinedButton(onClick=activity::requestOverlayPermission,modifier=Modifier.padding(16.dp)) { Text(stringResource(R.string.allow_overlay)) }
                    }
                    "display"->SettingsGroup(stringResource(R.string.display_settings)) {
                        HelpText(stringResource(R.string.display_settings_help))
                        listOf(DisplayScope.TRAINING to R.string.training_screen,DisplayScope.COMPACT to R.string.compact_panel,DisplayScope.EXPANDED to R.string.expanded_panel).forEach { (scope,label) -> SettingsRow(Icons.Default.List,stringResource(label)) { activity.metricScope.value=scope } }
                    }
                    "data"->BackupSettings(activity,c)
                }
            }
        }
    }
    if(editUser) UserEditor(c,user,onClose={ editUser=false },onSaved={ editUser=false })
    if(dialog=="identity") AlertDialog(onDismissRequest={ dialog="" },title={ Text(stringResource(R.string.user_identity_policy)) },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        IdentityPolicy.entries.forEach { option -> Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable { c.setIdentityPolicy(option); dialog="" },verticalAlignment=Alignment.CenterVertically) {
            RadioButton(policy==option,null)
            Text(stringResource(when(option) { IdentityPolicy.EACH_RECORDING -> R.string.user_policy_recording; IdentityPolicy.STARTUP -> R.string.user_policy_startup; IdentityPolicy.REMEMBER -> R.string.user_policy_remember }),Modifier.weight(1f))
        } }
    } },confirmButton={ TextButton(onClick={ dialog="" }) { Text(stringResource(R.string.close)) } })
    else if(dialog.isNotEmpty()) AlertDialog(onDismissRequest={ dialog="" },title={ Text(stringResource(when(dialog) { "language"->R.string.language; "theme"->R.string.theme; else->R.string.units })) },text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val options=when(dialog) { "language"->languages; "theme"->listOf("system" to stringResource(R.string.system),"light" to stringResource(R.string.light),"dark" to stringResource(R.string.dark)); else->listOf("metric" to stringResource(R.string.metric),"imperial" to stringResource(R.string.imperial)) }
            options.forEach { (value,label) -> val selected=when(dialog) { "language"->activity.currentLanguage()==value; "theme"->theme==value; else->imperial==(value=="imperial") }
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { when(dialog) { "language"->activity.setLanguage(value); "theme"->c.setTheme(value); else->c.setImperial(value=="imperial") }; dialog="" },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected,null); Text(label,Modifier.padding(start=12.dp)) }
            }
        }
    },confirmButton={ TextButton(onClick={ dialog="" }) { Text(stringResource(R.string.close)) } })
}

@Composable internal fun PageTitle(title: String,onBack: (() -> Unit)?=null) {
    val short=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<480
    Row(Modifier.fillMaxWidth().heightIn(min=if(short) 48.dp else 56.dp),verticalAlignment=Alignment.CenterVertically) {
        if(onBack!=null) IconButton(onClick=onBack,modifier=Modifier.testTag("page-back")) { Icon(Icons.Default.ArrowBack,stringResource(R.string.back)) }
        Text(title,style=if(short) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    }
}
@Composable internal fun SettingsGroup(title: String,content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(title.isNotEmpty()) Text(title,Modifier.padding(horizontal=4.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(content=content) }
    }
}
@Composable internal fun SettingsRow(icon: ImageVector,title: String,subtitle: String?=null,tag: String="",inlineValue: Boolean=false,symbol: Int?=null,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(onClick=onClick).heightIn(min=52.dp).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        if(symbol==null) Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) else Icon(androidx.compose.ui.res.painterResource(symbol),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium); if(!inlineValue) subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        if(inlineValue && subtitle!=null) Text(subtitle,Modifier.widthIn(max=130.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun ToggleRow(title: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        Switch(checked,onChange,enabled=enabled,modifier=Modifier.semantics { contentDescription=title })
    }
}
@Composable private fun HelpText(text: String) { Text(text,Modifier.padding(horizontal=16.dp,vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable private fun DiagnosticsSettings(activity: MainActivity,c: Controller) {
    val packets by c.display.packets.collectAsStateWithLifecycle()
    val link by c.ble.state.collectAsStateWithLifecycle()
    val heart by c.heart.state.collectAsStateWithLifecycle()
    var technical by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    SettingsGroup(stringResource(R.string.diagnostics)) {
        HelpText(stringResource(R.string.hardware_validation))
        ToggleRow(stringResource(R.string.packet_logging),packets,c.display::packetLogs)
        HelpText(stringResource(R.string.diagnostic_help))
    }
    SettingsGroup(stringResource(R.string.capture_steps)) { TroubleshootingSteps(diagnostic=true) }
    Column(Modifier.widthIn(max=560.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={ activity.export("diagnostics") },modifier=Modifier.fillMaxWidth().testTag("export-diagnostics")) { Text(stringResource(R.string.export_diagnostics)) }
        OutlinedButton(onClick=activity::feedback,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback)) }
    }
    Text(stringResource(R.string.log_retention),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsGroup(stringResource(R.string.connection_details)) {
        SettingsRow(Icons.Default.Info,stringResource(R.string.connection_details),"${link.protocol} · ${stringResource(connectionStatus(link))}") { technical=!technical }
        if(technical) {
            HelpText(stringResource(R.string.packet_counts,link.receivedPackets,link.parsedPackets))
            HelpText((link.diagnostic+heart.diagnostic).takeLast(32).joinToString("\n").ifBlank { stringResource(R.string.no_connection_details) })
        }
    }
    TextButton(onClick={ clear=true }) { Text(stringResource(R.string.clear_logs)) }
    if(clear) AlertDialog(onDismissRequest={ clear=false },title={ Text(stringResource(R.string.clear_logs)) },text={ Text(stringResource(R.string.clear_logs_confirmation)) },confirmButton={ TextButton(onClick={ clear=false; scope.launch { runCatching { AppLog.clear() }.onSuccess { android.widget.Toast.makeText(activity,R.string.logs_cleared,android.widget.Toast.LENGTH_SHORT).show() }.onFailure { c.error(R.string.io_failed) } } }) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ clear=false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun AboutSettings(activity: MainActivity,c: Controller) {
    var checking by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<Int?>(null) }; var release by remember { mutableStateOf<Release?>(null) }
    val scope=rememberCoroutineScope()
    SettingsGroup("OpenMOBI") {
        HelpText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android 10+")
        SettingsRow(Icons.Default.Info,stringResource(R.string.project_home),"github.com/q1ngyang/open-mobifitness") { activity.openUrl(Updates.REPOSITORY) }
        SettingsRow(Icons.Default.Email,stringResource(R.string.feedback),onClick=activity::feedback)
        SettingsRow(Icons.Default.AccountCircle,stringResource(R.string.author),"q1ngyang · x.com/q1ngyang") { activity.openUrl("https://x.com/q1ngyang") }
    }
    SettingsGroup(stringResource(R.string.updates)) {
        HelpText(stringResource(if(BuildConfig.DEBUG) R.string.debug_build_help else R.string.release_build_help))
        Button(enabled=!checking,onClick={ scope.launch { checking=true; message=null; try { release=Updates.check(BuildConfig.VERSION_NAME); if(release==null) message=R.string.up_to_date } catch(e: Exception) { message=R.string.network_unavailable } finally { checking=false } } },modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp)) { Text(stringResource(if(checking) R.string.checking else R.string.check_updates)) }
        message?.let { HelpText(stringResource(it)) }
        release?.let { r -> SettingsRow(Icons.Default.Share,stringResource(R.string.update_available,r.name),stringResource(R.string.open_release)) { activity.openUrl(r.url) } }
    }
    SettingsGroup(stringResource(R.string.privacy_local)) { HelpText(stringResource(R.string.privacy_summary)) }
    SettingsGroup(stringResource(R.string.compatibility)) { HelpText(stringResource(R.string.hardware_validation)); HelpText(stringResource(R.string.compatibility_detail)) }
}
