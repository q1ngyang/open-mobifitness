package org.openmobifitness.app.data

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.ArrayDeque

/** Separate budgets keep high-rate packet traces from evicting connection/control errors. */
internal class BoundedLogStore(private val folder: File,private val now: ()->Long=System::currentTimeMillis) {
    private data class Entry(val line: String,val at: Long) { val bytes=(line+"\n").toByteArray(Charsets.UTF_8).size }
    private class Ring(val file: File,val limit: Int,val age: Long) {
        val entries=ArrayDeque<Entry>(); var size=0
        fun add(e: Entry) { entries.addLast(e); size+=e.bytes }
        fun prune(time: Long): Boolean {
            var changed=false
            val iterator=entries.iterator()
            while(iterator.hasNext()) { val e=iterator.next(); if(e.at<time-age || e.at>time+300_000) { size-=e.bytes; iterator.remove(); changed=true } }
            while(size>limit) { size-=entries.removeFirst().bytes; changed=true }
            return changed
        }
        fun rewrite() { file.writeText(entries.joinToString("") { it.line+"\n" }) }
    }
    private val events=Ring(File(folder,"events.jsonl"),64*1024,48*60*60*1000L)
    private val packets=Ring(File(folder,"packets.jsonl"),32*1024,30*60*1000L)
    private var initialized=false
    private val atPattern=Regex("\\\"at\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    private val packetPattern=Regex("\\\"event\\\"\\s*:\\s*\\\"packet\\\"")
    private fun entry(line: String): Entry? = runCatching {
        require(line.toByteArray(Charsets.UTF_8).size<=8192)
        Entry(line,Instant.parse(atPattern.find(line)!!.groupValues[1]).toEpochMilli())
    }.getOrNull()
    private fun load() {
        if(initialized) return
        folder.mkdirs()
        // Read only a bounded tail of old alpha.1–3 files; discard any partial leading line.
        listOf("previous.jsonl","events.jsonl","packets.jsonl").forEach { name ->
            val file=File(folder,name)
            if(file.isFile) RandomAccessFile(file,"r").use { input ->
                val offset=(input.length()-128*1024).coerceAtLeast(0); input.seek(offset)
                val bytes=ByteArray((input.length()-offset).toInt()); input.readFully(bytes)
                val lines=bytes.toString(Charsets.UTF_8).lineSequence().drop(if(offset>0) 1 else 0)
                lines.forEach { line -> entry(line)?.let { e -> (if(packetPattern.containsMatchIn(line)) packets else events).add(e) } }
            }
        }
        listOf(events,packets).forEach { it.prune(now()); it.rewrite() }
        File(folder,"previous.jsonl").delete()
        initialized=true
    }
    @Synchronized fun append(line: String) {
        load()
        val e=entry(line) ?: return
        val ring=if(packetPattern.containsMatchIn(line)) packets else events
        ring.add(e)
        val time=now()
        if(ring.prune(time)) ring.rewrite() else ring.file.appendText(e.line+"\n")
        val other=if(ring===events) packets else events
        if(other.prune(time)) other.rewrite()
    }
    @Synchronized fun snapshot(): String {
        load(); val time=now()
        listOf(events,packets).forEach { if(it.prune(time)) it.rewrite() }
        return buildString {
            appendLine("--- Events: last 24 hours, at most 64 KiB ---")
            events.entries.filter { it.at>=time-24*60*60*1000L }.forEach { appendLine(it.line) }
            appendLine("--- Protocol packets: last 30 minutes, at most 32 KiB ---")
            packets.entries.forEach { appendLine(it.line) }
        }
    }
    @Synchronized fun clear() {
        load()
        listOf(events,packets).forEach { it.entries.clear(); it.size=0; it.rewrite() }
    }
}
