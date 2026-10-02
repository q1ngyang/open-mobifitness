@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.Updates
import org.openmobifitness.app.data.Release
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Pink=Color(0xFFD64C86)
private val Blue=Color(0xFF527CE8)
private val Yellow=Color(0xFFF3C64E)
private val Ink=Color(0xFF192538)
private val darkColors=darkColorScheme(
    primary=Color(0xFFAAC7FF),onPrimary=Color(0xFF002F65),primaryContainer=Color(0xFF234879),onPrimaryContainer=Color(0xFFD5E3FF),
    secondary=Color(0xFFADC3FF),secondaryContainer=Color(0xFF263956),onSecondaryContainer=Color(0xFFD9E4FF),
    tertiary=Yellow,tertiaryContainer=Color(0xFF483C1A),onTertiaryContainer=Color(0xFFFFEBAD),
    background=Color(0xFF111218),surface=Color(0xFF1C1D25),surfaceVariant=Color(0xFF2B2C32),onSurfaceVariant=Color(0xFFC7C7CE),
    onSurface=Color(0xFFF2F2F4),surfaceContainer=Color(0xFF23242B),surfaceContainerHigh=Color(0xFF2D2E35),surfaceContainerHighest=Color(0xFF35363D),
    surfaceContainerLow=Color(0xFF191A20),surfaceContainerLowest=Color(0xFF0E0F13),error=Color(0xFFFFADB4))
private val lightColors=lightColorScheme(
    primary=Color(0xFF0073E6),onPrimary=Color.White,primaryContainer=Color(0xFFF0F7FF),onPrimaryContainer=Color(0xFF173655),
    secondary=Color(0xFF3059B6),secondaryContainer=Color(0xFFE4EBFF),onSecondaryContainer=Color(0xFF18366D),
    tertiary=Color(0xFF755A00),tertiaryContainer=Color(0xFFFFF0B3),onTertiaryContainer=Color(0xFF352B00),
    background=Color(0xFFF8F9FB),surface=Color.White,surfaceVariant=Color(0xFFE1E6ED),onSurfaceVariant=Color(0xFF4D5B70),outline=Color(0xFFB8C2CF),outlineVariant=Color(0xFFDFE4EB),
    onSurface=Ink,surfaceContainer=Color(0xFFF0F0F3),surfaceContainerHigh=Color(0xFFE9E9ED),surfaceContainerHighest=Color(0xFFE3E3E8),
    surfaceContainerLow=Color(0xFFF5F5F7),surfaceContainerLowest=Color.White,error=Color(0xFFE5003A))

