package org.openmobifitness.app

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.io.File

/** Host varies display size/density, font scale and theme. All screenshots are real app renders. */
class WorkoutLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private fun screenshot(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val image=instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    @After fun finish() {
        compose.runOnUiThread { c.finish() }
        compose.waitUntil(30_000) { c.state.value.session==null }
    }
    @Test fun stagePreviewAndSaveDockStayReachable() {
        val args=InstrumentationRegistry.getArguments()
        val layout=args.getString("layout") ?: "phone"
        val config=compose.activity.resources.configuration
        println("Window: ${config.screenWidthDp} x ${config.screenHeightDp} dp; font=${config.fontScale}")
        compose.waitUntil(30_000) { c.state.value.ready }
        compose.runOnUiThread {
            c.setTheme(args.getString("theme") ?: "light")
            c.display.floating(true); c.display.automaticFloating(true)
            DisplayScope.entries.forEach { c.display.save(it,it.defaults) }
            c.setDemo(true); c.select(Presets.all.first { it.id=="hiit40" }); compose.activity.startTraining()
        }
        compose.waitUntil(30_000) { (c.state.value.session?.elapsedMs ?: 0)>1500 }
        compose.runOnUiThread { c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(elapsedMs=21*60_000L+36_000)) }
        compose.waitUntil(30_000) { c.state.value.stage>0 }
        compose.onNodeWithTag("finish").assertIsDisplayed()
        val saveText=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(compose.activity.getString(R.string.finish),useUnmergedTree=true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(saveText) }
        assertTrue("The complete save label must fit",saveText.isNotEmpty() && saveText.none { it.hasVisualOverflow })
        compose.onNodeWithTag("pause").assertIsDisplayed()
        compose.onNodeWithTag("float").assertIsDisplayed()
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON!=0)
        screenshot("$layout-training")
        val next=compose.activity.getString(R.string.next_metrics)
        if(compose.onAllNodesWithContentDescription(next).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(next).performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.power)).assertIsDisplayed()
            screenshot("$layout-metrics-page2")
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.previous_metrics)).performClick()
        }
        compose.onNodeWithTag("stage-preview",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.stage_overview)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.stage_overview)).assertIsDisplayed()
        screenshot("$layout-stages")
        compose.onNodeWithText(compose.activity.getString(R.string.close)).performClick()
        // Long pages can scroll, while the action dock must remain fixed.
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.resistance_slider)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("finish").assertIsDisplayed()
        screenshot("$layout-controls")
        compose.onNodeWithTag("finish").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.finish_note)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        assertNotNull(c.state.value.session)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back_to_app)).performClick()
        compose.waitForIdle()
        assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id=c.state.value.session!!.id
        compose.runOnUiThread { compose.activity.page.value=0; compose.activity.focusTraining.value=true }
        compose.onNodeWithTag("finish").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.finish)).onLast().performClick()
        compose.waitUntil(30_000) { c.state.value.session==null }
        assertTrue(c.repo.sessions.value.any { it.id==id && it.demo })

    }
}
