package org.openmobifitness.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.Assert.*
import java.util.regex.Pattern

/** Exercises the actual Storage Access Framework export, including the provider write. */
class DiagnosticExportTest {
    @Test fun diagnosticLogCanBeSavedThroughTheSystemPicker() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val name="OpenMOBI-log-test-${System.currentTimeMillis()}.txt"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.export("diagnostics") }
            val editor=device.wait(Until.findObject(By.clazz("android.widget.EditText")),30_000)
            assertNotNull("System file picker should open",editor)
            // Use the local Downloads provider, independently of the remembered directory.
            val roots=device.findObject(By.desc("Show roots"))
            if(roots!=null) {
                roots.click()
                // The tablet toolbar and breadcrumb also say "Downloads". Only
                // the drawer's root item has android:id/title.
                val downloads=device.findObject(UiSelector().resourceId("android:id/title").text("Downloads"))
                assertTrue(downloads.waitForExists(10_000)); assertTrue(downloads.click())
                assertTrue(device.wait(Until.gone(By.text("Save to")),10_000))
            }
            device.wait(Until.findObject(By.clazz("android.widget.EditText")),10_000).text=name
            // Let filename validation and the tablet picker layout settle before
            // obtaining button coordinates; setting text can move the save row.
            device.waitForIdle(2000)
            val save=device.wait(Until.findObject(By.text(Pattern.compile("save",Pattern.CASE_INSENSITIVE)).enabled(true)),10_000)
            assertNotNull("Save action should be visible",save); save.click()
            assertTrue(device.wait(Until.gone(By.pkg("com.android.documentsui")),30_000))
            var text=""; val deadline=android.os.SystemClock.elapsedRealtime()+15_000
            do {
                val fd=instrumentation.uiAutomation.executeShellCommand("cat /sdcard/Download/$name")
                text=android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                if(!text.startsWith("OpenMOBI diagnostic report 4")) Thread.sleep(200)
            } while(!text.startsWith("OpenMOBI diagnostic report 4") && android.os.SystemClock.elapsedRealtime()<deadline)
            assertTrue("The exported file must contain the diagnostic report",text.startsWith("OpenMOBI diagnostic report 4"))
            assertTrue("The exported diagnostic report is bounded",text.toByteArray(Charsets.UTF_8).size<128*1024)
            assertTrue(text.contains("Version: ${BuildConfig.VERSION_NAME}")); assertFalse(text.contains("session_id,start_utc"))
            instrumentation.uiAutomation.executeShellCommand("rm /sdcard/Download/$name").close()
        }
    }
}
