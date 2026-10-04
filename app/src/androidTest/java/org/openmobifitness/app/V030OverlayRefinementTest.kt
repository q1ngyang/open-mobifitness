package org.openmobifitness.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.app.data.saveUser
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.io.File
import java.util.UUID

class V030OverlayRefinementTest {
    @Test fun selectedMetricCountResizesTheRealWindowAndKeepsControlsReachable() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val c=(inst.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(inst)
        Configurator.getInstance().waitForIdleTimeout=100
        inst.uiAutomation.serviceInfo=inst.uiAutomation.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun main(action: ()->Unit)=inst.runOnMainSync(action)
        fun await(check: ()->Boolean) {
            val until=SystemClock.elapsedRealtime()+60000
            while(!check() && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertTrue(check())
        }
        fun text(id: Int)=inst.targetContext.localized().getString(id)
        val name=InstrumentationRegistry.getArguments().getString("case","tablet")
        val folder=File(inst.targetContext.getExternalFilesDir(null),"refined-overlay/$name").apply { mkdirs() }
        fun screenshot(label: String) {
            Thread.sleep(800)
            val bitmap=inst.uiAutomation.takeScreenshot()!!
            File(folder,"$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
        fun bounds()=device.findObjects(By.desc(text(R.string.expanded_panel))).maxBy { it.visibleBounds.height() }.visibleBounds
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            await { c.state.value.ready }
            runBlocking { val u=UserProfile(UUID.nameUUIDFromBytes("refine-overlay-user".toByteArray()).toString(),"林 Overlay"); c.repo.saveUser(u); c.switchUser(u.id).join() }
            val ids=listOf(MetricId.CADENCE,MetricId.HEART,MetricId.POWER,MetricId.SPEED,MetricId.DISTANCE,MetricId.CALORIES)
            main {
                c.setIdentityPolicy(IdentityPolicy.REMEMBER); c.setDemo(true); c.dismissResult(); c.setTheme(if(name.contains("dark")) "dark" else "light")
                c.display.floating(true); c.display.automaticFloating(true); c.local.resetPositions()
                c.display.save(DisplayScope.COMPACT,ids.take(2),Machine.ELLIPTICAL)
                c.display.save(DisplayScope.EXPANDED,ids.take(4),Machine.ELLIPTICAL)
            }
            scenario.onActivity { it.startControl() }; await { c.state.value.controlOnly }
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.start_recording))),60000))
            device.pressHome()
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expand_panel))),30000))
            screenshot("compact")
            assertTrue(device.hasObject(By.text("林 Overlay")))
            device.findObjects(By.desc(text(R.string.expand_panel))).minBy { it.visibleBounds.height() }.click()
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expanded_panel))),30000))
            val heights=mutableListOf<Int>()
            for(count in listOf(2,3,4,6)) {
                main { c.display.save(DisplayScope.EXPANDED,ids.take(count),Machine.ELLIPTICAL) }
                Thread.sleep(1800); heights+=bounds().height(); screenshot("expanded-$count")
                assertTrue(device.hasObject(By.desc(text(R.string.open_app))))
                assertTrue(device.hasObject(By.desc(text(R.string.start_recording))))
            }
            if(inst.targetContext.resources.configuration.fontScale<=1.1f && device.displayHeight>600) assertTrue("Height follows rows, including an odd metric count: $heights",heights[0]<heights[1] && heights[1]==heights[2] && heights[2]<heights[3])
            var plus=device.findObject(By.desc(text(R.string.increase)))
            repeat(6) { if(plus==null || plus!!.visibleBounds.height()<40) {
                val scroll=device.findObject(By.desc(text(R.string.expanded_panel)))?.findObject(By.clazz("android.widget.ScrollView"))
                assertNotNull("The floating panel exposes its scroll area",scroll)
                val area=scroll!!.visibleBounds
                // Scope the gesture to this window and allow software-rendered frames to catch up.
                device.swipe(area.centerX(),area.bottom-16,area.centerX(),area.top+16,48)
                Thread.sleep(500)
                plus=device.findObject(By.desc(text(R.string.increase)))
            } }
            assertNotNull(plus)
            val before=c.controlResistance()!!; plus!!.click(); await { c.controlResistance()!!>before }
            device.findObject(By.desc(text(R.string.start_recording))).click(); await { c.state.value.session!=null }
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.pause_short))),30000))
            screenshot("expanded-recording")
        } finally {
            main { if(c.state.value.session!=null) c.finish() }; await { c.state.value.session==null }
            main { c.dismissResult(); c.exitControl(); inst.targetContext.stopService(Intent(inst.targetContext,WorkoutService::class.java)) }
            scenario.close()
        }
    }
}
