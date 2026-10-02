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
        File(dir,"alpha6-$case-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
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
        compose.runOnUiThread { c.setDemo(true); c.setTheme(if(case.contains("dark")) "dark" else "light"); c.prefs.edit().putStringSet("favorite_workouts",setOf("cardio40","hiit30")).commit(); compose.activity.page.value=0; compose.activity.focusTraining.value=false }
        compose.activityRule.scenario.recreate()
    }
    @After fun finish() { compose.runOnUiThread { if(c.state.value.session!=null) c.finish() }; compose.waitUntil(20000) { c.state.value.session==null } }
    @Test fun libraryHistoryAndDetailVisualAudit() {
        compose.onNodeWithTag("plan-library").assertExists()
        screenshot("library")
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
        val window=PeriodWindow.of(HistoryPeriod.MONTH,LocalDate.now(),ZoneId.systemDefault())
        val count=runBlocking { c.repo.history.page(HistoryQuery(window.from,window.until,search="UI fixture")).count }
        compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,1,((count+19)/20).coerceAtLeast(1),count)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("history")
        if(case=="phone-final") {
            compose.onNodeWithText(compose.activity.getString(R.string.all_items)).performClick()
            val total=runBlocking { c.repo.history.page(HistoryQuery(search="UI fixture")).count }
            val pages=(total+19)/20
            fun pageVisible(n: Int)=compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,n,pages,total)).fetchSemanticsNodes().isNotEmpty()
            compose.waitUntil(30000) { pageVisible(1) }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.next_page)).performClick()
            compose.waitUntil(30000) { pageVisible(2) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(30000) { pageVisible(2) }
            compose.onNodeWithTag("history-search").assertTextEquals("UI fixture")
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.previous_page)).performClick()
            compose.waitUntil(30000) { pageVisible(1) }
            compose.onNodeWithText(compose.activity.getString(R.string.week)).performClick()
            val week=PeriodWindow.of(HistoryPeriod.WEEK,LocalDate.now(),ZoneId.systemDefault())
            val weekly=runBlocking { c.repo.history.page(HistoryQuery(week.from,week.until,search="UI fixture")).count }
            compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,1,((weekly+19)/20).coerceAtLeast(1),weekly)).fetchSemanticsNodes().isNotEmpty() }
            screenshot("history-week")
            scrollTo("history-list",hasTestTag("history-trend"))
            tap("history-trend")
            compose.onNodeWithText(compose.activity.getString(R.string.hide_trend)).assertExists()
            screenshot("history-trend")
            tap("history-trend")
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
        scrollTo("detail-scroll",hasTestTag("archive-record"))
        screenshot("before-archive")
        tap("archive-record")
        try {
            compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.restore_record)).fetchSemanticsNodes().isNotEmpty() }
        } catch(e: Exception) {
            screenshot("archive-timeout")
            android.util.Log.e("OpenMOBI-ui-test","archive=${runBlocking { c.repo.history.session(fixtureId)?.archived }}; "+compose.onRoot().printToString())
            throw e
        }
        assertTrue(runBlocking { c.repo.history.session(fixtureId)!!.archived })
        tap("archive-record")
        compose.waitUntil(30000) { compose.onAllNodesWithText(compose.activity.getString(R.string.archive_action)).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(runBlocking { c.repo.history.session(fixtureId)!!.archived })
        screenshot("detail-actions")
        tap("detail-back")
        compose.onNodeWithTag("record-$fixtureId").assertIsDisplayed()
        compose.onNodeWithTag("history-filter").performClick(); screenshot("history-filter")
        compose.onNodeWithText(compose.activity.getString(R.string.confirm)).performClick()
        compose.runOnUiThread { c.select(Presets.all.first { it.id=="cardio40" }); compose.activity.startTraining() }
        compose.waitUntil(30000) { c.state.value.session!=null }
        compose.runOnUiThread { c.pauseResume(); compose.activity.focusTraining.value=false; compose.activity.page.value=3 }
        compose.waitUntil(30000) { c.state.value.paused }
        compose.onNodeWithTag("return-workout").assertIsDisplayed(); screenshot("return-banner")
        val sessionId=c.state.value.session!!.id
        tap("return-workout")
        compose.waitUntil(30000) { compose.activity.focusTraining.value }
        assertEquals(sessionId,c.state.value.session!!.id)
    }
}
