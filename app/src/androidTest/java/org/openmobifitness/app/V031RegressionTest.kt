package org.openmobifitness.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.time.Instant
import java.util.UUID

/** Dedicated emulator only: real dock actions and native chart pixels with synthetic samples. */
class V031RegressionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val layout get()=InstrumentationRegistry.getArguments().getString("case","phone")
    private val userId=UUID.nameUUIDFromBytes("v031-ui-user".toByteArray()).toString()
    private val reportId=UUID.nameUUIDFromBytes("v031-power-report".toByteArray()).toString()
    private val dir get()=File(compose.activity.getExternalFilesDir(null),"v031-screens/$layout").apply { mkdirs() }
    private fun text(id: Int)=compose.activity.getString(id)
    private fun records()=runBlocking { c.repo.history.reportIds(HistoryQuery(owner=userId)).toSet() }
    private fun clearSystemUiWait() {
        val device=UiDevice.getInstance(inst)
        if(device.hasObject(By.textContains("System UI")) && device.hasObject(By.res("android:id/aerr_wait"))) {
            device.findObject(By.res("android:id/aerr_wait")).click(); Thread.sleep(500)
        }
        assertFalse("System or application error obscures the UI",device.hasObject(By.res("android:id/aerr_wait")))
    }
    private fun screen(name: String) {
        compose.waitForIdle()
        clearSystemUiWait()
        val bitmap=(0 until 3).firstNotNullOfOrNull { inst.uiAutomation.takeScreenshot().also { if(it==null) Thread.sleep(300) } }
            ?: error("Native screenshot unavailable")
        File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout=100
        clearSystemUiWait()
        listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.POST_NOTIFICATIONS).forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) } }
        compose.waitUntil(90000) { c.state.value.ready }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            if(c.repo.users.value.none { it.id==userId }) c.repo.saveUser(UserProfile(userId,"小林 · 复核",weightKg=65.0))
            c.switchUser(userId).join()
        }
        compose.runOnUiThread {
            c.cancelStart(); c.userPicker.value=false; c.dismissResult(); c.exitControl()
            c.setIdentityPolicy(IdentityPolicy.REMEMBER); c.setDemo(true); c.select(null)
            c.setTheme(if(layout.contains("dark")) "dark" else "light"); c.display.floating(true)
            c.historyScope.value="current"; c.historyRequest.value=null
            compose.activity.focusTraining.value=false; compose.activity.page.value=0
        }
        compose.waitForIdle()
    }
    @After fun cleanup() {
        runBlocking { if(c.state.value.session!=null) c.finish().join(); c.repo.deleteSession(reportId) }
        compose.runOnUiThread { c.cancelStart(); c.userPicker.value=false; c.dismissResult(); compose.activity.exitControl() }
    }
    @Test fun exitIsVisibleAndWorksWithoutOpeningTheMenu() {
        val before=records()
        for(floating in listOf(true,false)) {
            compose.runOnUiThread { c.display.floating(floating); compose.activity.startControl() }
            compose.waitUntil(30000) { c.state.value.controlOnly && !c.state.value.starting }
            val exit=compose.onNodeWithTag("finish").assertIsDisplayed().assertIsEnabled()
            exit.assertTextContains(text(R.string.exit_control))
            exit.assertContentDescriptionEquals(text(R.string.disconnect_exit))
            val bounds=exit.getUnclippedBoundsInRoot()
            val root=compose.onRoot().getUnclippedBoundsInRoot()
            assertTrue("Exit is fully inside the window",bounds.top>=root.top && bounds.bottom<=root.bottom && bounds.left>=root.left && bounds.right<=root.right)
            val primary=compose.onNodeWithTag("pause").getUnclippedBoundsInRoot()
            assertTrue("Exit follows the primary action",primary.right<=bounds.left || primary.bottom<=bounds.top)
            if(floating) assertTrue(bounds.right<=compose.onNodeWithTag("float").getUnclippedBoundsInRoot().left)
            else compose.onNodeWithTag("float").assertDoesNotExist()
            screen(if(floating) "control-exit" else "control-exit-no-overlay")
            val dockText=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true).fetchSemanticsNodes().filter { node ->
                generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag]=="workout-dock" }
            }
            compose.runOnUiThread { dockText.forEach { node ->
                val results=mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
                val diagnostic=results.joinToString { result -> "${result.layoutInput.text}: ${result.size}, lines="+(0 until result.lineCount).map { "${result.getLineRight(it)},${result.getLineBottom(it)},${result.isLineEllipsized(it)}" } }
                // Centered text can retain a paragraph alignment offset after intrinsic measurement.
                assertTrue("Action text remains readable: $diagnostic",results.isNotEmpty() && results.all { result -> !result.multiParagraph.didExceedMaxLines && (0 until result.lineCount).all { !result.isLineEllipsized(it) && result.getLineRight(it)-result.getLineLeft(it)<=result.size.width+1 && result.getLineBottom(it)<=result.size.height+1 } })
                if(floating && layout.contains("font2")) assertTrue("Large-font actions avoid splitting words",results.all { it.lineCount==1 })
            } }
            exit.performClick()
            compose.waitUntil(30000) { !c.state.value.inUse && !c.serviceStarted }
            assertFalse(compose.activity.focusTraining.value); assertEquals(0,compose.activity.page.value)
            assertEquals("disconnected",c.ble.state.value.phase); assertEquals("disconnected",c.heart.state.value.phase)
            assertEquals("Exiting control does not create history",before,records())
        }
        val cfg=compose.activity.resources.configuration
        if(cfg.fontScale>=1.5f) {
            compose.runOnUiThread { c.display.floating(true); compose.activity.startControl() }
            compose.waitUntil(30000) { c.state.value.controlOnly && !c.state.value.starting }
            compose.onNodeWithTag("pause").performClick()
            compose.waitUntil(30000) { c.state.value.session!=null && !c.state.value.starting }
            val recordedId=c.state.value.session!!.id
            compose.onNodeWithTag("finish").assertIsDisplayed().assertIsEnabled()
            screen("recording-dock")
            compose.onNodeWithTag("pause").performClick()
            compose.waitUntil(30000) { c.state.value.paused }
            screen("paused-recording-dock")
            runBlocking { c.finish().join(); c.repo.deleteSession(recordedId) }
            compose.runOnUiThread { c.dismissResult(); compose.activity.exitControl() }
        }
        if(cfg.screenWidthDp>=1200 && cfg.fontScale<=1.1f) {
            compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-hiit_elliptical"))
            val cards=listOf("hiit","hiit30","hiit40").map { compose.onNodeWithTag("plan-${it}_elliptical").assertIsDisplayed().getUnclippedBoundsInRoot() }
            assertTrue("HIIT progression occupies consecutive cards",cards.zipWithNext().all { (a,b) -> a.top==b.top && a.right<=b.left })
            screen("hiit-progression")
        }
    }
    @Test fun powerDefaultsToALineWithGapsAndSelectableMeasurements() {
        val start=Instant.now().minusSeconds(61)
        val session=Session(id=reportId,ownerUserId=userId,startedUserId=userId,startedUserName="小林 · 复核",identityVersion=1,start=start.toString(),end=start.plusSeconds(60).toString(),device="v0.3.1 UI fixture",machine=Machine.ELLIPTICAL,protocol=Protocol.DEMO,demo=true,status="completed",elapsedMs=60000,caloriesKcal=6.0,distanceM=90.0)
        val samples=(0..60).map { second -> Sample(reportId,second*1000L,Metrics(cadence=60.0,powerW=when(second) { in 0..10 -> 40.0+4*second; in 21..40 -> 100.0-2*(second-21); in 51..60 -> 0.0; else -> null })) }
        runBlocking {
            c.repo.deleteSession(reportId)
            withContext(Dispatchers.IO) { c.repo.db.runInTransaction { c.repo.db.records().session(session.row()); c.repo.db.records().samples(samples.map(Sample::row)) }; c.repo.refresh() }
        }
        compose.runOnUiThread { compose.activity.page.value=1 }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("record-$reportId"))
        compose.onNodeWithTag("record-$reportId").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail-scroll").performScrollToNode(hasTestTag("detail-chart"))
        compose.onAllNodesWithText(text(R.string.power)).filter(isSelectable()).onFirst().assertIsSelected()
        val chart=compose.onNodeWithTag("detail-chart").assertIsDisplayed()
        screen("power-line")
        val image=chart.captureToImage().asAndroidBitmap()
        File(dir,"power-plot.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val density=compose.activity.resources.displayMetrics.density
        val left=34*density; val bottom=image.height-24*density
        fun x(seconds: Int)=(left+(image.width-left)*seconds/60).toInt()
        fun ink(from: Int,to: Int): Int {
            var count=0
            for(px in x(from)..x(to)) for(y in (8*density).toInt()..bottom.toInt()+2) {
                val color=image.getPixel(px,y)
                if(android.graphics.Color.blue(color)>android.graphics.Color.red(color)+30 && android.graphics.Color.blue(color)>android.graphics.Color.green(color)+25) count++
            }
            return count
        }
        val strokePixels=ink(3,8).toDouble()/(x(8)-x(3)+1)
        assertTrue("Power is a thin continuous line, not filled vertical bars: $strokePixels",strokePixels>=.6*density && strokePixels<=8.0*density)
        assertEquals("No line connects missing power intervals",0,ink(14,18))
        assertTrue("Measured zero stays on the plot",ink(54,58)>0)
        chart.performTouchInput { click(androidx.compose.ui.geometry.Offset(left+(width-left)*5/60,height/2f)) }
        compose.onNodeWithTag("detail-chart-readout").assertTextContains("00:05",substring=true).assertTextContains("W",substring=true)
        screen("power-point")
        // A chart card can exceed the short viewport; align the child, not its whole lazy item.
        val report=compose.onNodeWithTag("detail-scroll")
        fun reveal(node: SemanticsNodeInteraction) {
            val viewport=report.getUnclippedBoundsInRoot(); val bounds=node.getUnclippedBoundsInRoot()
            val delta=when { bounds.top<viewport.top -> (bounds.top-viewport.top).value; bounds.bottom>viewport.bottom -> (bounds.bottom-viewport.bottom).value; else -> 0f }
            if(delta!=0f) report.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta*density) }
            compose.waitForIdle()
        }
        val heart=compose.onAllNodesWithText(text(R.string.heart_rate)).filter(isSelectable()).onFirst()
        reveal(heart); heart.performScrollTo()
        compose.onAllNodesWithText(text(R.string.heart_rate)).filter(isSelectable()).onFirst().assertIsDisplayed().performClick()
        reveal(compose.onNodeWithText(text(R.string.no_samples)))
        compose.onNodeWithText(text(R.string.no_samples)).assertIsDisplayed()
        val power=compose.onAllNodesWithText(text(R.string.power)).filter(isSelectable()).onFirst()
        reveal(power); power.performScrollTo().assertIsDisplayed().performClick()
        reveal(chart)
        chart.assertIsDisplayed()
    }
}
