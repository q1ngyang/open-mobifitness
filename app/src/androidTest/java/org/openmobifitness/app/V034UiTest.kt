package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.util.UUID

/** Dedicated emulator; real Compose, controller and Room, with explicitly synthetic telemetry. */
class V034UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val layout get()=InstrumentationRegistry.getArguments().getString("case","phone")
    private fun shot(name: String) {
        compose.waitForIdle()
        val device=UiDevice.getInstance(inst)
        assertFalse("System error obscures UI",device.hasObject(By.res("android:id/aerr_wait")))
        val bitmap=inst.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val dir=File(compose.activity.getExternalFilesDir(null),"v034-screens/$layout").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val cfg=compose.activity.resources.configuration
        File(dir,"environment.txt").writeText("${cfg.screenWidthDp}x${cfg.screenHeightDp}dp font=${cfg.fontScale} locale=${cfg.locales.toLanguageTags()} theme=${c.theme.value}\nSynthetic UI fixtures; no physical equipment.\n")
    }
    private fun page(index: Int) { compose.runOnUiThread { c.dismissResult();c.userPicker.value=false;c.cancelStart();compose.activity.focusTraining.value=false;compose.activity.page.value=index };compose.waitForIdle() }
    @Before fun setup() {
        Configurator.getInstance().waitForIdleTimeout=100
        listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.POST_NOTIFICATIONS).forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) } }
        compose.waitUntil(90000) { c.state.value.ready }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            val user=c.repo.users.value.firstOrNull { it.name=="小林" && it.available } ?: UserProfile(id=UUID.nameUUIDFromBytes("v034-ui".toByteArray()).toString(),name="小林",weightKg=65.0)
            c.repo.saveUser(user);c.switchUser(user.id).join()
        }
        compose.runOnUiThread { c.dismissResult();c.setIdentityPolicy(IdentityPolicy.REMEMBER);c.setTheme(if(layout.contains("dark")) "dark" else "light");c.error(null) }
        page(0)
    }
    @After fun cleanup() {
        runBlocking { if(c.state.value.session!=null) c.finish().join() }
        compose.runOnUiThread { c.dismissResult();c.ble.disconnect();c.setDemo(false) }
    }
    @Test fun completionAndConnectionStates() {
        compose.runOnUiThread {
            c.setDemo(true,Machine.BIKE)
            c.select(Workout(title="20 分钟稳态训练",machine=Machine.BIKE,ownerUserId=c.currentUser.value!!.id,steps=List(3) { Step(target=0.1) }))
        }
        runBlocking { c.start().join() }
        compose.runOnUiThread { compose.activity.focusTraining.value=true }
        compose.waitUntil(30000) { c.state.value.completionPending }
        // Give the completed summary representative values. The actual deadline transition above is real.
        runBlocking { withContext(Dispatchers.Main) {
            val plan=c.state.value.selected!!.let { it.copy(steps=it.steps.mapIndexed { index,step -> step.copy(target=listOf(300.0,600.0,300.0)[index]) }) }
            val s=c.state.value.session!!.copy(elapsedMs=1200000,planCompletedMs=1200000,distanceM=3200.0,caloriesKcal=160.0,caloriesEstimated=true,workoutSnapshot=Exchange.workouts(listOf(plan)))
            c.repo.save(s)
            c.state.value=c.state.value.copy(session=s,selected=plan)
            val totals=TelemetryTotals().apply { restore(s,Metrics()) }
            Controller::class.java.getDeclaredField("totals").apply { isAccessible=true }.set(c,totals)
        } }
        compose.onNodeWithTag("plan-completion").assertIsDisplayed()
        val label=compose.onNodeWithTag("completion-heading-text",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val check=compose.onNodeWithTag("completion-check",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Check must be on the right",check.left>=label.right)
        assertTrue("Check must center against both text lines",kotlin.math.abs(check.center.y-label.center.y)<2f)
        for(metric in listOf("TIME","CALORIES","DISTANCE")) compose.onNodeWithTag("completion-stat-$metric",useUnmergedTree=true).assertIsDisplayed()
        shot("01-completed")
        UiDevice.getInstance(inst).pressBack();compose.waitForIdle()
        assertTrue(c.state.value.completionPending)
        compose.runOnUiThread { c.error(R.string.storage_failed) }
        compose.onNodeWithText(compose.activity.getString(R.string.plan_save_error)).assertIsDisplayed()
        shot("02-save-error")
        compose.runOnUiThread { c.error(null) }
        compose.onNodeWithTag("plan-continue").performClick()
        compose.waitUntil(15000) { c.state.value.continuingManually }
        assertFalse(c.state.value.automatic)
        shot("03-manual-continuation")
        compose.onNodeWithText(compose.activity.getString(R.string.plan_same_record)).performScrollTo().assertIsDisplayed()
        runBlocking { c.finish().join() }
        compose.waitUntil(15000) { c.finishedSession.value!=null }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("change-owner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.plan_time),substring=true).assertExists()
        shot("04-details")
        page(2)
        compose.runOnUiThread {
            c.state.value=c.state.value.copy(demo=false,session=null,controlOnly=false,starting=false,error=null)
            c.ble.state.value=LinkState(name="MOBI · Rower",phase="ready",machine=Machine.ROWER,protocol=Protocol.V2,deviceDetails=MobiDeviceDetails(supportFold=true,foldedState=1))
        }
        compose.onNodeWithTag("equipment-fold").performScrollTo().assertIsDisplayed();shot("05-fold-ready")
        compose.onNodeWithTag("fold-action").performClick();shot("06-fold-confirm")
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnUiThread { c.ble.state.value=c.ble.state.value.copy(foldPhase="waiting",foldTarget=0) }
        compose.onNodeWithTag("fold-action").assertIsNotEnabled();shot("07-fold-waiting")
        compose.runOnUiThread { c.ble.state.value=c.ble.state.value.copy(foldPhase="timeout",foldTarget=null) };shot("08-fold-timeout")
        compose.runOnUiThread { c.ble.state.value=c.ble.state.value.copy(foldPhase="idle",deviceDetails=MobiDeviceDetails(supportFold=true)) };shot("09-fold-unknown")
        compose.runOnUiThread { c.ble.state.value=c.ble.state.value.copy(deviceDetails=MobiDeviceDetails()) }
        compose.onNodeWithTag("equipment-fold").assertDoesNotExist();shot("10-unsupported")
    }
}
