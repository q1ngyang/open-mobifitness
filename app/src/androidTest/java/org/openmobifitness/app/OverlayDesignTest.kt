package org.openmobifitness.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.openmobifitness.core.*
import java.io.File

/** Real application-overlay windows. The host varies viewport, language, theme and font scale. */
class OverlayDesignTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun awaitState(condition: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+30_000
        while(!condition() && android.os.SystemClock.elapsedRealtime()<end) Thread.sleep(70)
        assertTrue("Timed out waiting for overlay state",condition())
    }
    private fun foreground(): MainActivity? {
        var activity: MainActivity?=null
        instrumentation.runOnMainSync {
            activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        }
        return activity
    }
    private fun reachable(device: UiDevice,selector: BySelector): UiObject2? {
        for(direction in listOf(Direction.DOWN,Direction.UP)) repeat(5) {
            device.findObject(selector)?.let { if(it.visibleBounds.height()>=16) return it }
            device.findObject(By.clazz(android.widget.ScrollView::class.java))?.scroll(direction,.7f)
        }
        return device.findObject(selector)
    }
    private fun screenshot(name: String,bounds: android.graphics.Rect?=null) {
        val image=instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        // Capture the actual window region as well as the full display; no UI pixels are altered.
        bounds?.let {
            val region=android.graphics.Bitmap.createBitmap(image,it.left,it.top,it.width(),it.height())
            File(dir,"$name-window.png").outputStream().use { output -> region.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output) }; region.recycle()
        }
        image.recycle()
    }
    @Test fun languagesConfigurationChangesAndMetricShortcutPreserveTheWorkout() {
        val context=instrumentation.targetContext
        val c=(context.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        fun launch() { device.executeShellCommand("am start -W --activity-single-top --activity-clear-top -n ${context.packageName}/org.openmobifitness.app.MainActivity") }
        launch(); awaitState { foreground()!=null && c.state.value.ready }
        val activity=foreground()!!
        instrumentation.runOnMainSync {
            c.setTheme("light"); c.display.floating(true); c.display.automaticFloating(true)
            DisplayScope.entries.forEach { c.display.save(it,it.defaults) }
            c.setDemo(true); c.select(Presets.all.first { it.id=="hiit40" }); activity.startTraining()
        }
        awaitState { (c.state.value.session?.elapsedMs ?: 0)>1500 }
        instrumentation.runOnMainSync { c.pauseResume() }
        awaitState { c.state.value.paused }
        val id=c.state.value.session!!.id
        val held=c.state.value.session!!.elapsedMs
        val originalWidth=device.displayWidth; val originalHeight=device.displayHeight
        try {
            device.pressHome()
            device.wait(Until.findObject(By.desc(context.getString(R.string.expand_panel))),30_000)!!.click()
            for(tag in listOf("zh-Hans","zh-Hant","en","ja","ko","de")) {
                device.executeShellCommand("cmd locale set-app-locales ${context.packageName} --locales $tag")
                val localized=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) })
                val panel=device.wait(Until.findObject(By.desc(localized.getString(R.string.expanded_panel))),30_000)
                assertNotNull("Expanded panel survives locale change: $tag",panel)
                for(label in listOf(R.string.resume,R.string.open_app,R.string.choose_metrics,R.string.collapse_panel,R.string.increase,R.string.decrease)) {
                    assertNotNull("Localized control is reachable: $tag $label",device.wait(Until.findObject(By.desc(localized.getString(label))),10_000))
                }
                screenshot("alpha5-locale-$tag",panel!!.visibleBounds)
                assertEquals(id,c.state.value.session!!.id); assertEquals(held,c.state.value.session!!.elapsedMs)
            }
            // Resize the active display while the actual window remains open, including a
            // short landscape configuration. Unlike restarting a test this exercises Service
            // onConfigurationChanged and window clamping with an existing workout.
            for((width,height) in listOf(originalHeight to originalWidth,originalWidth to originalHeight)) {
                device.executeShellCommand("wm size ${width}x$height")
                awaitState { device.displayWidth==width && device.displayHeight==height }
                val local=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.GERMAN) })
                val panel=device.wait(Until.findObject(By.desc(local.getString(R.string.expanded_panel))),30_000)!!
                for(label in listOf(R.string.resume,R.string.open_app,R.string.increase,R.string.decrease)) {
                    val control=reachable(device,By.desc(local.getString(label)))
                    assertNotNull("Control remains reachable after resize: $label",control)
                    assertTrue("Controls remain on screen after resize",android.graphics.Rect(0,0,width,height).contains(control!!.visibleBounds))
                }
                screenshot("alpha5-live-resize-${width}x$height",panel.visibleBounds)
                assertEquals(id,c.state.value.session!!.id); assertTrue(c.state.value.paused)
            }
            val local=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.GERMAN) })
            device.findObject(By.desc(local.getString(R.string.choose_metrics))).click()
            // The picker Dialog owns window focus; the resumed Activity must retain its scope.
            awaitState { foreground()?.metricScope?.value==DisplayScope.EXPANDED }
            assertEquals(DisplayScope.EXPANDED,foreground()!!.metricScope.value)
            assertNotNull(device.wait(Until.findObject(By.text(local.getString(R.string.metric_selection_help,4))),15_000))
            device.findObject(By.text(local.getString(R.string.compact_panel))).click()
            assertNotNull(device.wait(Until.findObject(By.text(local.getString(R.string.metric_selection_help,2))),15_000))
            device.findObject(By.text(local.getString(R.string.cancel))).click()
            awaitState { foreground()!!.metricScope.value==null }
            assertEquals(DisplayScope.COMPACT.defaults,c.display.selections.value.getValue(DisplayScope.COMPACT))
            assertEquals(id,c.state.value.session!!.id); assertTrue(c.state.value.paused)
        } finally {
            device.executeShellCommand("wm size ${originalWidth}x$originalHeight")
            launch(); awaitState { foreground()!=null }
            instrumentation.runOnMainSync { c.finish() }
            awaitState { c.state.value.session==null }
            val current=foreground(); instrumentation.runOnMainSync { current?.finish() }
        }
    }

    @Test fun stagesControlsLongNumbersAndCornerAnchoringWork() {
        val args=InstrumentationRegistry.getArguments()
        val prefix=args.getString("layout") ?: "overlay"
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        fun launch() { device.executeShellCommand("am start -W --activity-single-top --activity-clear-top -n ${instrumentation.targetContext.packageName}/org.openmobifitness.app.MainActivity") }
        launch(); awaitState { foreground()!=null && c.state.value.ready }
        val activity=foreground()!!
        instrumentation.runOnMainSync {
            c.setTheme(args.getString("theme") ?: "light"); c.display.floating(true); c.display.automaticFloating(true)
            DisplayScope.entries.forEach { c.display.save(it,it.defaults) }
            c.setDemo(true); c.select(Presets.all.first { it.id=="hiit40" }); activity.startTraining()
        }
        awaitState { (c.state.value.session?.elapsedMs ?: 0)>1500 }
        val id=c.state.value.session!!.id
        val expand=activity.getString(R.string.expand_panel)
        val shrink=activity.getString(R.string.collapse_panel)
        val largeDescription=activity.getString(R.string.expanded_panel)
        fun panel()=device.wait(Until.findObject(By.desc(largeDescription)),30_000)!!.visibleBounds
        try {
            instrumentation.runOnMainSync {
                c.adjustTo(12.0)
                c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(elapsedMs=21*60_000L+36_000))
            }
            awaitState { c.state.value.metrics.resistance==12.0 && c.state.value.stage>0 }
            device.pressHome()
            assertNotNull(device.wait(Until.findObject(By.desc(expand)),30_000))
            screenshot("$prefix-small",device.findObject(By.desc(expand)).visibleBounds)
            device.findObject(By.desc(expand)).click()
            val large=panel()
            val increase=activity.getString(R.string.increase); val decrease=activity.getString(R.string.decrease)
            for(label in listOf(increase,decrease,activity.getString(R.string.open_app),activity.getString(R.string.pause_short))) {
                val control=reachable(device,By.desc(label))
                assertNotNull("Control $label remains visible in $prefix",control)
                assertTrue(large.contains(control!!.visibleBounds))
            }
            screenshot("$prefix-large",large)
            // In short windows, readings and resistance share the scroll area;
            // playback and return-to-app actions remain fixed.
            val rate=activity.getString(R.string.cadence)
            // Accessibility may include clipped descendants, so use the window's actual
            // height to exercise scrolling rather than treating node existence as visibility.
            if(large.height()<368*activity.resources.displayMetrics.density-2) {
                val scroller=device.findObject(By.desc(largeDescription)).findObject(By.clazz(android.widget.ScrollView::class.java))
                assertNotNull("Short windows provide an information scroller",scroller)
                if(scroller.visibleBounds.isEmpty) device.dumpWindowHierarchy(File(instrumentation.targetContext.getExternalFilesDir(null),"$prefix-scroll-hierarchy.xml"))
                assertFalse("The overlay scroller has a visible touch region",scroller.visibleBounds.isEmpty)
                scroller.scroll(Direction.UP,1f)
                val rateLabel=reachable(device,By.text(rate))
                assertNotNull(rateLabel)
                screenshot("$prefix-scrolled",large)
                val region=scroller.visibleBounds
                assertTrue("The second row is inside the visible information region: region=$region rate=${rateLabel!!.visibleBounds}",region.contains(rateLabel.visibleBounds))
                assertTrue(large.contains(device.findObject(By.desc(activity.getString(R.string.open_app))).visibleBounds))
            }
            reachable(device,By.desc(increase))!!.click(); awaitState { c.state.value.metrics.resistance==13.0 }
            reachable(device,By.desc(decrease))!!.click(); awaitState { c.state.value.metrics.resistance==12.0 }
            device.findObject(By.desc(activity.getString(R.string.pause_short))).click(); awaitState { c.state.value.paused }
            assertNotNull(device.wait(Until.findObject(By.desc(activity.getString(R.string.resume))),10_000))
            val held=c.state.value.session!!.elapsedMs; val remaining=c.state.value.remainingMs
            Thread.sleep(1600)
            assertEquals(held,c.state.value.session!!.elapsedMs); assertEquals(remaining,c.state.value.remainingMs)
            instrumentation.runOnMainSync {
                c.display.save(DisplayScope.COMPACT,listOf(MetricId.TIME,MetricId.CALORIES))
                c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(elapsedMs=359999000,caloriesKcal=99999.9,caloriesEstimated=true,distanceM=9999999.0,distanceEstimated=true))
            }
            Thread.sleep(1200)
            val longElapsed=reachable(device,By.text("99:59:59"))
            screenshot("$prefix-large-long",large)
            if(longElapsed==null) device.dumpWindowHierarchy(File(instrumentation.targetContext.getExternalFilesDir(null),"$prefix-hierarchy.xml"))
            assertNotNull("Long elapsed time in expanded panel; state=${c.state.value.session?.elapsedMs}",longElapsed)
            assertEquals(large,panel())
            device.findObject(By.desc(shrink)).click()
            val compact=device.wait(Until.findObject(By.desc(expand)),15_000)!!
            val original=compact.visibleBounds
            assertNotNull("Long elapsed time in compact panel",device.wait(Until.findObject(By.text("99:59:59")),15_000))
            screenshot("$prefix-small-long",original)
            val center=compact.visibleCenter
            device.swipe(center.x,center.y,device.displayWidth-20,device.displayHeight-45,30)
            assertFalse("Dragging must not expand the panel",device.hasObject(By.desc(activity.getString(R.string.open_app))))
            val moved=device.wait(Until.findObject(By.desc(expand)),15_000)!!.visibleBounds
            // Accessibility bounds can round a translated window by one physical pixel.
            assertTrue(kotlin.math.abs(original.width()-moved.width())<=1)
            assertTrue(kotlin.math.abs(original.height()-moved.height())<=1)
            assertTrue(moved.right<=device.displayWidth && moved.bottom<=device.displayHeight)
            device.findObject(By.desc(expand)).click()
            val anchored=panel()
            assertTrue("The right edge stays anchored on expansion",kotlin.math.abs(moved.right-anchored.right)<=2)
            assertTrue("The bottom edge stays anchored on expansion",kotlin.math.abs(moved.bottom-anchored.bottom)<=2)
            screenshot("$prefix-corner")
            device.findObject(By.desc(activity.getString(R.string.open_app))).click()
            awaitState { foreground()?.hasWindowFocus()==true }
            assertEquals(id,c.state.value.session!!.id); assertTrue(c.state.value.paused)
            assertEquals(listOf(MetricId.TIME,MetricId.CALORIES),c.display.selections.value.getValue(DisplayScope.COMPACT))
        } finally {
            launch(); awaitState { foreground()!=null }
            instrumentation.runOnMainSync { c.finish() }
            awaitState { c.state.value.session==null }
            val current=foreground(); instrumentation.runOnMainSync { current?.finish() }
        }
    }
}
