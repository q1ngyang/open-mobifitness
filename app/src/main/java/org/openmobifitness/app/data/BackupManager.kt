package org.openmobifitness.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

enum class TransferPhase { READING, EXPORTING, RESTORING, FINALIZING, COMPLETE, CANCELLED, FAILED }
data class TransferStatus(val phase: TransferPhase,val progress: Long=0,val error: String="",val restoredPreferences: Boolean=false) {
    val cancellable get()=phase in setOf(TransferPhase.READING,TransferPhase.EXPORTING,TransferPhase.RESTORING)
}
/** Application lifetime owner: rotation never loses a staged preview or an active transfer. */
class BackupManager(private val context: Context,private val repo: Repository,private val prefs: SharedPreferences,private val scope: CoroutineScope,private val restored: ()->Unit,private val exclusive: suspend (suspend ()->Unit)->Unit = { it() }) {
    val preview=MutableStateFlow<StagedBackup?>(null)
    val status=MutableStateFlow<TransferStatus?>(null)
    private var job: Job?=null
    private val cleanup=scope.async(Dispatchers.IO) {
        context.cacheDir.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("restore-[0-9a-f-]{36}")) }?.forEach { it.deleteRecursively() }
    }
    private fun failure(e: Throwable) {
        AppLog.exception("backup_transfer",e)
        status.value=TransferStatus(TransferPhase.FAILED,error=when {
            e.message?.contains("preference_restore_pending")==true -> "preferences"
            e.message?.contains("conflict")==true -> "conflict"
            e.message?.contains("capacity")==true -> "capacity"
            generateSequence(e) { it.cause }.any { it.message?.contains("ENOSPC")==true || it.message?.contains("full",true)==true } -> "space"
            else -> "invalid"
        })
    }
    fun read(uri: Uri) {
        if(job?.isActive==true) return
        discard()
        status.value=TransferStatus(TransferPhase.READING)
        job=scope.launch {
            var prepared: StagedBackup?=null
            try {
                cleanup.await()
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { prepared=BackupTransfer.stage(context,repo,it) { bytes -> status.value=TransferStatus(TransferPhase.READING,bytes) } } }
                preview.value=prepared; prepared=null; status.value=null
            } catch(e: CancellationException) { status.value=TransferStatus(TransferPhase.CANCELLED) }
            catch(e: Exception) { failure(e) }
            finally { withContext(NonCancellable+Dispatchers.IO) { prepared?.close() } }
        }
    }
    fun export(uri: Uri,includePreferences: Boolean) {
        if(job?.isActive==true) return
        status.value=TransferStatus(TransferPhase.EXPORTING)
        job=scope.launch {
            try {
                val settings=if(includePreferences) PortablePreferences.exportShared(prefs) else null
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")!!.use { BackupTransfer.export(repo,it,settings) { bytes -> status.value=TransferStatus(TransferPhase.EXPORTING,bytes) } } }
                status.value=TransferStatus(TransferPhase.COMPLETE)
            } catch(e: Exception) {
                withContext(NonCancellable+Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) }.onFailure { runCatching { context.contentResolver.openOutputStream(uri,"wt")?.close() } } }
                if(e is CancellationException) status.value=TransferStatus(TransferPhase.CANCELLED) else failure(e)
            }
        }
    }
    fun restore(groups: Set<PreferenceGroup>,overwriteProfiles: Boolean=false) {
        if(job?.isActive==true) return
        val source=preview.value ?: return
        status.value=TransferStatus(TransferPhase.RESTORING); preview.value=null
        job=scope.launch {
            try {
                var count=0L
                exclusive { count=BackupTransfer.restore(repo,source,groups,{ rows -> status.value=TransferStatus(TransferPhase.RESTORING,rows) },{ status.value=TransferStatus(TransferPhase.FINALIZING) },overwriteProfiles) }
                restored(); status.value=TransferStatus(TransferPhase.COMPLETE,count,restoredPreferences=groups.isNotEmpty())
            } catch(e: CancellationException) { status.value=TransferStatus(TransferPhase.CANCELLED) }
            catch(e: Exception) { failure(e) }
            finally { withContext(NonCancellable+Dispatchers.IO) { source.close() } }
        }
    }
    fun cancel() { if(status.value?.cancellable==true) job?.cancel() }
    fun dismiss() { if(status.value?.cancellable!=true && status.value?.phase!=TransferPhase.FINALIZING) status.value=null }
    fun discard() { val old=preview.value; preview.value=null; if(old!=null) scope.launch(Dispatchers.IO) { old.close() } }
}