@Composable fun OpenMobi(activity: MainActivity,controller: Controller) {
    val theme by controller.theme.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val link by controller.ble.state.collectAsStateWithLifecycle()
    val pageState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val page by activity.page
    val focused by activity.focusTraining
    val dark=theme=="dark" || (theme=="system" && isSystemInDarkTheme())
    SideEffect { WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } }
    MaterialTheme(colorScheme=if(dark) darkColors else lightColors,typography=Typography()) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                if(focused && state.session!=null) { TrainingScreen(activity,controller,state,link) } else {
                val shortLayout=maxHeight<480.dp
                val rail=maxWidth>=720.dp || (maxWidth>=520.dp && shortLayout)
                val labels=listOf(R.string.train,R.string.history,R.string.devices,R.string.settings)
                val icons=listOf(Icons.Default.PlayArrow,Icons.Default.List,Icons.Default.Search,Icons.Default.Settings)
                Row(Modifier.fillMaxSize()) {
                    if(rail) NavigationRail(containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight().width(100.dp)) {
                        BrandMark(dark,Modifier.size(if(shortLayout) 40.dp else 76.dp))
                        Spacer(Modifier.height(if(shortLayout) 4.dp else 32.dp))
                        labels.forEachIndexed { i,id -> NavigationRailItem(selected=page==i,onClick={ activity.page.value=i; if(i==0 && state.session!=null) activity.focusTraining.value=true },icon={ Icon(icons[i],stringResource(id)) },label=if(shortLayout) null else ({ Text(stringResource(id)) }),modifier=Modifier.padding(vertical=if(shortLayout) 0.dp else 8.dp)) }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if(!shortLayout || !rail) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(!rail) { BrandMark(dark,Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
                            Text("OpenMOBI",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            BadgeText(if(state.demo) stringResource(R.string.demo) else stringResource(R.string.local_only),if(state.demo) Yellow else Blue)
                        }
                        if(state.error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),shape=RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(state.errorRow!=null) stringResource(state.error!!,state.errorRow!!) else stringResource(state.error!!),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                IconButton(onClick={ controller.error(null) }) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
                            }
                        }
                        if(state.session!=null && page!=0) ActiveWorkoutBanner(state) { activity.page.value=0; activity.focusTraining.value=true }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            pageState.SaveableStateProvider(page) { when(page) {
                                0 -> if(state.session==null) WorkoutLibrary(activity,controller,state,link) else TrainingScreen(activity,controller,state,link)
                                1 -> HistoryScreen(activity,controller)
                                2 -> Devices(activity,controller,state,link)
                                else -> SettingsPage(activity,controller)
                            } }
                        }
                        if(!rail) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                            labels.forEachIndexed { i,id -> NavigationBarItem(selected=page==i,onClick={ activity.page.value=i; if(i==0 && state.session!=null) activity.focusTraining.value=true },icon={ Icon(icons[i],null) },label={
                                BasicText(stringResource(id),style=LocalTextStyle.current.copy(color=LocalContentColor.current,textAlign=TextAlign.Center,letterSpacing=0.sp),maxLines=2,
                                    autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=12.sp,stepSize=1.sp))
                            }) }
                        }
                    }
                }
                }
            }
        }
        val preview by activity.preview
        activity.metricScope.value?.let { scope -> MetricPicker(controller,scope) { activity.metricScope.value=null; activity.intent.removeExtra("metrics_scope") } }
        if(preview!=null) AlertDialog(onDismissRequest={ activity.preview.value=null },title={ Text(stringResource(R.string.import_preview)) },
            text={ Text(stringResource(R.string.import_summary,preview!!.sessions.size,preview!!.samples.size,preview!!.workouts.size)) },
            confirmButton={ TextButton(onClick=activity::confirmImport) { Text(stringResource(R.string.confirm)) } },dismissButton={ TextButton(onClick={ activity.preview.value=null }) { Text(stringResource(R.string.cancel)) } })
    }
}
@Composable private fun BrandMark(dark: Boolean,modifier: Modifier=Modifier) {
    Image(painterResource(if(dark) R.drawable.brand_dark else R.drawable.brand_light),"OpenMOBI",modifier.clip(RoundedCornerShape(12.dp)))
}
@Composable private fun BadgeText(text: String,color: Color) {
    Surface(color=color.copy(alpha=.13f),shape=RoundedCornerShape(30.dp)) {
        Text(text,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun SectionTitle(title: String,subtitle: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ConnectionCard(state: ExerciseState,link: LinkState,onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Settings,null,tint=MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(state.demo) "OpenMOBI Demo" else link.name.ifEmpty { stringResource(R.string.devices) },fontWeight=FontWeight.Bold)
                Text(if(state.demo) stringResource(R.string.demo) else phase(link.phase),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight,stringResource(R.string.devices))
        }
    }
}
@Composable private fun Devices(activity: MainActivity,c: Controller,state: ExerciseState,link: LinkState) {
    val found by c.ble.found.collectAsStateWithLifecycle(); val scanning by c.ble.scanning.collectAsStateWithLifecycle()
    var diagnostics by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { SectionTitle(stringResource(R.string.devices),stringResource(R.string.scan_help)) }
        item { ConnectionCard(state,link) {} }
        if(link.phase in setOf("ready","awaiting_data","subscription_failed")) item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${link.protocol} · ${stringResource(when(link.phase) { "awaiting_data" -> R.string.waiting_data_help; "subscription_failed" -> R.string.subscription_failed; else -> if(link.writable) R.string.unverified else R.string.read_only })}")
                link.range?.let { Text(stringResource(R.string.range_format,num(it.min),num(it.max))) }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick=activity::disconnect) { Text(stringResource(R.string.disconnect)) }
                    TextButton(onClick={ diagnostics=true }) { Text(stringResource(R.string.diagnostics)) }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick=activity::scan,enabled=!scanning) { Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text(stringResource(if(scanning) R.string.scanning else R.string.scan)) }
                if(c.prefs.contains("last_address") && link.phase=="disconnected") OutlinedButton(onClick=activity::reconnect) { Text(stringResource(R.string.reconnect)) }
            }
        }
        items(found,key={ it.address }) { device ->
            OutlinedCard(onClick={ activity.connect(device) },shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(device.name.ifEmpty { stringResource(R.string.unknown_device) },fontWeight=FontWeight.Bold); Text(if(device.heart) stringResource(R.string.heart_rate) else "${device.rssi} dBm",style=MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.connect),color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(found.isEmpty()) item { Text(stringResource(R.string.no_devices),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.permission_help),style=MaterialTheme.typography.bodySmall) }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.demo),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.demo_description))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(Machine.ELLIPTICAL to R.string.machine_elliptical,Machine.BIKE to R.string.machine_bike,Machine.ROWER to R.string.machine_rower,Machine.TREADMILL to R.string.machine_treadmill).forEach { (machine,label) ->
                            FilterChip(state.demo && state.demoMachine==machine,{ c.setDemo(true,machine) },enabled=state.session==null,label={ Text(stringResource(label)) })
                        }
                    }
                    OutlinedButton(onClick={ c.setDemo(!state.demo); if(!state.demo) activity.page.value=0 },enabled=state.session==null) { Text(stringResource(if(state.demo) R.string.exit_demo else R.string.start_demo)) }
                }
            }
        }
        item { TextButton(onClick={ diagnostics=true }) { Text(stringResource(R.string.diagnostics)) } }
    }
    if(diagnostics) AlertDialog(onDismissRequest={ diagnostics=false },title={ Text(stringResource(R.string.diagnostics)) },text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) { Text(stringResource(R.string.diagnostic_help)); Text(link.diagnostic.joinToString("\n"),style=MaterialTheme.typography.bodySmall) } },confirmButton={ TextButton(onClick={ activity.export("diagnostics") }) { Text(stringResource(R.string.export_diagnostics)) } },dismissButton={ TextButton(onClick={ diagnostics=false }) { Text(stringResource(R.string.close)) } })
}
@Composable private fun SettingsPage(activity: MainActivity,c: Controller) {
    val theme by c.theme.collectAsStateWithLifecycle(); val imperial by c.imperial.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }; var updateMessage by remember { mutableStateOf<Int?>(null) }; var release by remember { mutableStateOf<Release?>(null) }
    val scope=rememberCoroutineScope()
    var estimates by remember { mutableStateOf(false) }
    val packets by c.display.packets.collectAsStateWithLifecycle()
    val floating by c.display.floatingEnabled.collectAsStateWithLifecycle()
    val autoFloating by c.display.autoFloating.collectAsStateWithLifecycle()
    val overlayAllowed by activity.overlayAllowed
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
        item { SectionTitle(stringResource(R.string.settings),stringResource(R.string.local_only)) }
        item { SettingGroup(stringResource(R.string.language)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("" to stringResource(R.string.system),"zh-Hans" to "简体中文","zh-Hant" to "繁體中文","en" to "English","ja" to "日本語","ko" to "한국어","de" to "Deutsch").forEach { (tag,name) ->
                    FilterChip(selected=activity.currentLanguage()==tag,onClick={ activity.setLanguage(tag) },label={ Text(name) })
                }
            }
        } }
        item { SettingGroup(stringResource(R.string.theme)) { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to R.string.system,"light" to R.string.light,"dark" to R.string.dark).forEach { (value,label) -> FilterChip(selected=theme==value,onClick={ c.setTheme(value) },label={ Text(stringResource(label)) }) }
        } } }
        item { SettingGroup(stringResource(R.string.units)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!imperial,onClick={ c.setImperial(false) },label={ Text(stringResource(R.string.metric)) })
            FilterChip(selected=imperial,onClick={ c.setImperial(true) },label={ Text(stringResource(R.string.imperial)) })
        } } }
        item { SettingGroup(stringResource(R.string.floating_settings)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.floating_enabled),Modifier.weight(1f)); Switch(floating,c.display::floating,modifier=Modifier.semantics { contentDescription=activity.getString(R.string.floating_enabled) }) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.auto_floating),Modifier.weight(1f)); Switch(autoFloating,c.display::automaticFloating,enabled=floating,modifier=Modifier.semantics { contentDescription=activity.getString(R.string.auto_floating) }) }
            Text(stringResource(R.string.auto_floating_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(floating && !overlayAllowed) OutlinedButton(onClick=activity::requestOverlayPermission) { Text(stringResource(R.string.allow_overlay)) }
        } }
        item { SettingGroup(stringResource(R.string.history)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ activity.export("backup") }) { Text(stringResource(R.string.export_backup)) }
                OutlinedButton(onClick={ activity.export("workouts") }) { Text(stringResource(R.string.export_workouts)) }
                TextButton(onClick=activity::importFile) { Text(stringResource(R.string.import_file)) }
            }
        } }
        item { SettingGroup(stringResource(R.string.training_screen)) {
            Text(stringResource(R.string.display_settings_help),style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(DisplayScope.TRAINING to R.string.training_screen,DisplayScope.COMPACT to R.string.compact_panel,DisplayScope.EXPANDED to R.string.expanded_panel).forEach { (scope,label) ->
                    OutlinedButton(onClick={ activity.metricScope.value=scope }) { Text(stringResource(label)) }
                }
            }
            OutlinedButton(onClick={ estimates=true }) { Text(stringResource(R.string.estimation_settings)) }
        } }
        item { SettingGroup(stringResource(R.string.diagnostics)) {
            Text(stringResource(R.string.diagnostic_help),style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.packet_logging),Modifier.weight(1f)); Switch(packets,c.display::packetLogs) }
            Text(stringResource(R.string.packet_help),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={ activity.export("diagnostics") }) { Text(stringResource(R.string.export_diagnostics)) }
            Text(stringResource(R.string.log_retention),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={ scope.launch {
                runCatching { org.openmobifitness.app.data.AppLog.clear() }
                    .onSuccess { android.widget.Toast.makeText(activity,R.string.logs_cleared,android.widget.Toast.LENGTH_SHORT).show() }
                    .onFailure { c.error(R.string.io_failed) }
            } }) { Text(stringResource(R.string.clear_logs)) }
        } }
        item { SettingGroup(stringResource(R.string.updates)) {
            Text("OpenMOBI ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodyMedium)
            Button(enabled=!checking,onClick={ scope.launch {
                checking=true; updateMessage=null
                try { release=Updates.check(BuildConfig.VERSION_NAME); if(release==null) updateMessage=R.string.up_to_date }
                catch(e: Exception) { updateMessage=R.string.network_unavailable }
                finally { checking=false }
            } }) { Text(stringResource(if(checking) R.string.checking else R.string.check_updates)) }
            updateMessage?.let { Text(stringResource(it)) }
            release?.let { found -> Text(stringResource(R.string.update_available,found.name)); TextButton(onClick={ activity.openUrl(found.url) }) { Text(stringResource(R.string.open_release)) } }
            TextButton(onClick={ activity.openUrl(Updates.REPOSITORY) }) { Text(stringResource(R.string.source_code)) }
        } }
        item { Text(stringResource(R.string.overlay_info),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if(estimates) EstimateDialog(c) { estimates=false }
}
@Composable private fun SettingGroup(title: String,content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); content() }
}
private data class Draft(val condition: Condition,val target: String,val resistance: String)
@Composable internal fun WorkoutEditor(workout: Workout,range: ResistanceRange?=null,onDismiss: ()->Unit,onSave: (Workout)->Unit) {
    val originalName=if(workout.title.isEmpty()) "" else workoutTitle(workout)
    var title by remember { mutableStateOf(originalName) }; var invalid by remember { mutableStateOf(false) }
    val steps=remember { mutableStateListOf<Draft>().apply { addAll(workout.steps.map { Draft(it.condition,num(it.target),(it.resistancePercent ?: 0).toString()) }) } }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=740.dp).fillMaxHeight(.92f).imePadding(),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.new_workout),style=MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=20.dp)) {
                    item { OutlinedTextField(title,{ title=it },label={ Text(stringResource(R.string.title)) },modifier=Modifier.fillMaxWidth(),singleLine=true) }
                    items(steps.size) { i -> val draft=steps[i]
                        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.stage_format,i+1,steps.size),fontWeight=FontWeight.Bold)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Condition.entries.forEach { condition -> FilterChip(selected=draft.condition==condition,onClick={ steps[i]=draft.copy(condition=condition) },label={ Text(stringResource(when(condition) { Condition.TIME -> R.string.seconds; Condition.DISTANCE -> R.string.meters; Condition.STROKES -> R.string.strokes })) }) }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(draft.target,{ steps[i]=draft.copy(target=it) },label={ Text(stringResource(R.string.target)) },modifier=Modifier.weight(1f),singleLine=true)
                                OutlinedTextField(draft.resistance,{ steps[i]=draft.copy(resistance=it) },label={ Text(stringResource(R.string.resistance_percent)) },modifier=Modifier.weight(1f),singleLine=true)
                            }
                            draft.resistance.toIntOrNull()?.takeIf { it in 0..100 }?.let { percent -> range?.let { r -> Text(stringResource(R.string.mapped_target,num(r.percent(percent)),r.percentage(r.percent(percent))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) } }
                            if(steps.size>1) TextButton(onClick={ steps.removeAt(i) }) { Text(stringResource(R.string.remove_step)) }
                        } }
                    }
                    item { TextButton(enabled=steps.size<200,onClick={ steps.add(Draft(Condition.TIME,"60","20")) }) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.add_step)) } }
                }
                if(invalid) Text(stringResource(R.string.invalid_input),color=MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick={ try { onSave(Workout(if(workout.builtin) UUID.randomUUID().toString() else workout.id,title.trim(),steps.map { Step(it.condition,it.target.toDouble(),it.resistance.toInt()) })) } catch(e: Exception) { invalid=true } }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
@Composable internal fun workoutTitle(workout: Workout): String = LocalContext.current.workoutName(workout)
@Composable private fun phase(value: String) = stringResource(when(value) { "ready" -> R.string.connected; "awaiting_data" -> R.string.awaiting_data; "subscription_failed" -> R.string.subscription_failed; "connecting" -> R.string.connecting; "discovering" -> R.string.discovering; else -> R.string.disconnected })
@Composable private fun status(value: String) = stringResource(when(value) { "completed" -> R.string.completed; "interrupted" -> R.string.interrupted; "active" -> R.string.active; else -> R.string.stopped })
private fun num(value: Double?)=WorkoutService.number(value)
private fun date(value: String)=runCatching { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault(value)
