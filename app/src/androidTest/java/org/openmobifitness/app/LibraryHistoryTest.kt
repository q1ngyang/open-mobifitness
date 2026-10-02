package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.*
import java.util.UUID

/** Synthetic fixtures only. Host varies window, locale and font scale for the same functional flow. */
class LibraryHistoryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val fixtureId=UUID.nameUUIDFromBytes("alpha6-ui-detail".toByteArray()).toString()
    private val customId="alpha6-ui-custom"
    private val case get()=InstrumentationRegistry.getArguments().getString("case","phone")
    private fun tap(tag: String) {
        compose.waitForIdle()
        val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        device.waitForIdle(1500)
        val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val point=compose.runOnUiThread { node.positionInWindow+androidx.compose.ui.geometry.Offset(node.size.width/2f,node.size.height/2f) }
        assertTrue(device.click(point.x.toInt(),point.y.toInt()))
        compose.waitForIdle()
    }
    // Traverse/read placed lazy-item layout on the UI thread. Off-screen prefetch
    // can expose semantics before an item is placed in the viewport.
    private fun scrollTo(tag: String,matcher: SemanticsMatcher) {
        repeat(35) {
            val list=compose.onNodeWithTag(tag)
            val owner=list.fetchSemanticsNode()
            val candidates=compose.onAllNodes(matcher).fetchSemanticsNodes()
            val arrived=compose.runOnUiThread {
                fun bounds(n: androidx.compose.ui.semantics.SemanticsNode)=androidx.compose.ui.geometry.Rect(n.positionInRoot,androidx.compose.ui.geometry.Size(n.size.width.toFloat(),n.size.height.toFloat()))
                val viewport=bounds(owner)
                // Lazy prefetch may expose measured semantics at default coordinates even
                // though the item (or its parent) has never been placed in the viewport.
                val found=candidates.filter { n -> generateSequence(n.layoutInfo) { it.parentInfo }.all { it.isPlaced } }
                    .map(::bounds).filter { it.width>0 && it.height>0 }
                if(found.any { it.top>=viewport.top-1 && it.bottom<=viewport.bottom+1 }) true
                else {
                    val delta=found.firstOrNull()?.let { it.center.y-viewport.center.y } ?: viewport.height*.7f
                    check(owner.config[androidx.compose.ui.semantics.SemanticsActions.ScrollBy].action!!.invoke(0f,delta))
                    false
                }
            }
            if(arrived) return
            compose.waitForIdle()
        }
        error("No visible match in $tag: ${matcher.description}")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); Thread.sleep(350)
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot")
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"alpha7-$case-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Before fun seed() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout=100
        compose.waitUntil(30000) { c.state.value.ready }
        runBlocking {
            val existing=c.repo.archive().sessions.filter { it.device.startsWith("UI fixture") }
            existing.forEach { c.repo.deleteSession(it.id) }
            val today=LocalDate.now()
            val rows=(0..45).map { i -> Session(id=if(i==0) fixtureId else UUID.nameUUIDFromBytes("alpha6-ui-$i".toByteArray()).toString(),start=today.minusDays((i%29).toLong()).atTime(7,0).atZone(ZoneId.systemDefault()).toInstant().toString(),end=today.minusDays((i%29).toLong()).atTime(7,40).atZone(ZoneId.systemDefault()).toInstant().toString(),device=if(i==0) "UI fixture detail" else "UI fixture $i",machine=if(i%3==0) Machine.ELLIPTICAL else if(i%3==1) Machine.BIKE else Machine.ROWER,protocol=Protocol.DEMO,status="completed",demo=true,elapsedMs=2411000,caloriesKcal=396.0-i,caloriesEstimated=true,distanceM=5820.0-i*20,distanceEstimated=true,workoutTitle=if(i==0) "有氧进阶 40 分钟" else "HIIT ${20+i%3*10}",workoutId="cardio40",archived=i%7==1) }
            val speed=5820.0/2411
            val samples=(1..2411 step 5).map { i -> Sample(fixtureId,i*1000L,Metrics(cadence=74.0+kotlin.math.sin(i/70.0)*12,resistance=12.0,speedMps=speed,distanceM=i*speed,heartBpm=149+(kotlin.math.sin(i/130.0)*16).toInt(),powerW=112.0+kotlin.math.sin(i/90.0)*40,powerEstimated=true,stepCount=i*2)) }
            c.repo.importArchive(Archive(rows,samples))
            c.repo.saveWorkout(Workout(id=customId,title="UI custom 50%",steps=listOf(Step(target=120.0,resistancePercent=50))))
        }
        compose.runOnUiThread { c.dismissResult(); c.setDemo(true); c.setTheme(if(case.contains("dark")) "dark" else "light"); c.prefs.edit().putStringSet("favorite_workouts",setOf("cardio40","hiit30")).commit(); compose.activity.page.value=0; compose.activity.focusTraining.value=false }
        compose.activityRule.scenario.recreate()
    }
    @After fun finish() { compose.runOnUiThread { if(c.state.value.session!=null) c.finish() }; compose.waitUntil(20000) { c.state.value.session==null } }
    @Test fun libraryHistoryAndDetailVisualAudit() {
        compose.onNodeWithTag("plan-library").assertExists()
        screenshot("library")
        scrollTo("plan-library",hasTestTag("plan-search"))
        compose.onNodeWithTag("plan-search").performTextInput("UI custom")
        compose.onNodeWithTag("plan-search").performImeAction()
        scrollTo("plan-library",hasTestTag("plan-$customId"))
        tap("plan-$customId")
        compose.onNodeWithText("12 · 50%",substring=true).assertExists()
        screenshot("plan-mapping")
        compose.onNodeWithText(compose.activity.getString(R.string.duplicate)).assertDoesNotExist()
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithTag("plan-search").performTextClearance()
        compose.onNodeWithTag("plan-search").performImeAction()
        scrollTo("plan-library",hasTestTag("favorite-cardio40"))
        tap("favorite-cardio40")
        compose.waitUntil(10000) { !c.prefs.getStringSet("favorite_workouts",emptySet())!!.contains("cardio40") }
        compose.runOnUiThread { compose.activity.page.value=1 }
        compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.loading)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("history-search").performTextInput("UI fixture")
        compose.onNodeWithTag("history-search").performImeAction()
        val recent=RecordDates.recent()
        val count=runBlocking { c.repo.history.page(HistoryQuery(recent.first,recent.second,search="UI fixture")).count }
        compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,1,((count+19)/20).coerceAtLeast(1),count)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("history")
        if(case=="phone-final") {
            scrollTo("history-list",hasTestTag("all-records")); tap("all-records")
            compose.onNodeWithTag("record-year").assertExists()
            compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
            screenshot("all-records")
            val total=count; val pages=(total+19)/20
            fun pageVisible(n: Int)=compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,n,pages,total)).fetchSemanticsNodes().isNotEmpty()
            compose.waitUntil(30000) { pageVisible(1) }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.next_page)).performClick()
            compose.waitUntil(30000) { pageVisible(2) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(30000) { pageVisible(2) }
            compose.onNodeWithTag("history-search").assertTextEquals("UI fixture")
            tap("all-records-back")
            compose.waitUntil(30000) { pageVisible(1) }
        }
        compose.onNodeWithTag("history-search").performTextReplacement("UI fixture detail")
        compose.onNodeWithTag("history-search").performImeAction()
        compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,1,1,1)).fetchSemanticsNodes().isNotEmpty() }
        scrollTo("history-list",hasTestTag("record-$fixtureId"))
        screenshot("history-search")
        tap("record-$fixtureId")
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        screenshot("detail")
        scrollTo("detail-scroll",hasText(compose.activity.getString(R.string.motion_data)))
        screenshot("detail-chart")
        compose.onNodeWithTag("archive-record").assertDoesNotExist()
        tap("detail-back")
        compose.onNodeWithTag("record-$fixtureId").assertIsDisplayed()
        compose.onNodeWithTag("history-filter").performClick(); screenshot("history-filter")
        compose.onNodeWithText(compose.activity.getString(R.string.confirm)).performClick()
        compose.runOnUiThread { compose.activity.page.value=3; compose.activity.settingsSection.value="" }
        screenshot("settings")
        compose.onNodeWithTag("setting-floating").performScrollTo().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.auto_floating)).performScrollTo().assertExists()
        screenshot("floating-settings")
        compose.runOnUiThread { compose.activity.openDiagnostics() }
        compose.onNodeWithTag("export-diagnostics").performScrollTo().assertIsDisplayed()
        screenshot("diagnostics")
        compose.runOnUiThread { compose.activity.settingsSection.value="about" }
        screenshot("about")
        compose.runOnUiThread { compose.activity.page.value=2; c.setDemo(false); c.ble.state.value=org.openmobifitness.app.ble.LinkState(name="MB-EP · UI fixture",phase="ready",protocol=Protocol.V1,machine=Machine.ELLIPTICAL,range=ResistanceRange(1.0,24.0),metrics=Metrics(cadence=60.0,resistance=12.0),motionAt=android.os.SystemClock.elapsedRealtime(),writable=true,dataReceived=true) }
        screenshot("devices")
        // UI fixture only: the GATT client remains disconnected; no hardware claim.
        if(compose.onAllNodesWithTag("device-connections").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("connection-help").performScrollTo() else scrollTo("device-list",hasTestTag("connection-help"))
        tap("connection-help"); screenshot("connection-help")
        compose.onNodeWithTag("open-diagnostics").performScrollTo().performClick()
        assertEquals("diagnostics",compose.activity.settingsSection.value)
        compose.runOnUiThread { c.setDemo(true) }
        compose.runOnUiThread { c.select(Presets.all.first { it.id=="cardio40" }); compose.activity.startTraining() }
        compose.waitUntil(30000) { c.state.value.session!=null }
        compose.runOnUiThread { c.pauseResume(); compose.activity.focusTraining.value=false; compose.activity.page.value=3 }
        compose.waitUntil(30000) { c.state.value.paused }
        compose.onNodeWithTag("return-workout").assertIsDisplayed(); screenshot("return-banner")
        val sessionId=c.state.value.session!!.id
        tap("return-workout")
        compose.waitUntil(30000) { compose.activity.focusTraining.value }
        assertEquals(sessionId,c.state.value.session!!.id)
        compose.runOnUiThread { c.finish() }
        compose.waitUntil(30000) { c.state.value.session==null && c.finishedSession.value==sessionId }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.workout_saved)).assertIsDisplayed()
        screenshot("saved-result")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.workout_saved)).assertExists()
        // Dialog windows have their own origin below the status bar. Read the
        // actual screen bounds through accessibility instead of window coordinates.
        val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val close=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.desc(compose.activity.getString(R.string.close))),10000)
        assertNotNull(close); close.click()
        compose.waitUntil(10000) { c.finishedSession.value==null }
    }
}
