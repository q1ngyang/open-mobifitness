package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.*
import org.openmobifitness.app.data.AppLog
import kotlinx.coroutines.runBlocking
import java.io.File

class DisplayAndDiagnosticsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @After fun restorePreferencesAndCloseDemo() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        instrumentation.runOnMainSync { c.finish(); c.display.packetLogs(false); DisplayScope.entries.forEach { c.display.save(it,it.defaults) } }
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        while(c.state.value.session!=null && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
    }
    @Test fun chooseOrderPersistMetricsAndExportLocalDiagnostics() {
        val c=(compose.activity.application as OpenMobiApp).controller
        compose.waitUntil(30_000) { c.state.value.ready }
        compose.runOnUiThread {
            c.display.save(DisplayScope.TRAINING,listOf(MetricId.STROKE_RATE,MetricId.STROKES,MetricId.FORCE,MetricId.CALORIES,MetricId.DISTANCE,MetricId.HEART))
            c.setDemo(true,Machine.ROWER); c.select(Presets.all.first { it.id=="hiit" }); compose.activity.startTraining()
        }
        compose.waitUntil(30_000) { c.state.value.session?.elapsedMs?.let { it>2500 }==true }
        val id=c.state.value.session!!.id
        compose.onNodeWithText(compose.activity.getString(R.string.stroke_rate)).assertIsDisplayed()
        assertNotNull(c.state.value.strokeCount)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.choose_metrics)).performClick()
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.move_up))[1].performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.waitUntil(10_000) { c.display.selections.value.getValue(DisplayScope.TRAINING).first()==MetricId.STROKES }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { runCatching { compose.onNodeWithContentDescription(compose.activity.getString(R.string.choose_metrics)).fetchSemanticsNode(); true }.getOrDefault(false) }
        assertEquals(id,c.state.value.session!!.id)
        assertEquals(MetricId.STROKES,DisplayPreferences(c.prefs).selections.value.getValue(DisplayScope.TRAINING).first())
        val count=c.display.selections.value.getValue(DisplayScope.TRAINING).size
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.choose_metrics)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.compact_panel)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.expanded_panel)).performClick()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if(image!=null) { val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }; File(dir,"metric-picker.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        assertEquals(count,c.display.selections.value.getValue(DisplayScope.TRAINING).size)
        AppLog.event("test_redaction","address=AA:BB:CC:DD:EE:FF")
        val packet=java.nio.ByteBuffer.allocate(8).putLong(System.nanoTime()).array()
        val hex=packet.joinToString("") { "%02x".format(it) }
        c.display.packetLogs(false); AppLog.packet("tx","8812",packet)
        Thread.sleep(300)
        val report=runBlocking { AppLog.report(c) }
        assertTrue(report.contains("test_redaction")); assertTrue(report.contains("[address]")); assertFalse(report.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(report.contains(hex)); assertTrue(report.contains("session_start"))
        File(compose.activity.getExternalFilesDir(null),"diagnostic-test.txt").writeText(report)
        c.display.packetLogs(true); AppLog.packet("tx","8812",packet); Thread.sleep(300)
        assertTrue(runBlocking { AppLog.report(c) }.contains(hex))
        c.display.packetLogs(false)
        compose.runOnUiThread { c.finish(); DisplayScope.entries.forEach { c.display.save(it,it.defaults) } }
        compose.waitUntil(30_000) { c.state.value.session==null }
        for(machine in listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL)) {
            compose.runOnUiThread { c.setDemo(true,machine) }
            val cadence=compose.activity.reading(MetricId.CADENCE,c)
            assertEquals(compose.activity.getString(R.string.cadence),cadence.label)
            assertEquals(if(machine in listOf(Machine.ROWER,Machine.TREADMILL)) "spm" else "rpm",cadence.unit)
        }
        compose.runOnUiThread { c.setDemo(true,Machine.ELLIPTICAL) }
    }
}
