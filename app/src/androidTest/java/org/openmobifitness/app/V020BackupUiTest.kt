package org.openmobifitness.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.TransferPhase
import org.openmobifitness.core.Machine
import org.openmobifitness.core.MetricId
import java.io.File

/** Explicit large_backup argument enables the real system document-picker path. */
class V020BackupUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun largeArchiveFromTheSystemPickerRestoresSelectedPreferences() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val requested=InstrumentationRegistry.getArguments().getString("large_backup")
        Assume.assumeTrue("Supply a fixture in Downloads and pass large_backup",requested!=null)
        val filename=requireNotNull(requested)
        val c=(compose.activity.application as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val dir=File(compose.activity.getExternalFilesDir(null),"v020-large-backup-ui").apply { mkdirs() }
        fun shot(name: String) {
            compose.waitForIdle()
            Thread.sleep(500)
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
        compose.waitUntil(30000) { c.state.value.ready }
        compose.runOnUiThread {
            c.dismissResult(); c.exitControl(); c.setTheme("light"); c.setImperial(false)
            compose.activity.page.value=3; compose.activity.settingsSection.value="data"
        }
        val before=runBlocking(Dispatchers.IO) { c.repo.db.records().sampleCount() }
        val metricSelection=listOf(MetricId.HEART,MetricId.CADENCE)
        compose.runOnUiThread { c.display.save(DisplayScope.TRAINING,metricSelection,Machine.ELLIPTICAL) }
        var imported: String?=null
        try {
            compose.onNodeWithTag("import-backup").performScrollTo().performClick()
            if(!device.wait(Until.hasObject(By.text(filename)),3000)) {
                val roots=device.wait(Until.findObject(By.desc("Show roots")),20000)
                assertNotNull("System document picker must finish opening",roots)
                roots!!.click()
                val downloads=device.wait(Until.findObject(By.text("Downloads")),15000)
                assertNotNull("Downloads root must be available",downloads)
                downloads!!.click()
            }
            val document=device.wait(Until.findObject(By.text(filename)),15000)
            assertNotNull("Large fixture must be selectable in the system picker",document)
            shot("01-system-picker")
            document!!.click()
            compose.waitUntil(30000) { c.backup.status.value!=null || c.backup.preview.value!=null }
            shot("02-reading")
            compose.waitUntil(900000) { c.backup.preview.value!=null || c.backup.status.value?.phase==TransferPhase.FAILED }
            val preview=c.backup.preview.value
            assertNotNull("Archive must reach a valid preview: ${c.backup.status.value}",preview)
            assertEquals(360001L,preview!!.preview.samples)
            imported=runBlocking(Dispatchers.IO) { preview.db.records().sessions().single().id }
            compose.onNodeWithTag("confirm-restore").assertIsDisplayed()
            shot("03-preview")
            compose.onAllNodesWithText(compose.activity.getString(R.string.backup_appearance)).onLast().performScrollTo().performClick()
            compose.onNodeWithTag("confirm-restore").performClick()
            compose.waitUntil(900000) { c.backup.status.value?.phase in setOf(TransferPhase.COMPLETE,TransferPhase.FAILED) }
            assertEquals(TransferPhase.COMPLETE,c.backup.status.value?.phase)
            assertEquals(before+360001,runBlocking(Dispatchers.IO) { c.repo.db.records().sampleCount() })
            assertEquals("dark",c.theme.value); assertTrue(c.imperial.value)
            assertEquals(27,c.display.targetCadence.value) // Estimation settings belong to Appearance.
            assertEquals("Unselected metric preferences must stay unchanged",metricSelection,c.display.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL))
            compose.waitForIdle()
            shot("04-restored-preferences")
            File(dir,"result.txt").writeText("system_picker=true\nsamples_restored=360001\npreferences=appearance only\nresult=passed\n")
        } finally {
            compose.runOnUiThread { c.backup.cancel(); c.backup.discard(); c.backup.dismiss() }
            imported?.let { id -> runBlocking { c.repo.deleteSession(id) } }
        }
    }
}
