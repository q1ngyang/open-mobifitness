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
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.app.ui.OpenMobi
import org.openmobifitness.core.*

class MainActivity : ComponentActivity() {
    private val controller get()=(application as OpenMobiApp).controller
    val page=mutableStateOf(0)
    val focusTraining=mutableStateOf(false)
    private var pendingPermission: (() -> Unit)?=null
    private var exportKind="sessions"
    private var exportSessionId: String?=null
    val preview=mutableStateOf<Archive?>(null)
    private var awaitingOverlay=false
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action=pendingPermission; pendingPermission=null
        if(bluetoothAllowed()) action?.invoke() else controller.error(R.string.permission_help)
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val importLauncher=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) controller.scope.launch {
            try {
                val archive=withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { Files.read(it) } }
                Exchange.validateReferences(archive,controller.repo.sessions.value)
                preview.value=archive
            } catch(e: ImportProblem) { controller.error(R.string.import_row_error,e.row) }
            catch(e: Exception) { controller.error(R.string.import_failed) }
        }
    }
    private val exportLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if(uri!=null) controller.scope.launch {
            val kind=exportKind
            val sessionId=exportSessionId
            try {
                val diagnostic=if(kind=="diagnostics") org.openmobifitness.app.data.AppLog.report(controller) else null
                val archive=if(kind=="diagnostics") Archive() else controller.repo.archive(sessionId)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri,"wt")!!.use { output ->
                        when(kind) {
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
        awaitingOverlay=savedInstanceState?.getBoolean("overlay") ?: false
        page.value=savedInstanceState?.getInt("page") ?: 0
        focusTraining.value=savedInstanceState?.getBoolean("focusTraining") ?: false
        if(intent.getBooleanExtra("training",false)) { page.value=0; focusTraining.value=true }
        setContent { OpenMobi(this,controller) }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("exportKind",exportKind); outState.putString("exportSessionId",exportSessionId); outState.putBoolean("overlay",awaitingOverlay); outState.putInt("page",page.value); outState.putBoolean("focusTraining",focusTraining.value); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if(intent.getBooleanExtra("training",false)) { page.value=0; focusTraining.value=true } }
    override fun onResume() {
        super.onResume()
        if(awaitingOverlay && Settings.canDrawOverlays(this)) { awaitingOverlay=false; minimize(); return }
        if(controller.serviceStarted) startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.HIDE))
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
    fun startTraining() { if(!controller.canStart()) { controller.error(R.string.connect_first); page.value=2 } else { ensureService(); controller.start(); page.value=0; focusTraining.value=true } }
    fun minimize() {
        if(controller.state.value.session==null) return
        if(!Settings.canDrawOverlays(this)) {
            awaitingOverlay=true
            Toast.makeText(this,R.string.overlay_permission,Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))); return
        }
        ensureService()
        startService(Intent(this,WorkoutService::class.java).setAction(WorkoutService.SHOW))
        moveTaskToBack(true)
    }
    fun currentLanguage(): String = if(Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags() else controller.prefs.getString("language","") ?: ""
    fun setLanguage(tag: String) {
        controller.prefs.edit().putString("language",tag).apply()
        if(Build.VERSION.SDK_INT>=33) getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(tag)
        else recreate()
    }
    fun export(kind: String,sessionId: String?=null) { exportKind=kind; exportSessionId=sessionId; exportLauncher.launch("OpenMobi-$kind.${if(kind=="backup") "zip" else if(kind=="diagnostics") "txt" else "csv"}") }
    fun importFile() { importLauncher.launch(arrayOf("text/*","application/zip","application/octet-stream")) }
    fun confirmImport() {
        val archive=preview.value ?: return; preview.value=null
        controller.scope.launch { try { controller.repo.importArchive(archive); Toast.makeText(applicationContext.localized(),R.string.import_done,Toast.LENGTH_SHORT).show() } catch(e: Exception) { controller.error(R.string.import_failed) } }
    }
    fun openUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { controller.error(R.string.network_unavailable) } }
    fun disconnect() { controller.ble.disconnect(); if(controller.state.value.session==null) stopService(Intent(this,WorkoutService::class.java)) }
}
