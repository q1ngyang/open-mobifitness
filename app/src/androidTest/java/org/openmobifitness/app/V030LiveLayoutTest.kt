package org.openmobifitness.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.openmobifitness.app.data.saveUser
import org.openmobifitness.core.*
import java.io.File
import java.util.UUID

/** Dedicated emulator: checks actual text layout, navigation order and selected-page behavior. */
class V030LiveLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun readingsFitAndPagingFollowsTheReadingOrder() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val c=(compose.activity.application as OpenMobiApp).controller
        val name=InstrumentationRegistry.getArguments().getString("case","tablet")
        val dir=File(compose.activity.getExternalFilesDir(null),"live-refinement/$name").apply { mkdirs() }
        val ids=listOf(MetricId.CADENCE,MetricId.HEART,MetricId.DISTANCE,MetricId.CALORIES,MetricId.POWER,MetricId.SPEED)
        fun text(id: Int)=compose.activity.getString(id)
        fun screen(label: String) {
            compose.waitForIdle()
            val device=UiDevice.getInstance(inst)
            if(device.hasObject(By.res("android:id/aerr_wait"))) { device.findObject(By.res("android:id/aerr_wait")).click(); Thread.sleep(500) }
            // SurfaceFlinger can briefly return null after an emulator display resize.
            val bitmap=(0 until 3).firstNotNullOfOrNull {
                inst.uiAutomation.takeScreenshot().also { if(it==null) Thread.sleep(500) }
            } ?: android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p")).use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: error("The emulator did not provide a screenshot after its display resize")
            File(dir,"$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        fun reveal(node: SemanticsNodeInteraction): SemanticsNodeInteraction { if(runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo();return node }
        fun checkText() {
            val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true).fetchSemanticsNodes().filter { node ->
                (node.config.contains(SemanticsProperties.TestTag) && node.config[SemanticsProperties.TestTag] in setOf("automatic-mode-label","stage-preview-target")) || generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag].startsWith("live-metric-") }
            }
            assertTrue("Live metric text is present",nodes.isNotEmpty())
            compose.runOnUiThread {
                for(node in nodes) {
                    val layouts=mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    // Paragraph width is fractional, while the measured node is rounded to pixels.
                    val fits=layouts.isNotEmpty() && layouts.all { layout ->
                        !layout.multiParagraph.didExceedMaxLines && (0 until layout.lineCount).all { line ->
                            !layout.isLineEllipsized(line) && layout.getLineRight(line)<=layout.size.width+1f && layout.getLineBottom(line)<=layout.size.height+1f
                        }
                    }
                    assertTrue("Metric text fits its measured cell: ${layouts.map { "${it.layoutInput.text.text}: size=${it.size}, constraints=${it.layoutInput.constraints}, height=${it.multiParagraph.height}" }}",fits)
                }
            }
        }
        Configurator.getInstance().waitForIdleTimeout=100
        listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.POST_NOTIFICATIONS).forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) } }
        compose.waitUntil(90000) { c.state.value.ready }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            val id=UUID.nameUUIDFromBytes("v030-ui-a".toByteArray()).toString()
            if(c.repo.users.value.none { it.id==id }) c.repo.saveUser(UserProfile(id,"小林",weightKg=65.0))
            c.switchUser(id).join()
        }
        compose.runOnUiThread {
            c.dismissResult(); c.cancelStart(); c.userPicker.value=false; c.setIdentityPolicy(IdentityPolicy.REMEMBER)
            c.setDemo(true); c.setTheme(if(name.contains("dark")) "dark" else "light"); c.display.floating(true)
            c.display.save(DisplayScope.TRAINING,ids,Machine.ELLIPTICAL)
            c.local.savePresets("demo.ELLIPTICAL",Machine.ELLIPTICAL,ResistanceRange(1.0,24.0),listOf(4.0,8.0,12.0,18.0))
            c.select(null); compose.activity.startTraining()
        }
        try {
            compose.waitUntil(60000) { c.state.value.session!=null }
            screen("free");checkText()
            runBlocking { c.finish().join() }
            compose.runOnUiThread { c.dismissResult(); c.select(WorkoutPolicy.templates(Machine.ELLIPTICAL).first { it.id=="steady20_elliptical" }); compose.activity.startTraining() }
            compose.waitUntil(60000) { c.state.value.session!=null };compose.waitForIdle()
            screen("training")
            reveal(compose.onNodeWithTag("metric-page-count")).assertIsDisplayed()
            val previous=compose.onNodeWithContentDescription(text(R.string.previous_metrics))
            val next=compose.onNodeWithContentDescription(text(R.string.next_metrics))
            val page=compose.onNodeWithTag("metric-page-count")
            val before=previous.fetchSemanticsNode().boundsInRoot
            val center=page.fetchSemanticsNode().boundsInRoot
            val after=next.fetchSemanticsNode().boundsInRoot
            assertTrue("Page number sits between previous and next",before.right<=center.left+1 && center.right<=after.left+1)
            checkText();screen("metrics")
            val cfg=compose.activity.resources.configuration
            fun visibleAboveDock(tag: String) {
                val bounds=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                val dock=compose.onNodeWithTag("workout-dock").getUnclippedBoundsInRoot()
                assertTrue("$tag is fully above the fixed action dock",bounds.bottom<=dock.top)
            }
            if(cfg.screenWidthDp in 760..839 && cfg.screenHeightDp>=1150 && cfg.fontScale<=1.1f) {
                ids.forEach { visibleAboveDock("live-metric-${it.name}") }
                visibleAboveDock("preset-18.0")
            }
            if(cfg.screenWidthDp in 700..759 && cfg.screenHeightDp in 700..900 && cfg.fontScale<=1.1f) {
                ids.take(4).forEach { visibleAboveDock("live-metric-${it.name}") }
                visibleAboveDock("preset-18.0")
            }
            if(cfg.screenWidthDp>=1200 && cfg.fontScale<=1.1f) {
                ids.forEach { compose.onNodeWithTag("live-metric-${it.name}").assertIsDisplayed() }
                page.assertTextEquals("1 / 1");previous.assertIsNotEnabled();next.assertIsNotEnabled()
                compose.onNodeWithTag("metric-page-indicators").assertIsDisplayed()
                compose.onNodeWithTag("preset-18.0").assertIsDisplayed()
            }
            compose.runOnUiThread { c.display.save(DisplayScope.TRAINING,ids+MetricId.RESISTANCE,Machine.ELLIPTICAL) };compose.waitForIdle()
            reveal(next).assertIsEnabled().performClick();compose.waitForIdle()
            page.assertTextContains("2 /",substring=true);previous.assertIsEnabled().performClick();compose.waitForIdle();page.assertTextContains("1 /",substring=true)
            compose.runOnUiThread { c.display.save(DisplayScope.TRAINING,ids,Machine.ELLIPTICAL) };compose.waitForIdle()
            reveal(compose.onNodeWithTag("preset-18.0")).assertIsDisplayed()
            val levels=listOf(4.0,8.0,12.0,18.0).map { compose.onNodeWithTag("preset-$it").fetchSemanticsNode().boundsInRoot }
            assertTrue("All presets share a single row",levels.all { kotlin.math.abs(it.top-levels.first().top)<1f })
            screen("controls")
        } finally { runBlocking { if(c.state.value.session!=null) c.finish().join() } }
    }
}
