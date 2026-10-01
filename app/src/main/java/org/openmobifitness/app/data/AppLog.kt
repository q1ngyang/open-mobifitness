package org.openmobifitness.app.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.openmobifitness.app.BuildConfig
import org.openmobifitness.app.Controller
import java.io.File
import java.time.Instant

/** Bounded, opt-in packet trace. Never collect system logcat, addresses, names or serial numbers. */
object AppLog {
    private sealed interface Command {
        data class Append(val line: String): Command
        data class Snapshot(val result: CompletableDeferred<String>): Command
        data class Clear(val result: CompletableDeferred<Unit>): Command
    }
    // Only event sends may be dropped when full; snapshot/clear barriers are never discarded.
    private val queue=Channel<Command>(256)
    private var store: BoundedLogStore?=null
    private var prefs: android.content.SharedPreferences?=null
    private val lastPacket=mutableMapOf<String,Long>()
    fun initialize(context: Context) {
        store=BoundedLogStore(File(context.filesDir,"logs"))
        prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            for(command in queue) when(command) {
                is Command.Append -> runCatching { store?.append(command.line) }
                is Command.Snapshot -> runCatching { store?.snapshot().orEmpty() }.fold(command.result::complete,command.result::completeExceptionally)
                is Command.Clear -> runCatching { store?.clear(); Unit }.fold(command.result::complete,command.result::completeExceptionally)
            }
        }
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            runCatching { store?.append(entry("crash",frames(error))) }; previous?.uncaughtException(thread,error)
        }
        event("app_start","version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT}")
    }
    private fun clean(value: String)=value.replace(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"),"[address]").take(1800)
    private fun entry(type: String,detail: String)=JSONObject().put("at",Instant.now().toString()).put("event",type).put("detail",clean(detail)).toString()
    fun event(type: String,detail: String="") { queue.trySend(Command.Append(entry(type,detail))) }
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
    suspend fun clear() {
        val result=CompletableDeferred<Unit>(); queue.send(Command.Clear(result)); result.await()
    }
    suspend fun report(c: Controller): String=withContext(Dispatchers.IO) {
        val result=CompletableDeferred<String>(); queue.send(Command.Snapshot(result))
        val recent=result.await()
        val header=buildString {
                appendLine("OpenMOBI diagnostic report 4")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.SDK_INT}; manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}")
                appendLine("Protocol trace enabled: ${c.display.packets.value}; simulated: ${c.state.value.demo}")
                listOf("equipment" to c.ble.state.value,"heart" to c.heart.state.value).forEach { (role,state) ->
                    appendLine("$role: ${state.phase} ${state.protocol} ${state.machine} range=${state.range} writable=${state.writable}")
                    appendLine("  subscriptions=${state.subscriptions} received=${state.receivedPackets} parsed=${state.parsedPackets} dataReceived=${state.dataReceived}")
                    appendLine("  lastPacketAgeMs=${state.lastReceiveAt.takeIf { it>0 }?.let { android.os.SystemClock.elapsedRealtime()-it }} controlFeedbackTimedOut=${state.controlTimedOut}")
                    state.diagnostic.takeLast(64).forEach { appendLine(clean(it).take(160)) }
                }
        }
        // State header is bounded as well. Ring files together never exceed 96 KiB.
        header.take(4096)+"\n"+recent
    }
}
