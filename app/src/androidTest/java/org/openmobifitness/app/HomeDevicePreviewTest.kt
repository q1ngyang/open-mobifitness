package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.time.*
import java.util.Locale

/** Synthetic page fixtures, not a physical equipment connection. */
class HomeDevicePreviewTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val layout get()=InstrumentationRegistry.getArguments().getString("layout","phone")
    private val ids=listOf("v011-home-one","v011-home-two","v011-home-demo")
    private var createdSession: String?=null
    private fun readyEquipment()=LinkState(name="MB-EP · UI fixture",phase="ready",protocol=Protocol.V1,machine=Machine.ELLIPTICAL,range=ResistanceRange(1.0,24.0),metrics=Metrics(cadence=60.0,resistance=12.0,heartBpm=92,powerW=235.0,powerEstimated=true),motionAt=android.os.SystemClock.elapsedRealtime(),heartAt=android.os.SystemClock.elapsedRealtime(),writable=true,dataReceived=true)
    private fun screenshot(name: String) {
        compose.waitForIdle(); Thread.sleep(350)
        val device=androidx.test.uiautomator.UiDevice.getInstance(inst)
        assertFalse("System dialog covers preview",device.hasObject(androidx.test.uiautomator.By.textContains("isn\'t responding")))
        val bitmap=inst.uiAutomation.takeScreenshot()!!
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(dir,"v011-$layout-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun openPage(page: Int) { compose.runOnUiThread { compose.activity.page.value=page; compose.activity.focusTraining.value=false }; compose.waitForIdle() }
    @Before fun setup() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout=100
        if(android.os.Build.VERSION.SDK_INT>=31) listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT).forEach { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) }
        if(android.os.Build.VERSION.SDK_INT>=33) inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        compose.waitUntil(30000) { c.state.value.ready }
        compose.runOnUiThread { c.dismissResult(); c.setDemo(false); c.setTheme(if(layout.contains("dark")) "dark" else "light"); c.prefs.edit().putStringSet("favorite_workouts",setOf("cardio40","hiit30")).commit() }
        runBlocking {
            ids.forEach { c.repo.deleteSession(it) }
            val time=LocalDate.now().atTime(8,0).atZone(ZoneId.systemDefault()).toInstant().toString()
            c.repo.save(Session(id=ids[0],start=time,end=time,status="completed",elapsedMs=1200000,device="UI fixture · preview",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,caloriesKcal=146.0,caloriesEstimated=true,distanceM=3000.0,distanceEstimated=true))
            c.repo.save(Session(id=ids[1],start=time,end=time,status="completed",elapsedMs=720000,device="UI fixture · preview",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,caloriesKcal=90.0,caloriesEstimated=true,distanceM=1800.0,distanceEstimated=true))
            c.repo.save(Session(id=ids[2],start=time,end=time,status="completed",elapsedMs=600000,demo=true,protocol=Protocol.DEMO,caloriesKcal=999.0,distanceM=99999.0))
        }
        openPage(0)
    }
    @After fun cleanup() {
        compose.runOnUiThread { if(c.state.value.session!=null) c.finish(); c.ble.disconnect(); c.heart.disconnect() }
        compose.waitUntil(20000) { c.state.value.session==null }
        compose.runOnUiThread { c.dismissResult(); compose.activity.stopService(android.content.Intent(compose.activity,org.openmobifitness.app.service.WorkoutService::class.java)) }
        runBlocking { (ids+listOfNotNull(createdSession)).forEach { c.repo.deleteSession(it) } }
    }
    @Test fun finalLayoutSnapshots() {
        val dates=RecordDates.today()
        val expected=runBlocking { c.repo.history.overview(HistoryQuery(dates.first,dates.second,source=1)) }
        compose.waitUntil(30000) { compose.onAllNodes(hasTestTag("today-metric-2") and hasText(expected.count.toString())).fetchSemanticsNodes().isNotEmpty() }
        screenshot("home-disconnected")
        compose.runOnUiThread { c.ble.state.value=readyEquipment() }
        screenshot("home-connected")
        if(InstrumentationRegistry.getArguments().getString("heroOnly")=="true") return
        openPage(2)
        compose.onNodeWithTag("heart-accessory").assertDoesNotExist()
        screenshot("devices-connected")
        openPage(3)
        compose.runOnUiThread { compose.activity.settingsSection.value="" }
        screenshot("settings")
    }
    @Test fun fourColorSummaryConnectionAndHeartAccessoryAreTruthful() {
        val dates=RecordDates.today()
        val expected=runBlocking { c.repo.history.overview(HistoryQuery(dates.first,dates.second,source=1)) }
        val calories=if(expected.caloriesPresent==expected.count) String.format(Locale.getDefault(),"%.0f",expected.calories!!) else "—"
        compose.waitUntil(30000) { compose.onAllNodesWithText(calories).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("today-metric-2").assertTextContains(expected.count.toString())
        screenshot("home-disconnected")
        compose.onNodeWithTag("hero-start").performScrollTo().performClick()
        compose.runOnUiThread { assertEquals(2,compose.activity.page.value); assertNull(c.state.value.session) }
        compose.onNodeWithTag("heart-accessory").assertDoesNotExist()
        screenshot("devices-disconnected")
        compose.runOnUiThread { c.ble.state.value=readyEquipment() }
        compose.onNodeWithTag("heart-accessory").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.heart_from_equipment,92)).assertExists()
        screenshot("devices-connected")
        compose.runOnUiThread { c.heart.state.value=LinkState(name="HR accessory · UI fixture",phase="ready",protocol=Protocol.UNKNOWN,machine=Machine.HEART,metrics=Metrics(heartBpm=128),heartAt=android.os.SystemClock.elapsedRealtime()) }
        compose.onNodeWithTag("heart-accessory").assertExists()
        screenshot("heart-accessory")
        compose.runOnUiThread { c.heart.disconnect(); c.ble.state.value=readyEquipment() }
        compose.onNodeWithTag("heart-accessory").assertDoesNotExist()
        openPage(0)
        compose.onNodeWithTag("plan-library").performScrollToIndex(0)
        screenshot("home-connected")
        compose.onNodeWithTag("hero-history").performScrollTo().performClick()
        compose.runOnUiThread { assertEquals(1,compose.activity.page.value) }
        openPage(0)
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-search"))
        compose.onNodeWithTag("plan-search").performScrollTo().performTextInput("HIIT")
        compose.onNodeWithTag("plan-search").performImeAction()
        screenshot("search")
        compose.onNodeWithTag("plan-search").performTextClearance()
        compose.onNodeWithTag("plan-search").performImeAction()
        compose.onNodeWithTag("plan-library").performScrollToIndex(0)
        compose.runOnUiThread { c.ble.state.value=readyEquipment(); c.select(Presets.all.first()) }
        compose.onNodeWithTag("hero-start").performScrollTo().performClick()
        compose.waitUntil(30000) { c.state.value.session!=null }
        createdSession=c.state.value.session!!.id
        compose.runOnUiThread { assertNull(c.state.value.selected); assertTrue(compose.activity.focusTraining.value); c.pauseResume(); c.finish() }
        compose.waitUntil(30000) { c.state.value.session==null }
        compose.runOnUiThread { c.dismissResult() }
        openPage(3)
        compose.runOnUiThread { compose.activity.settingsSection.value="" }
        screenshot("settings")
    }
}
