package org.openmobifitness.app

import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Configurator
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.Presets
import java.io.File

/** Real Android UI/service check with a simulated device. Does not validate radio/hardware. */
class ExerciseFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val controller get()=(compose.activity.application as OpenMobiApp).controller
    @After fun cleanup() {
        compose.runOnUiThread { if(controller.state.value.session!=null) controller.finish() }
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        while(controller.state.value.session!=null && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
    }
    private fun screenshot(name: String) {
        val image=instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun demoTrainingSurvivesOverlayRoundTripAndSavesHistory() {
        compose.waitUntil(30_000) { controller.state.value.ready }
        compose.runOnUiThread { controller.dismissResult(); controller.setDemo(true); controller.select(Presets.all.first()); compose.activity.startTraining() }
        compose.waitUntil(30_000) { controller.state.value.session!=null }
        val id=controller.state.value.session!!.id
        compose.waitUntil(30_000) { controller.state.value.session!!.elapsedMs>1000 }
        val automatic=compose.activity.getString(R.string.auto)
        compose.onNodeWithContentDescription(automatic).performScrollTo().assertIsOn().performClick()
        val heldResistance=controller.state.value.metrics.resistance
        assertFalse(controller.state.value.automatic)
        Thread.sleep(2200)
        assertEquals(heldResistance,controller.state.value.metrics.resistance)
        compose.onNodeWithContentDescription(automatic).performClick().assertIsOn()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.resistance_slider))
            .performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(6f) }
        compose.waitUntil(20_000) { controller.state.value.metrics.resistance==6.0 && !controller.state.value.automatic }
        compose.onNodeWithText("+").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(20_000) { !controller.state.value.automatic }
        screenshot("phone-live")
        assertNotNull(controller.state.value.session!!.caloriesKcal)
        assertNotNull(controller.state.value.session!!.distanceM)
        compose.runOnUiThread { controller.pauseResume() }
        compose.waitUntil(20_000) { controller.state.value.paused }
        val paused=controller.state.value.session!!
        val countdown=controller.state.value.remainingMs
        // Wait through multiple real ticker updates, not virtual Compose time.
        Thread.sleep(2200)
        assertEquals(paused.elapsedMs,controller.state.value.session!!.elapsedMs)
        assertEquals(paused.caloriesKcal,controller.state.value.session!!.caloriesKcal)
        assertEquals(paused.distanceM,controller.state.value.session!!.distanceM)
        assertEquals(countdown,controller.state.value.remainingMs)
        // Host grants SYSTEM_ALERT_WINDOW only to the test APK before running this test.
        compose.onNodeWithTag("float").assertIsDisplayed().performClick()
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        val compact=device.wait(Until.findObject(By.desc(compose.activity.getString(R.string.expand_panel))),30_000)
        screenshot("overlay-after-minimize")
        assertNotNull("Small info-only panel should be visible",compact)
        assertFalse(device.hasObject(By.desc(compose.activity.getString(R.string.open_app))))
        screenshot("phone-overlay-small")
        val center=compact.visibleCenter
        device.swipe(center.x,center.y,center.x+25,center.y+90,20)
        assertFalse("Dragging must not expand the panel",device.hasObject(By.desc(compose.activity.getString(R.string.open_app))))
        device.findObject(By.desc(compose.activity.getString(R.string.expand_panel))).click()
        val shrink=device.wait(Until.findObject(By.desc(compose.activity.getString(R.string.collapse_panel))),30_000)
        assertNotNull(shrink)
        screenshot("phone-overlay-large")
        shrink.click()
        val smallAgain=device.wait(Until.findObject(By.desc(compose.activity.getString(R.string.expand_panel))),30_000)
        assertNotNull(smallAgain); smallAgain.click()
        val open=device.wait(Until.findObject(By.desc(compose.activity.getString(R.string.open_app))),30_000)
        assertNotNull("Expanded panel should offer return to training",open)
        open.click()
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        assertEquals(id,controller.state.value.session!!.id)
        assertTrue(controller.state.value.paused)
        compose.runOnUiThread { controller.finish() }
        compose.waitUntil(30_000) { controller.state.value.session==null }
        assertTrue(controller.repo.sessions.value.any { it.id==id && it.demo && it.elapsedMs>0 })
        compose.runOnUiThread { compose.activity.page.value=1 }
        compose.waitForIdle(); screenshot("phone-history")
    }
}
