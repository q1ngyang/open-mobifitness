package org.openmobifitness.app

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.time.Instant
import java.util.UUID

/** Dedicated emulator: actual report layouts and dismissal paths, with synthetic equipment data. */
class V032ReportTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val args get()=InstrumentationRegistry.getArguments()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val case get()=args.getString("case","phone390")
    private val userId=UUID.nameUUIDFromBytes("v032-report-user".toByteArray()).toString()
    private val owned=mutableSetOf<String>()
    private val dir get()=File(compose.activity.getExternalFilesDir(null),"v032-screens/$case").apply { mkdirs() }
    private fun text(id: Int)=compose.activity.getString(id)
    private fun screen(name: String) {
        compose.waitForIdle()
        val device=UiDevice.getInstance(inst)
        if(device.hasObject(By.textContains("System UI")) && device.hasObject(By.res("android:id/aerr_wait"))) {
            device.findObject(By.res("android:id/aerr_wait")).click(); Thread.sleep(500)
        }
        assertFalse("An error obscures the application",device.hasObject(By.res("android:id/aerr_wait")))
        val bitmap=(0 until 3).firstNotNullOfOrNull { inst.uiAutomation.takeScreenshot().also { if(it==null) Thread.sleep(300) } } ?: error("Screenshot unavailable")
        File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout=100
        listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.POST_NOTIFICATIONS).forEach {
            runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) }
        }
        compose.waitUntil(90000) { c.state.value.ready }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            if(c.repo.users.value.none { it.id==userId }) c.repo.saveUser(UserProfile(userId,"小林 · 报告",weightKg=65.0))
            c.switchUser(userId).join()
        }
        compose.runOnUiThread {
            c.cancelStart(); c.userPicker.value=false; c.dismissResult(); c.exitControl()
            c.setIdentityPolicy(IdentityPolicy.REMEMBER); c.setDemo(true); c.select(null)
            c.setTheme(if(case.contains("dark")) "dark" else "light")
            c.setImperial(args.getString("imperial","false")=="true")
            c.historyScope.value="current"; c.historyRequest.value=null
            compose.activity.page.value=0; compose.activity.focusTraining.value=false
        }
        compose.waitForIdle()
    }
    @After fun cleanup() {
        runBlocking { if(c.state.value.session!=null) c.finish().join(); owned.forEach { c.repo.deleteSession(it) } }
        compose.runOnUiThread { c.dismissResult(); c.cancelStart(); c.userPicker.value=false; compose.activity.exitControl() }
    }
    private fun openHistory(id: String) {
        compose.runOnUiThread { c.historyRequest.value=HistoryQuery(owner=userId); compose.activity.page.value=1 }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("record-$id"))
        compose.onNodeWithTag("record-$id").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun reveal(tag: String,top: Boolean=false) {
        val scroll=compose.onNodeWithTag("detail-scroll")
        scroll.performScrollToNode(hasTestTag(tag))
        val viewport=scroll.getUnclippedBoundsInRoot()
        val bounds=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val delta=when { top || bounds.bottom-bounds.top>viewport.bottom-viewport.top || bounds.top<viewport.top -> bounds.top-viewport.top; bounds.bottom>viewport.bottom -> bounds.bottom-viewport.bottom; else -> 0.dp }
        if(delta.value!=0f) scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta.value*compose.activity.resources.displayMetrics.density) }
        compose.waitForIdle()
    }
    @Test fun finishingReturnsHomeThroughCloseBackAndRecreatedReport() {
        var last=""
        for(mode in 0..2) {
            compose.runOnUiThread {
                if(mode==1) { c.select(Workout(title="Report navigation",machine=Machine.ELLIPTICAL,ownerUserId=userId,steps=listOf(Step(target=120.0,resistancePercent=25)))); compose.activity.startTraining() }
                else { c.select(null); compose.activity.startControl() }
            }
            if(mode!=1) {
                compose.waitUntil(30000) { c.state.value.controlOnly && !c.state.value.starting }
                compose.onNodeWithTag("pause").performClick()
            }
            compose.waitUntil(30000) { c.state.value.session!=null && !c.state.value.starting }
            last=c.state.value.session!!.id; owned+=last
            compose.onNodeWithTag("finish").performClick()
            val label=text(if(mode==1) R.string.finish else R.string.finish_save)
            compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
            compose.waitUntil(30000) { c.finishedSession.value==last && !c.state.value.inUse }
            compose.onNodeWithTag("detail-back").assertIsDisplayed()
            if(mode==0) { screen("saved-report"); compose.onNodeWithTag("detail-back").performClick() }
            else {
                if(mode==2) {
                    compose.activityRule.scenario.recreate()
                    compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-back").fetchSemanticsNodes().isNotEmpty() }
                    assertEquals(last,c.finishedSession.value)
                }
                UiDevice.getInstance(inst).pressBack()
            }
            compose.waitUntil(30000) { c.finishedSession.value==null && !compose.activity.focusTraining.value }
            assertEquals(UseMode.IDLE,c.state.value.mode); assertEquals(0,compose.activity.page.value)
            compose.onNodeWithTag("plan-library").assertExists()
            compose.onNodeWithTag("workout-dock").assertDoesNotExist()
            assertNotNull(runBlocking { c.repo.history.session(last) })
        }
        screen("closed-report-home")
        openHistory(last)
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("history-list").assertExists()
        assertEquals("History reports return to history",1,compose.activity.page.value)
        screen("history-back")
    }
    private fun fixture(machine: Machine): String {
        val id=UUID.nameUUIDFromBytes("v032-$machine".toByteArray()).toString(); owned+=id
        // An interrupted emulator run may leave this task's fixture behind.
        runBlocking {
            c.repo.history.session(id)?.let { previous ->
                check(previous.ownerUserId==userId && previous.device=="v0.3.2 UI fixture")
                c.repo.deleteSession(id)
            }
        }
        val start=Instant.now().minusSeconds(120)
        val resistanceMax=when(machine) { Machine.ELLIPTICAL -> 24; Machine.BIKE -> 32; Machine.ROWER -> 16; else -> null }
        val snapshot=if(resistanceMax==null || case.contains("missing-heart")) "" else
            """{"version":1,"machine":"${machine.name}","protocol":"DEMO","model":"fixture","evidence":"recorded","units":{"resistance":"level"},"resistanceMin":1,"resistanceMax":$resistanceMax,"resistanceIncrement":1}"""
        val session=Session(id=id,ownerUserId=userId,startedUserId=userId,startedUserName="小林 · 报告",identityVersion=1,start=start.toString(),end=start.plusSeconds(60).toString(),
            device="v0.3.2 UI fixture",machine=machine,protocol=Protocol.DEMO,demo=true,status="completed",elapsedMs=60000,caloriesKcal=9.0,distanceM=160.0,capabilitySnapshot=snapshot)
        val samples=(0..60).map { second ->
            val phase=(second%3)-1
            Sample(id,second*1000L,Metrics(cadence=56.0+phase*6,resistance=8.0+phase*4,speedMps=if(case.contains("long")) .25+phase*.01 else (9.6+phase*.8)/3.6,
                heartBpm=if(case.contains("missing-heart")) null else 132+phase*10,powerW=136.0+phase*6,strokes=second/2,
                inclinePercent=6.0+phase*2,strideM=.86+phase*.1,forceN=120.0+phase*20,stepRate=160.0+phase*10,stepCount=second*2,
                jumpCount=second*2,continuousJumps=second,jumpInterruptions=2,repetitions=second/2,loadKg=12.5+phase*2.5))
        }
        runBlocking { withContext(Dispatchers.IO) {
            c.repo.db.runInTransaction { c.repo.db.records().session(session.row()); c.repo.db.records().samples(samples.map(Sample::row)) }
            c.repo.refresh()
        } }
        return id
    }
    private fun inspectText() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true).fetchSemanticsNodes().filter { node ->
            generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag]=="report-performance" }
        }
        assertTrue("Performance text is exposed",nodes.isNotEmpty())
        val typography=mutableMapOf<String,Float>()
        compose.runOnUiThread { nodes.forEach { node ->
            val layouts=mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            layouts.forEach { result ->
                // A paragraph may retain its original width after Text shrinks to its glyphs.
                // Check actual line extents, with only a half-pixel allowance for Int rounding.
                val paragraph=result.multiParagraph
                val clippedLine=(0 until result.lineCount).any { result.isLineEllipsized(it) || result.getLineRight(it)-result.getLineLeft(it)>result.size.width+.5f }
                assertFalse("Clipped report text: ${result.layoutInput.text}; size=${result.size}; paragraph=${paragraph.width} x ${paragraph.height}",
                    paragraph.didExceedMaxLines || clippedLine || paragraph.height>result.size.height+.5f)
                assertTrue("Text has a real height",result.size.height>0)
                val tag=generateSequence(node) { it.parent }.mapNotNull { it.config.getOrElse(SemanticsProperties.TestTag) { "" }.takeIf { s -> s.endsWith("-average") || s.endsWith("-maximum") } }.firstOrNull()
                if(tag!=null) {
                    typography[tag]=maxOf(typography[tag] ?: 0f,result.layoutInput.style.fontSize.value)
                    if(tag.endsWith("-average") && result.layoutInput.style.fontSize.value>=26) {
                        val color=result.layoutInput.style.color.toArgb()
                        val expected=if(case.contains("dark")) 0xFFF2F2F4.toInt() else 0xFF192538.toInt()
                        assertEquals("Average uses neutral theme text, not accent blue",expected,color)
                    }
                }
            }
        } }
        typography.filterKeys { it.endsWith("-average") }.forEach { (key,size) ->
            typography[key.removeSuffix("-average")+"-maximum"]?.let { assertTrue("Average retains stronger hierarchy",size>it) }
        }
    }
    @Test fun performanceStaysReadableForCompatibleEquipmentAndThemes() {
        val machines=if(args.getString("allEquipment","false")=="true") listOf(Machine.ELLIPTICAL,Machine.BIKE,Machine.ROWER,Machine.TREADMILL,Machine.JUMP_ROPE,Machine.DUMBBELL) else listOf(Machine.ELLIPTICAL,Machine.ROWER)
        for(machine in machines) {
            val id=fixture(machine)
            openHistory(id)
            reveal("report-performance",true)
            screen("performance-${machine.name.lowercase()}")
            inspectText()
            val series=ReportMetrics.series(machine)
            for(metric in series) {
                val tag="performance-family-${metric.name}"
                reveal(tag)
                compose.onNodeWithTag(tag).assertIsDisplayed()
                val family=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                val card=compose.onNodeWithTag("report-performance").getUnclippedBoundsInRoot()
                assertTrue("Family stays horizontally inside its card",family.left>=card.left && family.right<=card.right)
                if(metric==SeriesMetric.RESISTANCE) {
                    val average=compose.onNodeWithTag("performance-RESISTANCE-average")
                    average.assert(hasAnyDescendant(hasText("8")))
                    if(!case.contains("missing-heart")) {
                        val percent=when(machine) { Machine.ELLIPTICAL -> "33%"; Machine.BIKE -> "25%"; else -> "50%" }
                        average.assert(hasAnyDescendant(hasText(percent)))
                    }
                    screen("performance-${machine.name.lowercase()}-resistance")
                }
            }
            if(machine==Machine.ROWER) {
                reveal("performance-family-PACE",true)
                val pace=compose.onNodeWithTag("performance-family-PACE")
                pace.assert(hasAnyDescendant(hasText(text(R.string.report_slowest_short))))
                pace.assert(hasAnyDescendant(hasText(text(R.string.report_fastest_short))))
                screen("performance-rower-pace")
            }
            inspectText()
            compose.onNodeWithTag("detail-back").performClick()
            assertEquals(1,compose.activity.page.value)
        }
    }
}
