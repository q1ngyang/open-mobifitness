package org.openmobifitness.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Configurator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.io.File

/** Real Android interactions with Debug telemetry. No radio/hardware claims. */
class V020ExperienceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val args get()=InstrumentationRegistry.getArguments()
    private fun shot(scene: String) {
        if(scene.contains("editor")) Thread.sleep(800) // Include settled native IME insets.
        compose.waitForIdle()
        Thread.sleep(400) // Let navigation fades reach the actual Surface frame.
        val config=compose.activity.resources.configuration
        val prefix=args.getString("layout") ?: "v020-phone"
        val dir=File(compose.activity.getExternalFilesDir(null),"v020-qa/$prefix").apply { mkdirs() }
        val image=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(dir,"$scene.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        File(dir,"environment.txt").writeText("width_dp=${config.screenWidthDp}\nheight_dp=${config.screenHeightDp}\nfont=${config.fontScale}\nlocale=${config.locales.toLanguageTags()}\napi=${android.os.Build.VERSION.SDK_INT}\ntheme=${c.theme.value}\ntelemetry=Debug simulation, no hardware\n")
    }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout=100
        compose.waitUntil(30000) { c.state.value.ready }
        compose.runOnUiThread {
            c.dismissResult(); c.error(null)
            if(c.state.value.session==null) { c.exitControl(); c.setDemo(true) }
            c.setTheme(args.getString("theme") ?: "light")
            c.display.floating(true)
            c.local.remember(SavedDevice("00:00:00:00:02:20","MB-EP · UI fixture",Machine.ELLIPTICAL))
            c.prefs.edit().apply { DisplayScope.entries.forEach { scope -> remove(scope.key); Machine.entries.forEach { m -> remove("${scope.key}.${m.name}") } } }.commit()
            c.display.reload()
            compose.activity.page.value=0; compose.activity.focusTraining.value=false
            args.getString("language")?.let { if(compose.activity.currentLanguage()!=it) compose.activity.setLanguage(it) }
        }
        compose.waitForIdle()
    }
    @After fun cleanup() {
        compose.runOnUiThread { if(c.state.value.session!=null) c.finish() }
        compose.waitUntil(30000) { c.state.value.session==null }
        compose.runOnUiThread { c.dismissResult(); c.exitControl() }
    }
    @Test fun controlRecordingPresetsAndSettingsStayReachable() {
        shot("01-home")
        compose.onNodeWithTag("hero-control").performScrollTo().performClick()
        compose.waitUntil(30000) { c.state.value.controlOnly }
        assertNull(c.state.value.session)
        shot("02-control")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().performClick()
        compose.waitUntil(10000) { c.controlResistance()==2.0 }
        compose.onNodeWithTag("edit-presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset-input").performTextReplacement("4, 8, 12, 16")
        shot("03-preset-editor-keyboard")
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        assertEquals(2.0,c.controlResistance()!!,0.0)
        compose.onNodeWithTag("preset-12.0").performScrollTo().performClick()
        compose.waitUntil(10000) { c.controlResistance()==12.0 }
        shot("04-controls-presets")
        val floating=compose.onNodeWithTag("float").fetchSemanticsNode().boundsInRoot
        val primary=compose.onNodeWithTag("pause").fetchSemanticsNode().boundsInRoot
        assertTrue("Floating stays on the right in control mode",floating.left>primary.left)
        compose.onNodeWithTag("pause").performClick()
        compose.waitUntil(30000) { c.state.value.session!=null }
        assertEquals(12.0,c.controlResistance()!!,0.0)
        val id=c.state.value.session!!.id
        compose.waitUntil(30000) { c.state.value.session!!.elapsedMs>=1000 }
        compose.onNodeWithTag("pause").performClick()
        compose.waitUntil(10000) { c.state.value.paused }
        val elapsed=c.state.value.session!!.elapsedMs
        Thread.sleep(1500)
        assertEquals(elapsed,c.state.value.session!!.elapsedMs)
        shot("05-record-paused")
        assertTrue("Floating stays on the right while recording",compose.onNodeWithTag("float").fetchSemanticsNode().boundsInRoot.left>compose.onNodeWithTag("finish").fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithTag("finish").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.finish_save),useUnmergedTree=true).onLast().performClick()
        compose.waitUntil(30000) { c.state.value.session==null }
        assertFalse(c.state.value.inUse)
        compose.runOnUiThread { c.dismissResult(); compose.activity.focusTraining.value=false; compose.activity.page.value=2 }
        shot("06-devices")
        compose.runOnUiThread { compose.activity.page.value=3; compose.activity.settingsSection.value="personal" }
        shot("07-personal-hints")
        compose.onAllNodesWithText(compose.activity.getString(R.string.edit)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("hint-lower").performTextReplacement("50")
        compose.onNodeWithTag("hint-upper").performTextReplacement("80")
        shot("08-hint-editor-keyboard")
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.runOnUiThread { compose.activity.settingsSection.value="floating" }
        compose.onNodeWithTag("reset-position").performScrollTo().performClick()
        shot("09-floating-settings")
        compose.runOnUiThread { compose.activity.settingsSection.value="data" }
        shot("10-backup")
        compose.onNodeWithTag("export-backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("import-backup").performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.metricScope.value=DisplayScope.TRAINING }
        shot("11-metric-picker")
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        runBlocking { assertNotNull(c.repo.history.session(id)); c.repo.deleteSession(id) }
    }
    /** Host drives the documented dimensions, themes and fonts; this shorter case
     * verifies each changed surface and the fixed actions in every configuration. */
    @Test fun visualMatrix() {
        shot("01-home")
        compose.onNodeWithTag("hero-control").performScrollTo().performClick()
        compose.waitUntil(30000) { c.state.value.controlOnly }
        shot("02-control")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().performClick()
        compose.waitUntil(10000) { c.controlResistance()==2.0 }
        compose.onNodeWithTag("edit-presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset-input").performTextReplacement("4, 8, 12, 16")
        shot("03-preset-editor")
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.onNodeWithTag("preset-16.0").performScrollTo().performClick()
        compose.waitUntil(10000) { c.controlResistance()==16.0 }
        shot("04-resistance")
        compose.onNodeWithTag("pause").assertIsDisplayed().performClick()
        compose.waitUntil(30000) { c.state.value.session!=null }
        val id=c.state.value.session!!.id
        compose.runOnUiThread { c.state.value=c.state.value.copy(paused=true,session=c.state.value.session!!.copy(elapsedMs=359999000,distanceM=9999999.0,caloriesKcal=99999.0)) }
        shot("05-record-long-values")
        val floating=compose.onNodeWithTag("float").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(floating.left>compose.onNodeWithTag("finish").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithTag("finish").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.finish_save),useUnmergedTree=true).onLast().performClick()
        compose.waitUntil(30000) { c.state.value.session==null }
        compose.runOnUiThread { c.dismissResult(); c.exitControl(); compose.activity.focusTraining.value=false; compose.activity.page.value=0 }
        runBlocking { c.repo.history.session(id)?.let { c.repo.save(it.copy(demo=false,elapsedMs=359999000,distanceM=9999999.0,caloriesKcal=99999.0)) } }
        compose.waitForIdle()
        shot("06-home-long-values")
        compose.runOnUiThread { compose.activity.page.value=2 }
        shot("07-devices")
        compose.runOnUiThread { compose.activity.page.value=3; compose.activity.settingsSection.value="personal" }
        shot("08-personal-hints")
        compose.onAllNodesWithText(compose.activity.getString(R.string.edit)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("hint-lower").performTextReplacement("50")
        shot("09-hint-editor")
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnUiThread { compose.activity.settingsSection.value="floating" }
        compose.onNodeWithTag("reset-position").performScrollTo().performClick()
        shot("10-floating-settings")
        compose.runOnUiThread { compose.activity.settingsSection.value="data" }
        shot("11-backup")
        compose.onNodeWithTag("import-backup").performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.metricScope.value=DisplayScope.TRAINING }
        shot("12-metric-picker")
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        runBlocking { c.repo.deleteSession(id) }
    }
    @Test fun customMetricsPagesSurviveStartingAndFinishingARecord() {
        val selected=MetricCatalog.forMachine(Machine.ROWER).take(12)
        compose.runOnUiThread {
            c.setDemo(true,Machine.ROWER)
            c.display.save(DisplayScope.TRAINING,selected,Machine.ROWER)
            compose.activity.startControl()
        }
        compose.waitUntil(30000) { c.state.value.controlOnly }
        compose.onNodeWithText("1 / 3").performScrollTo().assertIsDisplayed()
        shot("custom-01-page-one")
        val next=compose.activity.getString(R.string.next_metrics)
        val previous=compose.activity.getString(R.string.previous_metrics)
        compose.onNodeWithContentDescription(next).performScrollTo().performClick()
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        shot("custom-02-page-two")
        compose.onNodeWithContentDescription(next).performClick()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription(next).assertIsNotEnabled()
        shot("custom-03-page-three")
        compose.onNodeWithContentDescription(previous).performClick()
        compose.onNodeWithTag("pause").performClick()
        compose.waitUntil(30000) { c.state.value.session!=null }
        val id=c.state.value.session!!.id
        assertEquals(selected,c.display.selected(DisplayScope.TRAINING,Machine.ROWER,freeRecording=true))
        compose.onNodeWithText("2 / 3").performScrollTo().assertIsDisplayed()
        shot("custom-04-recording-page-two")
        compose.onNodeWithTag("finish").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.finish_save),useUnmergedTree=true).onLast().performClick()
        compose.waitUntil(30000) { c.state.value.session==null }
        assertFalse(c.state.value.inUse)
        assertEquals(selected,c.display.selected(DisplayScope.TRAINING,Machine.ROWER,true))
        runBlocking { c.repo.deleteSession(id) }
    }
    @Test fun largeFontRecordingDocksKeepActionsReachable() {
        val ids=mutableListOf<String>()
        try {
            for(plan in listOf(false,true)) {
                compose.runOnUiThread {
                    c.select(if(plan) Presets.all.first { it.id=="hiit40" } else null)
                    compose.activity.page.value=0
                    compose.activity.startTraining()
                }
                compose.waitUntil(30000) { c.state.value.session!=null }
                ids+=c.state.value.session!!.id
                val name=if(plan) "plan" else "free"
                val finish=compose.onNodeWithTag("finish").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val floating=compose.onNodeWithTag("float").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(floating.left>finish.left)
                val config=compose.activity.resources.configuration
                if(compose.activity.currentLanguage()=="de" && config.fontScale>=1.2f && (config.screenHeightDp<480 || config.screenWidthDp<600)) {
                    compose.onNodeWithText(compose.activity.getString(R.string.finish_compact)).assertIsDisplayed()
                    assertTrue("Compact German label must not become a four-line dock",finish.height/compose.activity.resources.displayMetrics.density<=96f)
                }
                shot("dock-$name-recording")
                assertFalse("A delayed service shutdown must not pause a newly started record",c.state.value.paused)
                compose.onNodeWithTag("pause").performClick()
                compose.waitUntil(10000) { c.state.value.paused }
                shot("dock-$name-paused")
                compose.onNodeWithTag("finish").performClick()
                shot("dock-$name-save-confirmation")
                val explanation=compose.onNodeWithText(compose.activity.getString(R.string.finish_note)).performScrollTo().assertIsDisplayed()
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                explanation.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Save explanation must not be clipped; scrollable text retains its full layout",layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
                shot("dock-$name-save-explanation")
                compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
                assertNotNull(c.state.value.session)
                compose.runOnUiThread { c.finish() }
                compose.waitUntil(30000) { c.state.value.session==null }
                compose.runOnUiThread { c.dismissResult(); c.exitControl() }
            }
        } finally {
            // Finish a failed fixture before deleting it. Otherwise the active-row
            // guard masks the original UI failure and loses its useful diagnostic.
            runBlocking { c.finish().join(); ids.forEach { c.repo.deleteSession(it) } }
        }
    }
    @Test fun idleStopQueuedBeforeANewRecordDoesNotStopThatRecord() {
        val ids=mutableListOf<String>()
        try {
            repeat(3) {
                compose.runOnUiThread { compose.activity.startControl() }
                compose.waitUntil(30000) { c.state.value.controlOnly }
                compose.runOnUiThread {
                    compose.activity.exitControl()
                    compose.activity.startTraining()
                }
                compose.waitUntil(30000) { c.state.value.session!=null }
                val id=c.state.value.session!!.id; ids+=id
                compose.waitUntil(15000) { c.state.value.paused || (c.state.value.session?.elapsedMs ?: 0)>=2000 }
                assertEquals(id,c.state.value.session?.id)
                assertFalse(c.state.value.paused)
                assertTrue(c.serviceStarted)
                compose.runOnUiThread { c.finish() }
                compose.waitUntil(30000) { c.state.value.session==null }
                compose.runOnUiThread { c.dismissResult(); compose.activity.exitControl() }
                compose.waitUntil(30000) { !c.serviceStarted }
            }
        } finally { runBlocking { c.finish().join(); ids.forEach { c.repo.deleteSession(it) } } }
    }
    @Test fun settingsAndPlanControlsStayReachable() {
        compose.runOnUiThread { compose.activity.page.value=3; compose.activity.settingsSection.value="" }
        shot("plan-01-settings")
        compose.onNodeWithTag("setting-personal").performScrollTo().assertIsDisplayed()
        shot("plan-02-personal-entry")
        compose.onNodeWithTag("setting-personal").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.personal_hints)).onFirst().assertIsDisplayed()
        compose.runOnUiThread {
            c.select(Presets.all.first { it.id=="hiit40" })
            compose.activity.page.value=0; compose.activity.startTraining()
        }
        compose.waitUntil(30000) { c.state.value.session!=null }
        val id=c.state.value.session!!.id
        try {
            shot("plan-03-recording")
            val floating=compose.onNodeWithTag("float").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(floating.left>compose.onNodeWithTag("finish").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().performClick()
            compose.waitUntil(10000) { !c.state.value.automatic }
            assertNotNull(c.state.value.session)
            shot("plan-04-manual-control")
            compose.onNodeWithTag("edit-presets").performScrollTo().performClick()
            compose.onNodeWithTag("preset-input").performTextReplacement("4, 8, 12, 16")
            shot("plan-05-preset-editor")
            compose.onNodeWithText(compose.activity.getString(R.string.save)).assertIsDisplayed()
            val device=UiDevice.getInstance(instrumentation)
            val save=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(compose.activity.getString(R.string.save))),10000)!!.visibleBounds
            device.click(save.centerX(),save.centerY())
            compose.waitForIdle()
            compose.onNodeWithTag("preset-12.0").performScrollTo().performClick()
            compose.waitUntil(10000) { c.controlResistance()==12.0 }
            assertFalse(c.state.value.automatic)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.stage_overview)).performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.stage_overview)).assertIsDisplayed()
            shot("plan-06-stages")
            compose.onNodeWithText(compose.activity.getString(R.string.close)).performClick()
            compose.onNodeWithTag("pause").assertIsDisplayed().performClick()
            compose.waitUntil(10000) { c.state.value.paused }
            compose.onNodeWithTag("finish").performClick()
            compose.onAllNodesWithText(compose.activity.getString(R.string.finish)).onLast().performClick()
            compose.waitUntil(30000) { c.state.value.session==null }
        } finally {
            compose.runOnUiThread { if(c.state.value.session!=null) c.finish() }
            compose.waitUntil(30000) { c.state.value.session==null }
            compose.runOnUiThread { c.dismissResult() }
            runBlocking { c.repo.deleteSession(id) }
        }
    }
    @Test fun accumulatedSummaryStaysInOneRowAndScrollsWithoutOpeningHistory() {
        fun value(index: Int)=compose.onNodeWithTag("today-value-$index",useUnmergedTree=true)
        fun text(index: Int)=value(index).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        fun assertNumbersFit() {
            repeat(4) { index ->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                value(index).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Numeric text must have a rendered layout",layouts.isNotEmpty())
                assertTrue("Full accumulated value must fit: ${text(index)}",layouts.all { !it.hasVisualOverflow })
            }
        }
        compose.onNodeWithTag("hero-history").performScrollTo()
        val previousCount=text(2)
        assertNumbersFit()
        shot("summary-00-ordinary-values")
        val record=Session(status="completed",demo=false,elapsedMs=59999940000,distanceM=999999990.0,caloriesKcal=9999999.0)
        runBlocking { c.repo.save(record) }
        try {
            compose.waitUntil(30000) { text(2)!=previousCount }
            compose.onNodeWithTag("hero-history").performScrollTo()
            assertNumbersFit()
            val first=compose.onNodeWithTag("today-metric-0",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val last=compose.onNodeWithTag("today-metric-3",useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertEquals("All four metrics retain a single row",first.top.value,last.top.value,1f)
            shot("summary-01-long-values")
            repeat(4) {
                val edge=compose.onNodeWithTag("today-metric-3",useUnmergedTree=true).getUnclippedBoundsInRoot()
                val viewport=compose.onNodeWithTag("hero-history").getUnclippedBoundsInRoot()
                if(edge.right>viewport.right) compose.onNodeWithTag("hero-history").performTouchInput {
                    swipe(androidx.compose.ui.geometry.Offset(right-8,bottom-28),androidx.compose.ui.geometry.Offset(left+8,bottom-28),500)
                }
            }
            compose.waitForIdle()
            assertEquals("Horizontal scrolling must not activate history",0,compose.activity.page.value)
            compose.onNodeWithTag("today-metric-3",useUnmergedTree=true).assertIsDisplayed()
            val visible=compose.onNodeWithTag("today-metric-3",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val card=compose.onNodeWithTag("hero-history").getUnclippedBoundsInRoot()
            assertTrue(visible.right.value<=card.right.value+1 && visible.left>=card.left)
            shot("summary-02-distance-revealed")
        } finally { runBlocking { c.repo.deleteSession(record.id) } }
    }
    @Test fun missingReadOnlyPendingAndUnconfirmedValuesRemainDistinct() {
        fun link(value: org.openmobifitness.app.ble.LinkState) {
            compose.runOnUiThread {
                c.ble.state.value=value
                c.state.value=c.state.value.copy(demo=false,controlOnly=true,metrics=value.metrics)
                compose.activity.focusTraining.value=true
            }
        }
        compose.runOnUiThread { c.setDemo(false) }
        val base=org.openmobifitness.app.ble.LinkState(name="MB-EP · synthetic UI state",phase="ready",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,range=ResistanceRange(1.0,24.0),writable=false)
        link(base)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.control_unavailable)).assertExists()
        shot("state-01-missing-read-only")
        link(base.copy(writable=true,metrics=Metrics(resistance=12.0),requested=16.0))
        compose.onNodeWithText(compose.activity.getString(R.string.control_pending,"16")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.resistance_level,"12")).assertExists()
        shot("state-02-actual-and-pending")
        link(base.copy(writable=true,resistanceFeedback=false,commandedResistance=16.0))
        compose.onNodeWithText(compose.activity.getString(R.string.resistance_commanded)).performScrollTo().assertIsDisplayed()
        shot("state-03-command-without-feedback")
        link(base.copy(phase="disconnected"))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.increase)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("pause").assertIsNotEnabled()
        shot("state-04-disconnected")
        assertNull(c.state.value.session)
    }
    @Test fun editorActionsStayUsableWithTheKeyboardOpen() {
        compose.onNodeWithTag("hero-control").performScrollTo().performClick()
        compose.waitUntil(30000) { c.state.value.controlOnly }
        compose.onNodeWithTag("edit-presets").performScrollTo().performClick()
        compose.onNodeWithTag("preset-input").performTextReplacement("4, 8, 12, 16")
        Thread.sleep(1500) // Native IME/window insets must settle before visual assertions.
        shot("keyboard-01-presets")
        val device=UiDevice.getInstance(instrumentation)
        val save=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(compose.activity.getString(R.string.save))),10000)
        assertNotNull(save); save!!.click()
        compose.waitForIdle()
        compose.onNodeWithTag("preset-input").assertDoesNotExist()
        compose.runOnUiThread { c.exitControl(); compose.activity.focusTraining.value=false; compose.activity.page.value=3; compose.activity.settingsSection.value="personal" }
        compose.onAllNodesWithText(compose.activity.getString(R.string.edit)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("hint-lower").performTextReplacement("50")
        Thread.sleep(1500)
        shot("keyboard-02-hints")
        compose.onNodeWithTag("hint-upper").performScrollTo().performTextReplacement("80")
        Thread.sleep(800)
        shot("keyboard-03-upper-bound")
        val cancel=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(compose.activity.getString(R.string.cancel))),10000)
        assertNotNull(cancel); cancel!!.click()
        compose.waitForIdle()
        compose.onNodeWithTag("hint-lower").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.page.value=2 }
        shot("keyboard-04-devices")
        compose.onNodeWithTag("device-note-00:00:00:00:02:20").performScrollTo().performClick()
        compose.onNodeWithTag("device-note-input").performTextReplacement("Living room · 常用器材")
        Thread.sleep(800)
        shot("keyboard-05-device-note")
        device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(compose.activity.getString(R.string.save))),10000)!!.click()
        compose.waitUntil(10000) { c.local.devices.value.any { it.address=="00:00:00:00:02:20" && it.note=="Living room · 常用器材" } }
        Thread.sleep(800)
        compose.runOnUiThread { compose.activity.metricScope.value=DisplayScope.TRAINING }
        val timeLabel=compose.activity.getString(metricLabel(MetricId.TIME))
        compose.onNodeWithTag("metric-choices").performScrollToNode(hasText(timeLabel))
        compose.onNodeWithText(timeLabel).assertIsDisplayed().performClick()
        shot("keyboard-06-metric-visible")
        device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(compose.activity.getString(R.string.save))),10000)!!.click()
        compose.waitUntil(10000) { MetricId.TIME in c.display.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL) }
    }
    @Test fun backupPreviewSelectionsAndFailureActionsStayReachable() {
        val record=Session(status="completed",demo=true,elapsedMs=9000)
        val file=File(compose.activity.cacheDir,"ui-preview-${record.id}.zip")
        val preferences=org.openmobifitness.app.data.PortablePreferences.export(c.prefs)
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            mapOf("manifest.txt" to "OpenMOBI backup 7\n","sessions/0.csv" to Exchange.sessions(listOf(record)),"samples/0.csv" to Exchange.samples(emptyList()),"workouts/0.csv" to Exchange.workouts(emptyList()),"preferences.json" to preferences).forEach { (name,value) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        try {
            compose.runOnUiThread { compose.activity.page.value=3; compose.activity.settingsSection.value="data"; c.backup.read(android.net.Uri.fromFile(file)) }
            compose.waitUntil(30000) { c.backup.preview.value!=null }
            compose.onNodeWithTag("confirm-restore").assertIsDisplayed()
            shot("backup-01-preview")
            compose.onAllNodesWithText(compose.activity.getString(R.string.backup_devices)).onLast().performScrollTo().performClick()
            compose.onAllNodesWithText(compose.activity.getString(R.string.backup_appearance)).onLast().performScrollTo().performClick()
            shot("backup-02-selected-groups")
            compose.onNodeWithTag("confirm-restore").performClick()
            compose.waitUntil(30000) { c.backup.status.value?.phase==org.openmobifitness.app.data.TransferPhase.COMPLETE }
            compose.onNodeWithText(compose.activity.getString(R.string.backup_complete)).assertIsDisplayed()
            shot("backup-03-complete")
            compose.onNodeWithText(compose.activity.getString(R.string.close)).performClick()
            for(error in listOf("space","capacity")) {
                compose.runOnUiThread { c.backup.status.value=org.openmobifitness.app.data.TransferStatus(org.openmobifitness.app.data.TransferPhase.FAILED,error=error) }
                compose.onNodeWithText(compose.activity.getString(R.string.close)).assertIsDisplayed()
                shot("backup-04-error-$error")
                compose.onNodeWithText(compose.activity.getString(R.string.close)).performClick()
            }
        } finally {
            compose.runOnUiThread { c.backup.discard(); c.backup.dismiss() }
            runBlocking { c.repo.deleteSession(record.id) }; file.delete()
        }
    }
    @Test fun personalHintValidationAndMuteNeverChangeResistance() {
        val original=c.local.hints.value
        try {
            compose.runOnUiThread {
                c.local.saveHints(original.copy(frequency=original.frequency+(Machine.ELLIPTICAL to PersonalRange())))
                compose.activity.page.value=3; compose.activity.settingsSection.value="personal"
            }
            compose.onAllNodesWithText(compose.activity.getString(R.string.edit)).onFirst().performScrollTo().performClick()
            compose.onNodeWithTag("hint-lower").performTextReplacement("50")
            compose.onNodeWithTag("hint-upper").performScrollTo().performTextReplacement("40")
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.hint_enable)).performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.invalid_hint_bounds)).performScrollTo().assertIsDisplayed()
            shot("hint-01-invalid-bounds")
            compose.onNodeWithTag("hint-upper").performScrollTo().performTextReplacement("80")
            compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
            assertTrue(c.local.hints.value.frequency.getValue(Machine.ELLIPTICAL).enabled)
            compose.runOnUiThread { compose.activity.page.value=0; compose.activity.startControl() }
            compose.waitUntil(30000) { c.state.value.controlOnly }
            val resistance=c.controlResistance()
            compose.onNodeWithTag("mute-hints").performScrollTo().assertIsDisplayed()
            shot("hint-02-enabled")
            compose.onNodeWithTag("mute-hints").performClick()
            assertTrue(c.state.value.hintsMuted); assertEquals(resistance,c.controlResistance()); assertNull(c.state.value.session)
            shot("hint-03-muted")
            compose.onNodeWithTag("mute-hints").performClick()
            assertFalse(c.state.value.hintsMuted); assertEquals(resistance,c.controlResistance())
            shot("hint-04-unmuted")
        } finally { compose.runOnUiThread { c.local.saveHints(original) } }
    }
}
