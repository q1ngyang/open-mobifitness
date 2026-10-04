package org.openmobifitness.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.time.*
import java.util.*

/** Isolated synthetic records; exercises the actual home refresh and post-save navigation. */
class HomeTodayTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val case get()=InstrumentationRegistry.getArguments().getString("case","phone390")
    private val owner=UserProfile(UUID.nameUUIDFromBytes("home-today-owner".toByteArray()).toString(),"小林 · 今日")
    private val other=UserProfile(UUID.nameUUIDFromBytes("home-today-other".toByteArray()).toString(),"访客 · 今日")
    private val owned=mutableListOf<String>()
    private fun screen(name: String) {
        compose.waitForIdle()
        val dir=File(compose.activity.getExternalFilesDir(null),"today-screens/$case").apply { mkdirs() }
        val bitmap=inst.uiAutomation.takeScreenshot()!!
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    private fun totals(expected: List<String>) {
        try { compose.waitUntil(30000) {
            expected.indices.all { i -> compose.onAllNodes(hasTestTag("today-value-$i") and hasText(expected[i]),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        } } catch(error: Throwable) {
            screen("totals-failed")
            println(compose.onRoot(useUnmergedTree=true).printToString())
            throw error
        }
        expected.forEachIndexed { i,value -> compose.onNodeWithTag("today-value-$i",useUnmergedTree=true).assertTextEquals(value) }
    }
    @Before fun setup() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout=100
        compose.waitUntil(60000) { c.state.value.ready }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            for(user in listOf(owner,other)) {
                c.repo.saveUser(user)
                c.repo.history.reportIds(HistoryQuery(owner=user.id)).forEach { c.repo.deleteSession(it) }
            }
            c.switchUser(owner.id).join()
        }
        compose.runOnUiThread {
            c.cancelStart(); c.dismissResult(); c.exitControl(); c.userPicker.value=false
            c.setDemo(false); c.display.floating(false); c.setIdentityPolicy(IdentityPolicy.REMEMBER)
            c.setTheme(if(case.contains("dark")) "dark" else "light"); c.setImperial(false)
            compose.activity.page.value=0; compose.activity.focusTraining.value=false
        }
    }
    @After fun cleanup() {
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            owned.forEach { c.repo.deleteSession(it) }
        }
        compose.runOnUiThread { c.dismissResult(); c.exitControl(); c.setDemo(false) }
    }
    private fun save(demo: Boolean,user: UserProfile,minutes: Int,kcal: Double,meters: Double) {
        val date=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).plusHours(1).toInstant().toString()
        val record=Session(ownerUserId=user.id,startedUserId=user.id,startedUserName=user.name,start=date,end=date,
            device="Home today UI fixture",status="completed",demo=demo,protocol=if(demo) Protocol.DEMO else Protocol.V1,
            machine=Machine.ELLIPTICAL,elapsedMs=minutes*60000L,caloriesKcal=kcal,distanceM=meters)
        owned+=record.id
        runBlocking { c.repo.save(record) }
    }
    @Test fun savedDemoRealAndNewlyFinishedRecordsRefreshTheCurrentUsersFourTotals() {
        totals(listOf("0","0","0","0.0"))
        save(true,owner,20,100.0,2000.0)
        totals(listOf("20","100","1","2.0"))
        compose.onNodeWithTag("today-demo-note",useUnmergedTree=true).assertExists()
        screen("home-demo")
        save(false,owner,10,60.0,1000.0)
        save(false,other,5,30.0,500.0)
        totals(listOf("30","160","2","3.0"))
        screen("home-mixed")
        runBlocking { c.switchUser(other.id).join() }
        totals(listOf("5","30","1","0.5"))
        compose.onNodeWithTag("today-demo-note",useUnmergedTree=true).assertDoesNotExist()
        screen("home-real-only")
        runBlocking { c.switchUser(owner.id).join() }
        totals(listOf("30","160","2","3.0"))
        compose.runOnUiThread { c.setDemo(true); c.select(null); compose.activity.startTraining() }
        compose.waitUntil(30000) { c.state.value.session?.elapsedMs?.let { it>=2000 }==true }
        owned+=c.state.value.session!!.id
        runBlocking { c.finish().join() }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-back").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail-back").performClick()
        val stats=runBlocking { c.repo.history.todayOverview(owner.id) }
        assertEquals(3,stats.count); assertEquals(2,stats.demoCount)
        totals(listOf((stats.elapsedMs/60000).toString(),String.format(Locale.getDefault(),"%.0f",stats.calories!!),"3",String.format(Locale.getDefault(),"%.1f",stats.distanceM!!/1000)))
        compose.onNodeWithTag("workout-dock").assertDoesNotExist()
        screen("home-after-save")
    }
}
