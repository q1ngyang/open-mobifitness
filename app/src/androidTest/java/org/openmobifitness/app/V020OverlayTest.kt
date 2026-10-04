package org.openmobifitness.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.app.data.saveUser
import org.openmobifitness.core.UserProfile
import org.openmobifitness.core.IdentityPolicy
import java.io.File

/** Native overlay gestures and mode transitions with Debug readings, not radio I/O. */
class V020OverlayTest {
    @Test fun controlPanelDragsWithoutChangingResistanceAndRecordingReturnsToTheApp() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        fun main(block: ()->Unit)=instrumentation.runOnMainSync(block)
        fun await(condition: ()->Boolean) {
            val end=SystemClock.elapsedRealtime()+30000
            while(!condition() && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
            assertTrue(condition())
        }
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"v020-overlay/${args.getString("layout") ?: "phone"}").apply { mkdirs() }
        fun screen(name: String) {
            Thread.sleep(350)
            val picture=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            File(directory,"$name.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG,100,it) }; picture.recycle()
            val cfg=instrumentation.targetContext.resources.configuration
            File(directory,"environment.txt").writeText("window=${cfg.screenWidthDp}x${cfg.screenHeightDp} font=${cfg.fontScale} api=${android.os.Build.VERSION.SDK_INT} theme=${c.theme.value} locale=${instrumentation.targetContext.localized().resources.configuration.locales.toLanguageTags()}\nDebug simulation; no GATT\n")
        }
        fun text(id: Int)=instrumentation.targetContext.localized().getString(id)
        fun clickDescription(description: String) {
            assertTrue(device.wait(Until.hasObject(By.desc(description)),15000))
            device.findObjects(By.desc(description)).minBy { it.visibleBounds.width()*it.visibleBounds.height() }.click()
        }
        fun compactBounds(): android.graphics.Rect {
            // Android 10's accessibility window keeps its original outer bounds
            // after updateViewLayout, although the live root reports its new
            // screen coordinates. Use that root for real drag coordinates.
            // Screenshots and state assertions still verify every gesture.
            if(android.os.Build.VERSION.SDK_INT==29) {
                var bounds: android.graphics.Rect?=null
                await {
                    val root=instrumentation.uiAutomation.windows.mapNotNull { it.root }.firstOrNull { it.contentDescription==text(R.string.expand_panel) }
                    bounds=root?.let { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) } }
                    bounds?.isEmpty==false
                }
                return bounds!!
            }
            var panels=emptyList<UiObject2>()
            await {
                panels=device.findObjects(By.desc(text(R.string.expand_panel)))
                panels.isNotEmpty()
            }
            return panels.maxBy { it.visibleBounds.width()*it.visibleBounds.height() }.visibleBounds
        }
        var recorded: String?=null
        try {
            await { c.state.value.ready }
            runBlocking {
                val user=UserProfile(java.util.UUID.nameUUIDFromBytes("v030-overlay-user".toByteArray()).toString(),"小林 Overlay")
                c.repo.saveUser(user); c.switchUser(user.id).join()
            }
            main { c.setIdentityPolicy(IdentityPolicy.REMEMBER) }
            main { c.dismissResult(); c.exitControl(); c.setDemo(true); c.setImperial(false); c.display.floating(true); c.display.automaticFloating(true); c.local.resetPositions(); c.setTheme(args.getString("theme") ?: "light") }
            args.getString("language")?.let { language -> scenario.onActivity { if(it.currentLanguage()!=language) it.setLanguage(language) } }
            val before=runBlocking(Dispatchers.IO) { c.repo.db.records().sessionCount() }
            scenario.onActivity { it.startControl() }
            await { c.state.value.controlOnly }
            // Wait for the control screen to finish entering before leaving it.
            // The state changes before Compose has laid out this screen, especially
            // after changing locale/font scale on software-rendered emulators.
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.start_recording))),30000))
            device.waitForIdle(2000)
            device.pressHome()
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expand_panel))),30000))
            screen("01-control-compact")
            val resistance=c.controlResistance()
            for((index,corner) in listOf(0 to 0,1 to 0,1 to 1,0 to 1).withIndex()) {
                val panel=compactBounds()
                val x=panel.left+50; val y=panel.top+36
                val left=if(corner.first==0) 0 else device.displayWidth-panel.width()
                val top=if(corner.second==0) 0 else device.displayHeight-panel.height()
                device.drag(x,y,(x+left-panel.left).coerceIn(1,device.displayWidth-1),(y+top-panel.top).coerceIn(1,device.displayHeight-1),24)
                Thread.sleep(300)
                assertEquals(resistance,c.controlResistance())
                assertNull(c.state.value.session)
                screen("02-corner-$index")
            }
            assertTrue(c.local.position(false)!=null || c.local.position(true)!=null)
            assertEquals(before,runBlocking(Dispatchers.IO) { c.repo.db.records().sessionCount() })
            // AOSP Launcher can lock portrait. Use a real rotatable background
            // Activity so device rotation actually changes the overlay's display.
            device.executeShellCommand("am start -W -n ${instrumentation.context.packageName}/${OverlayRotationActivity::class.java.name}")
            compactBounds()
            val initialLandscape=device.displayWidth>device.displayHeight
            val position=c.local.position(initialLandscape)
            device.setOrientationLeft()
            await { (device.displayWidth>device.displayHeight)!=initialLandscape }
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expand_panel))),15000))
            // Software-rendered rotation can finish after display dimensions change.
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expand_panel))),15000))
            device.waitForIdle(2000); Thread.sleep(8000)
            screen("02-rotated")
            device.setOrientationNatural()
            await { (device.displayWidth>device.displayHeight)==initialLandscape }
            assertEquals("Placement for the original aspect survives rotation",position,c.local.position(initialLandscape))
            // Software-rendered rotation can finish after display dimensions change.
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expand_panel))),15000))
            device.waitForIdle(2000); Thread.sleep(8000)
            screen("02-position-restored")
            clickDescription(text(R.string.expand_panel))
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.expanded_panel))),15000))
            screen("03-control-expanded")
            // Resistance shares the scrollable information area on short/large-font panels.
            var plus=device.findObject(By.desc(text(R.string.increase)))
            repeat(5) {
                if(plus==null || plus!!.visibleBounds.height()<30) {
                    device.findObject(By.clazz("android.widget.ScrollView"))?.scroll(Direction.DOWN,.8f)
                    plus=device.findObject(By.desc(text(R.string.increase)))
                }
            }
            assertNotNull(plus); plus!!.click(); await { c.controlResistance()!!>resistance!! }
            val selected=c.controlResistance()
            clickDescription(text(R.string.start_recording))
            await { c.state.value.session!=null }
            recorded=c.state.value.session!!.id
            assertEquals(selected,c.controlResistance())
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.pause_short))),15000))
            screen("04-recording-expanded")
            // Recording adds another metric row. Reach the resistance controls
            // through the actual native scroll area above the fixed action dock.
            var decrease=device.findObject(By.desc(text(R.string.decrease)))
            repeat(5) {
                if(decrease==null || decrease!!.visibleBounds.height()<40) {
                    device.findObject(By.clazz("android.widget.ScrollView"))?.scroll(Direction.DOWN,.8f)
                    decrease=device.findObject(By.desc(text(R.string.decrease)))
                }
            }
            assertNotNull(decrease); decrease!!.click(); await { c.controlResistance()!!<selected!! }
            clickDescription(text(R.string.increase)); await { c.controlResistance()==selected }
            screen("04-recording-resistance-reached")
            clickDescription(text(R.string.pause_short)); await { c.state.value.paused }
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.resume))),15000))
            screen("05-recording-paused")
            clickDescription(text(R.string.open_app))
            assertTrue(device.wait(Until.hasObject(By.desc(text(R.string.finish_save))),30000))
            main { c.finish() }; await { c.state.value.session==null }
            assertTrue(c.state.value.controlOnly)
            clickDescription(text(R.string.close))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.control_only))),15000))
            screen("06-back-in-control")
        } catch(error: Throwable) {
            screen("failure")
            device.dumpWindowHierarchy(File(directory,"failure-hierarchy.xml"))
            File(directory,"failure-windows.txt").writeText(instrumentation.uiAutomation.windows.joinToString("\n") { window ->
                "window=$window root=${window.root}"
            })
            throw error
        } finally {
            main { if(c.state.value.session!=null) c.finish() }
            await { c.state.value.session==null }
            main { c.dismissResult(); c.exitControl(); instrumentation.targetContext.stopService(Intent(instrumentation.targetContext,WorkoutService::class.java)) }
            recorded?.let { id -> runBlocking { c.repo.deleteSession(id) } }
            device.setOrientationNatural(); device.unfreezeRotation()
            scenario.close()
        }
    }
}

/** Test-APK-only background for native rotation; no production UI or telemetry. */
class OverlayRotationActivity: android.app.Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        setContentView(android.widget.TextView(this).apply {
            text="Overlay rotation · test background"
            gravity=android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.DKGRAY)
            setBackgroundColor(android.graphics.Color.rgb(238,241,245))
        })
    }
}
