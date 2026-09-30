package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.Presets
import java.io.File

class LanguageLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun sixLanguagesAndRecreationKeepTheSameWorkout() {
        val c=(compose.activity.application as OpenMobiApp).controller
        compose.waitUntil(30_000) { c.state.value.ready }
        compose.runOnUiThread { c.setDemo(true); c.select(Presets.all[2]); compose.activity.startTraining() }
        compose.waitUntil(30_000) { c.state.value.session!=null }
        val id=c.state.value.session!!.id
        val variants=listOf("en" to "Train","zh-Hans" to "训练","zh-Hant" to "訓練","ja" to "トレーニング","ko" to "운동","de" to "Training")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val device=InstrumentationRegistry.getArguments().getString("layout") ?: "phone"
        for((tag,label) in variants) {
            compose.runOnUiThread { compose.activity.setLanguage(tag) }
            // Locale changes recreate the Activity; the Compose root is briefly absent.
            compose.waitUntil(30_000) { runCatching { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.waitForIdle()
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
            if(screenshot!=null) { File(dir,"$device-$tag.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle() }
            assertEquals(id,c.state.value.session!!.id)
        }
        compose.runOnUiThread { c.finish() }
        compose.waitUntil(30_000) { c.state.value.session==null }
    }
}
