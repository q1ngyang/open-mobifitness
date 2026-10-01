package org.openmobifitness.app.data

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant

class BoundedLogStoreTest {
    private fun withStore(test: (File)->Unit) {
        val base=System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")
        val dir=File(base,"openmobi-logs-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try { test(dir) } finally { dir.deleteRecursively() }
    }
    private fun line(at: Long,event: String,detail: String)="{\"at\":\"${Instant.ofEpochMilli(at)}\",\"event\":\"$event\",\"detail\":\"$detail\"}"
    @Test fun packetFloodDoesNotHideErrorsAndUtf8BudgetIsBounded() = withStore { dir ->
        val now=1_800_000_000_000L; val store=BoundedLogStore(dir) { now }
        store.append(line(now,"control_timeout","important error"))
        repeat(1800) { store.append(line(now,"packet","报文数据".repeat(25)+it)) }
        val text=store.snapshot()
        assertTrue(text.contains("important error")); assertTrue(text.contains("1799"))
        assertTrue(text.toByteArray().size<98*1024)
        assertTrue(File(dir,"packets.jsonl").length()<=32*1024)
        assertFalse(text.contains('\uFFFD'))
        assertEquals(text,BoundedLogStore(dir) { now }.snapshot())
    }
    @Test fun retentionMigratesOldFilesAndClearSurvivesRestart() = withStore { dir ->
        var now=1_800_000_000_000L
        File(dir,"previous.jsonl").writeText(line(now-72*3600_000,"app_start","expired")+"\n"+line(now-26*3600_000,"session_start","old event")+"\n")
        File(dir,"events.jsonl").writeText(line(now-31*60_000,"packet","expired packet")+"\n"+line(now,"packet","new packet")+"\n"+line(now,"ble","new event")+"\n")
        val store=BoundedLogStore(dir) { now }; val text=store.snapshot()
        assertFalse(text.contains("expired")); assertFalse(text.contains("old event")); assertTrue(text.contains("new packet")); assertTrue(text.contains("new event"))
        assertFalse(File(dir,"previous.jsonl").exists())
        now+=31*60_000; assertFalse(store.snapshot().contains("new packet"))
        now+=49*3600_000; assertFalse(store.snapshot().contains("new event"))
        assertEquals(0L,File(dir,"events.jsonl").length())
        store.append(line(now,"ble","clear me")); store.clear()
        assertFalse(BoundedLogStore(dir) { now }.snapshot().contains("clear me"))
    }
}
