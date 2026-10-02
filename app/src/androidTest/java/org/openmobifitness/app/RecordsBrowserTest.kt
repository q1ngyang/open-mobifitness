package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.openmobifitness.core.*
import java.io.File
import java.time.*
import java.util.UUID

/** Browse older records through the actual year/month controls, including old archive flags. */
class RecordsBrowserTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val ids=(0..2).map { UUID.nameUUIDFromBytes("alpha7-browser-$it".toByteArray()).toString() }
    private val oldYear=LocalDate.now().year-2
    private val case get()=InstrumentationRegistry.getArguments().getString("case","tablet")
    @Before fun seed() {
        compose.waitUntil(30000) { c.state.value.ready }
        runBlocking {
            val dates=listOf(LocalDate.now().minusDays(1),LocalDate.of(oldYear,1,8),LocalDate.of(oldYear,8,18))
            c.repo.importArchive(Archive(dates.mapIndexed { i,d ->
                val start=d.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant()
                Session(id=ids[i],start=start.toString(),end=start.plusSeconds(2400).toString(),device="Browser fixture $i",machine=Machine.ELLIPTICAL,protocol=Protocol.DEMO,status="completed",demo=true,elapsedMs=2400000,caloriesKcal=300.0,distanceM=5000.0,archived=i==1)
            },emptyList()))
        }
        compose.runOnUiThread { c.dismissResult(); c.setTheme(if(case.contains("dark")) "dark" else "light"); compose.activity.page.value=1; compose.activity.focusTraining.value=false }
    }
    @After fun cleanUp() { runBlocking { ids.forEach { c.repo.deleteSession(it) } } }
    private fun expectCount(count: Int) {
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText(compose.activity.getString(R.string.page_count,1,1,count)).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"alpha7-$case-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun olderRecordsAreReachableWithoutArchiveActions() {
        compose.onNodeWithTag("history-search").performTextInput("Browser fixture")
        compose.onNodeWithTag("history-search").performImeAction(); expectCount(1)
        screenshot("history-browser")
        val narrow=compose.activity.resources.configuration.screenWidthDp<600
        if(narrow) compose.onNodeWithTag("history-list").performScrollToIndex(1)
        compose.onNodeWithTag("all-records").performClick(); expectCount(1)
        compose.onNodeWithTag("record-year").performClick()
        compose.onNodeWithText(oldYear.toString()).performClick(); expectCount(2)
        compose.onNodeWithTag("record-month-1").performClick(); expectCount(1)
        if(narrow) compose.onNodeWithTag("history-list").performScrollToIndex(2)
        compose.onNodeWithTag("record-${ids[1]}").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); expectCount(1)
        compose.onNodeWithTag("record-month-1").assertIsSelected()
        screenshot("all-records")
        compose.onNodeWithTag("record-year").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.all_years)).performClick(); expectCount(3)
        compose.onNodeWithTag("all-records-back").performClick(); expectCount(1)
    }
}
