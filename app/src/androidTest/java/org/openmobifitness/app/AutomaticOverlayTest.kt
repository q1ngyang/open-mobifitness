package org.openmobifitness.app

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.io.File

class AutomaticOverlayTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun awaitState(condition: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+30_000
        while(!condition() && android.os.SystemClock.elapsedRealtime()<end) Thread.sleep(100)
        assertTrue(condition())
    }
    private fun screenshot(name: String) {
        val image=instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    @Test fun homeAutoFloatsLongValuesFitAndBothPreferencesAreRespected() {
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        fun launchApp() {
            device.executeShellCommand("am start -W --activity-single-top --activity-clear-top -n ${instrumentation.targetContext.packageName}/org.openmobifitness.app.MainActivity")
        }
        fun foreground(): MainActivity? {
            var activity: MainActivity?=null
            instrumentation.runOnMainSync { activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            return activity
        }
        launchApp(); awaitState { foreground()!=null && c.state.value.ready }
        val activity=foreground()!!
        instrumentation.runOnMainSync {
            c.setTheme("light"); c.display.floating(true); c.display.automaticFloating(true)
            DisplayScope.entries.forEach { c.display.save(it,it.defaults) }
            c.setDemo(true); c.select(Presets.all[0]); activity.startTraining()
        }
        awaitState { (c.state.value.session?.elapsedMs ?: 0)>1500 }
        val expand=activity.getString(R.string.expand_panel)
        val expanded=activity.getString(R.string.expanded_panel)
        val open=activity.getString(R.string.open_app)
        val id=c.state.value.session!!.id
        try {
            device.pressHome()
            val compact=device.wait(Until.findObject(By.desc(expand)),30_000)
            assertNotNull("Home should open the small panel automatically",compact)
            val bounds=compact.visibleBounds
            instrumentation.runOnMainSync { c.pauseResume() }; awaitState { c.state.value.paused }
            instrumentation.runOnMainSync {
                c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(elapsedMs=359999000,caloriesKcal=99999.9,caloriesEstimated=true,distanceM=9999999.0,distanceEstimated=true))
            }
            assertNotNull(device.wait(Until.findObject(By.text("99:59:59")),30_000))
            assertEquals(bounds,device.findObject(By.desc(expand)).visibleBounds)
            screenshot("overlay-long-compact")
            device.findObject(By.desc(expand)).click()
            val large=device.wait(Until.findObject(By.desc(expanded)),30_000)
            assertNotNull(large)
            val largeBounds=large.visibleBounds
            screenshot("overlay-long-expanded")
            instrumentation.runOnMainSync { c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(caloriesKcal=1.0,distanceM=10.0)) }
            Thread.sleep(1500)
            assertEquals(largeBounds,device.findObject(By.desc(expanded)).visibleBounds)
            device.findObject(By.desc(open)).click()
            awaitState { foreground()?.hasWindowFocus()==true }
            assertNotNull(device.wait(Until.findObject(By.text(activity.getString(R.string.finish_save))),30_000))
            assertEquals(id,c.state.value.session!!.id)
            assertFalse(device.hasObject(By.desc(expand)))
            instrumentation.runOnMainSync { c.display.automaticFloating(false) }
            device.pressHome(); Thread.sleep(1600)
            assertFalse("Auto floating can be disabled",device.hasObject(By.desc(expand)))
            launchApp(); awaitState { foreground()!=null }
            awaitState { foreground()?.hasWindowFocus()==true }
            instrumentation.runOnMainSync { c.display.automaticFloating(true); c.display.floating(false) }
            assertTrue(device.wait(Until.gone(By.desc(activity.getString(R.string.minimize))),15_000))
            device.pressHome(); Thread.sleep(1600)
            assertFalse("The master switch disables automatic floating too",device.hasObject(By.desc(expand)))
        } finally {
            launchApp(); awaitState { foreground()!=null }
            instrumentation.runOnMainSync { c.finish(); c.display.floating(true); c.display.automaticFloating(true) }
            awaitState { c.state.value.session==null }
            val current=foreground(); instrumentation.runOnMainSync { current?.finish() }
        }
    }
}
