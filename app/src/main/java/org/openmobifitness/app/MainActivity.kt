package org.openmobifitness.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.openmobifitness.app.ble.FoundDevice
import org.openmobifitness.app.data.Files
import org.openmobifitness.app.data.HistoryQuery
import org.openmobifitness.app.data.StatisticsReport
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.app.ui.OpenMobi
import org.openmobifitness.core.*

class MainActivity : ComponentActivity() {
    private val controller get()=(application as OpenMobiApp).controller
    val page=mutableStateOf(0)
    val settingsSection=mutableStateOf("")
    val focusTraining=mutableStateOf(false)
    val metricScope=mutableStateOf<DisplayScope?>(null)
    private var pendingPermission: (() -> Unit)?=null
    private var exportKind="sessions"
    private var exportSessionId: String?=null
    private var reportQuery=HistoryQuery()
    var includeBackupPreferences=true
    private var awaitingOverlay=false
    private var externalNavigation=false
    val overlayAllowed=mutableStateOf(false)
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action=pendingPermission; pendingPermission=null
        if(bluetoothAllowed()) action?.invoke() else controller.error(R.string.permission_help)
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val importLauncher=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) controller.backup.read(uri)
    }

    private val exportLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if(uri!=null && exportKind=="backup") controller.backup.export(uri,includeBackupPreferences)
        else if(uri!=null) controller.scope.launch {
            val kind=exportKind
            val sessionId=exportSessionId
            val query=reportQuery
            val units=controller.imperial.value
            val reportContext=applicationContext.localized().let { it.createConfigurationContext(android.content.res.Configuration(it.resources.configuration)) }
            try {
                val diagnostic=if(kind=="diagnostics") org.openmobifitness.app.data.AppLog.report(controller) else null
                val archive=if(kind=="diagnostics" || kind=="report") Archive() else controller.repo.archive(sessionId,includeSamples=kind in setOf("backup","samples"),includeWorkouts=kind in setOf("backup","workouts"),includeSessions=kind!="workouts")
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri,"wt")!!.use { output ->
                        when(kind) {
                            "report" -> StatisticsReport.write(reportContext,controller.repo.history,query,sessionId,output,units)
                            "backup" -> Files.backup(output,archive)
                            else -> {
                                val text=when(kind) {
                                    "samples" -> Exchange.samples(archive.samples)
                                    "workouts" -> Exchange.workouts(archive.workouts)
                                    "diagnostics" -> diagnostic.orEmpty()
                                    else -> Exchange.sessions(archive.sessions)
                                }
                                output.write(text.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
                Toast.makeText(applicationContext.localized(),R.string.saved,Toast.LENGTH_SHORT).show()
            } catch(e: Exception) { controller.error(R.string.io_failed) }
        }
    }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.localized()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        exportKind=savedInstanceState?.getString("exportKind") ?: "sessions"
        exportSessionId=savedInstanceState?.getString("exportSessionId")
        reportQuery=HistoryQuery.decode(savedInstanceState?.getString("reportQuery"))
        awaitingOverlay=savedInstanceState?.getBoolean("overlay") ?: false
        page.value=savedInstanceState?.getInt("page") ?: 0
        settingsSection.value=savedInstanceState?.getString("settingsSection") ?: ""
        focusTraining.value=savedInstanceState?.getBoolean("focusTraining") ?: false
        if(savedInstanceState==null) {
            if(intent.getBooleanExtra("training",false)) { page.value=0; focusTraining.value=true } else if(intent.getBooleanExtra("devices",false)) { page.value=2; focusTraining.value=false }
        }
        intent.removeExtra("training"); intent.removeExtra("devices")
        metricScope.value=runCatching { DisplayScope.valueOf(savedInstanceState?.getString("metrics_scope") ?: intent.getStringExtra("metrics_scope") ?: "") }.getOrNull()
        setContent { OpenMobi(this,controller) }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("settingsSection",settingsSection.value); outState.putString("reportQuery",reportQuery.encode()); outState.putString("exportKind",exportKind); outState.putString("exportSessionId",exportSessionId); outState.putBoolean("overlay",awaitingOverlay); outState.putInt("page",page.value); outState.putBoolean("focusTraining",focusTraining.value); outState.putString("metrics_scope",metricScope.value?.name); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if(intent.getBooleanExtra("training",false)) { page.value=0; focusTraining.value=true } else if(intent.getBooleanExtra("devices",false)) { page.value=2; focusTraining.value=false }; intent.removeExtra("training"); intent.removeExtra("devices"); metricScope.value=runCatching { DisplayScope.valueOf(intent.getStringExtra("metrics_scope") ?: "") }.getOrNull() }
    override fun onResume() {
        super.onResume()
        if(intent.getBooleanExtra("reconnect",false)) { intent.removeExtra("reconnect"); reconnect() }
        externalNavigation=false
        overlayAllowed.value=Settings.canDrawOverlays(this)
        val minimizeOnReturn=awaitingOverlay; awaitingOverlay=false
        if(minimizeOnReturn && overlayAllowed.value) { minimize(); return }
        if(controller.serviceStarted) startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.HIDE))
    }
    override fun onStop() {
        super.onStop()
        // Do not draw over our own permission/file flows, rotations, or the lock screen.
        if(!isChangingConfigurations && !externalNavigation && controller.state.value.inUse &&
            controller.display.floatingEnabled.value && controller.display.autoFloating.value &&
            Settings.canDrawOverlays(this) && controller.serviceStarted &&
            getSystemService(android.os.PowerManager::class.java).isInteractive &&
            !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            runCatching { startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.SHOW)) }
                .onFailure { org.openmobifitness.app.data.AppLog.exception("automatic_overlay",it) }
        }
    }
    private fun requiredBluetooth() = if(Build.VERSION.SDK_INT>=31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun bluetoothAllowed()=requiredBluetooth().all { ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED }
    private fun withBluetooth(action: ()->Unit) {
        if(bluetoothAllowed()) action() else { pendingPermission=action; permissionLauncher.launch(requiredBluetooth()) }
    }
    fun scan() = withBluetooth { if(controller.ble.available()) controller.ble.scan() else controller.error(R.string.bluetooth_off) }
    fun connect(device: FoundDevice) = withBluetooth {
        if(controller.state.value.demo && controller.state.value.session!=null) return@withBluetooth
        controller.connect(device); ensureService()
    }
    fun reconnect() {
        val address=controller.prefs.getString("last_address",null) ?: return
        connect(FoundDevice(address,controller.prefs.getString("last_name","") ?: "",0))
    }
    fun ensureService() {
        val intent=Intent(this,WorkoutService::class.java)
        try {
            if(controller.state.value.demo) startService(intent) else ContextCompat.startForegroundService(this,intent)
            if(Build.VERSION.SDK_INT>=33 && !controller.state.value.demo && !controller.prefs.getBoolean("notifications_requested",false) && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                controller.prefs.edit().putBoolean("notifications_requested",true).apply()
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch(e: Exception) { controller.error(R.string.permission_help) }
    }
    fun startTraining() { if(!controller.canStart()) { controller.error(if(controller.ble.state.value.phase=="awaiting_data") R.string.waiting_data_help else R.string.connect_first); page.value=2 } else { ensureService(); controller.start(); page.value=0; focusTraining.value=true } }
    fun startControl() {
        if(!controller.canStart()) { controller.error(R.string.connect_first); page.value=2; return }
        ensureService(); controller.enterControl(); page.value=0; focusTraining.value=true
    }
    fun exitControl() { controller.exitControl(); focusTraining.value=false; page.value=0; stopIdleService() }
    private fun stopIdleService() { if(controller.serviceStarted) startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.STOP_IF_IDLE)) }
    fun minimize() {
        if(!controller.state.value.inUse || !controller.display.floatingEnabled.value) return
        if(!Settings.canDrawOverlays(this)) {
            awaitingOverlay=true
            Toast.makeText(this,R.string.overlay_permission,Toast.LENGTH_LONG).show()
            requestOverlayPermission(); return
        }
        ensureService()
        startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.SHOW))
        moveTaskToBack(true)
    }
    fun requestOverlayPermission() {
        externalNavigation=true
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
            .onFailure { externalNavigation=false; awaitingOverlay=false; controller.error(R.string.overlay_permission) }
    }
    fun currentLanguage(): String = if(Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags() else controller.prefs.getString("language","") ?: ""
    fun setLanguage(tag: String) {
        controller.prefs.edit().putString("language",tag).apply()
        if(Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(tag)
        else recreate()
    }
    fun export(kind: String,sessionId: String?=null) { if(kind=="backup" && controller.state.value.session!=null) { controller.error(R.string.backup_active); return }; externalNavigation=true; exportKind=kind; exportSessionId=sessionId; exportLauncher.launch("OpenMOBI-$kind.${if(kind=="backup") "zip" else if(kind=="diagnostics") "txt" else "csv"}") }
    fun exportReport(query: HistoryQuery=HistoryQuery(),sessionId: String?=null) { reportQuery=query; export("report",sessionId) }
    fun importFile() { if(controller.state.value.session!=null) { controller.error(R.string.backup_active); return }; externalNavigation=true; importLauncher.launch(arrayOf("text/*","application/zip","application/octet-stream")) }

    fun openDiagnostics() { focusTraining.value=false; page.value=3; settingsSection.value="diagnostics" }
    fun feedback() { openUrl(org.openmobifitness.app.data.Updates.REPOSITORY+"/issues/new?template=device-problem.yml") }
    fun openUrl(url: String) { externalNavigation=true; runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { externalNavigation=false; controller.error(R.string.network_unavailable) } }
    fun disconnect() { controller.disconnectEquipment(); if(!controller.state.value.inUse && controller.heart.state.value.phase=="disconnected") stopIdleService() }
}
