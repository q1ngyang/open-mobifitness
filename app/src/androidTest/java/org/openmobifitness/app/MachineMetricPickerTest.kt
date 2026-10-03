package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.io.File

class MachineMetricPickerTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun switchingConnectedMachineWhilePickerIsOpenReplacesChoicesAndKeepsSettingsSeparate() {
        val c=(compose.activity.application as OpenMobiApp).controller
        compose.waitUntil(30_000) { c.state.value.ready }
        val prior=c.display.byMachine.value
        try {
            compose.runOnUiThread {
                c.dismissResult(); c.setDemo(true,Machine.ELLIPTICAL)
                c.display.save(DisplayScope.TRAINING,listOf(MetricId.CADENCE,MetricId.RESISTANCE),Machine.ELLIPTICAL)
                c.display.save(DisplayScope.TRAINING,listOf(MetricId.STROKE_RATE,MetricId.STROKES),Machine.ROWER)
                compose.activity.metricScope.value=DisplayScope.TRAINING
            }
            compose.onNodeWithText(compose.activity.getString(R.string.cadence)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.strokes)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.stroke_rate)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.step_rate)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.incline)).assertDoesNotExist()
            // The same state flow is updated by BLE model identification; no need to close/reopen the dialog.
            compose.runOnUiThread { c.setDemo(true,Machine.ROWER) }
            compose.onNodeWithText(compose.activity.getString(R.string.stroke_rate)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.strokes)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.cadence)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.incline)).assertDoesNotExist()
            compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.move_up))[1].performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
            assertEquals(MetricId.STROKES,DisplayPreferences(c.prefs).selected(DisplayScope.TRAINING,Machine.ROWER).first())
            assertEquals(listOf(MetricId.CADENCE,MetricId.RESISTANCE),DisplayPreferences(c.prefs).selected(DisplayScope.TRAINING,Machine.ELLIPTICAL))
            compose.waitForIdle()
            compose.runOnIdle { c.setDemo(true,Machine.ELLIPTICAL); compose.activity.metricScope.value=DisplayScope.TRAINING }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("metric-picker").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.cadence)).assertIsDisplayed()
            val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if(screenshot!=null) {
                val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
                File(dir,"elliptical-metrics.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                screenshot.recycle()
            }
            compose.runOnUiThread { c.setDemo(true,Machine.TREADMILL) }
            compose.onNodeWithText(compose.activity.getString(R.string.incline)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.stroke_rate)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.cadence)).assertDoesNotExist()
        } finally {
            compose.runOnUiThread {
                compose.activity.metricScope.value=null
                Machine.entries.forEach { machine -> DisplayScope.entries.forEach { scope ->
                    c.prefs.edit().remove("${scope.key}.${machine.name}").commit()
                } }
                c.display.byMachine.value=prior
                prior.forEach { (machine,scopes) -> scopes.forEach { (scope,items) -> c.display.save(scope,items,machine) } }
                c.setDemo(false)
            }
        }
    }
}
