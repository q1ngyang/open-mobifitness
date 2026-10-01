package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FloatingSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun switchesAreReachableInSettingsAndPersist() {
        val c = (compose.activity.application as OpenMobiApp).controller
        compose.waitUntil(30_000) { c.state.value.ready }
        compose.runOnUiThread {
            c.display.floating(true); c.display.automaticFloating(true)
            compose.activity.focusTraining.value = false; compose.activity.page.value = 3
        }
        val auto = compose.activity.getString(R.string.auto_floating)
        val master = compose.activity.getString(R.string.floating_enabled)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription(auto))
        compose.onNodeWithContentDescription(auto).assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription(master).assertIsOn()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        if (screenshot != null) {
            val dir = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(dir, "floating-settings.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
        }
        compose.onNodeWithContentDescription(auto).performClick().assertIsOff()
        assertFalse(DisplayPreferences(c.prefs).autoFloating.value)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription(auto))
        compose.onNodeWithContentDescription(auto).assertIsOff()
        compose.onNodeWithContentDescription(master).performClick()
        compose.onNodeWithContentDescription(auto).assertIsNotEnabled()
        assertFalse(DisplayPreferences(c.prefs).floatingEnabled.value)
        compose.onNodeWithContentDescription(master).performClick()
        compose.onNodeWithContentDescription(auto).performClick().assertIsOn()
    }
}
