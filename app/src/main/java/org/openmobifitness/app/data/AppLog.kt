package org.openmobifitness.app.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import org.json.JSONObject
import org.openmobifitness.app.BuildConfig
import org.openmobifitness.app.Controller
import java.io.File
import java.time.Instant

/** Bounded, opt-in packet trace. Never collect system logcat, addresses, names or serial numbers. */
object AppLog {
    private val guard=Any()
    private val queue=Channel<String>(256,BufferOverflow.DROP_OLDEST)
    private var folder: File?=null
    private var prefs: android.content.SharedPreferences?=null
    private val lastPacket=mutableMapOf<String,Long>()
    fun initialize(context: Context) {
        folder=File(context.filesDir,"logs").apply { mkdirs() }
        prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { for(line in queue) append(line) }
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            append(entry("crash",frames(error))); previous?.uncaughtException(thread,error)
        }
        event("app_start","version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT}")
    }
    private fun clean(value: String)=value.replace(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"),"[address]").take(1800)
    private fun entry(type: String,detail: String)=JSONObject().put("at",Instant.now().toString()).put("event",type).put("detail",clean(detail)).toString()
    fun event(type: String,detail: String="") { queue.trySend(entry(type,detail)) }
    private fun frames(t: Throwable)=t.javaClass.simpleName+"\n"+t.stackTrace.take(14).joinToString("\n")
    fun exception(type: String,error: Throwable)=event(type,frames(error))
    fun packet(direction: String,characteristic: String,bytes: ByteArray) {
        if(prefs?.getBoolean("packet_logs",false)!=true) return
        val key="$direction:$characteristic"; val now=android.os.SystemClock.elapsedRealtime()
        synchronized(lastPacket) {
            if(direction=="rx" && now-(lastPacket[key] ?: 0)<200) return
            lastPacket[key]=now
        }
        event("packet","$key len=${bytes.size} ${bytes.take(64).joinToString("") { "%02x".format(it) }}")
    }
    private fun append(line: String)=synchronized(guard) {
        runCatching {
            val dir=folder ?: return@synchronized
            val file=File(dir,"events.jsonl")
            if(file.length()>256*1024) { val old=File(dir,"previous.jsonl"); old.delete(); file.renameTo(old) }
            file.appendText(line+"\n")
        }; Unit
    }
    suspend fun report(c: Controller): String=withContext(Dispatchers.IO) {
        synchronized(guard) {
            buildString {
                appendLine("OpenMobi diagnostic report 3")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.SDK_INT}; manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}")
                appendLine("Protocol trace enabled: ${c.display.packets.value}; simulated: ${c.state.value.demo}")
                listOf("equipment" to c.ble.state.value,"heart" to c.heart.state.value).forEach { (role,state) ->
                    appendLine("$role: ${state.phase} ${state.protocol} ${state.machine} range=${state.range} writable=${state.writable}")
                    appendLine("  subscriptions=${state.subscriptions} received=${state.receivedPackets} parsed=${state.parsedPackets} dataReceived=${state.dataReceived}")
                    appendLine("  lastPacketAgeMs=${state.lastReceiveAt.takeIf { it>0 }?.let { android.os.SystemClock.elapsedRealtime()-it }} controlFeedbackTimedOut=${state.controlTimedOut}")
                    state.diagnostic.forEach { appendLine(clean(it)) }
                }
                appendLine("--- Recent application events (up to 512 KiB) ---")
                folder?.let { dir -> listOf("previous.jsonl","events.jsonl").forEach { name -> File(dir,name).takeIf { it.exists() }?.let { append(it.readText()) } } }
            }
        }
    }
}
