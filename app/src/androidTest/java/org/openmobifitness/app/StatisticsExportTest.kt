package org.openmobifitness.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.app.data.HistoryQuery
import org.openmobifitness.core.*
import java.util.regex.Pattern

class StatisticsExportTest {
    @Test fun filteredReadableStatisticsReachSystemDocumentProvider() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val token="csv-fixture-${System.currentTimeMillis()}"
        val s=Session(device=token,workoutTitle="UI report",machine=Machine.ELLIPTICAL,status="completed",elapsedMs=2411000,caloriesKcal=396.0,distanceM=5820.0,caloriesEstimated=true)
        runBlocking { c.repo.save(s,Sample(s.id,1000,Metrics(heartBpm=149,cadence=74.0,powerW=112.0,powerEstimated=true))) }
        val device=UiDevice.getInstance(instrumentation); Configurator.getInstance().waitForIdleTimeout=100
        val name="$token.csv"
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.exportReport(HistoryQuery(search=token)) }
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),30_000))
            device.findObject(By.desc("Show roots"))?.let { roots -> roots.click(); val downloads=device.findObject(UiSelector().resourceId("android:id/title").text("Downloads")); assertTrue(downloads.waitForExists(10_000)); downloads.click(); device.wait(Until.gone(By.text("Save to")),10_000) }
            device.wait(Until.findObject(By.clazz("android.widget.EditText")),10_000).text=name
            device.waitForIdle(2000)
            val save=device.wait(Until.findObject(By.text(Pattern.compile("save",Pattern.CASE_INSENSITIVE)).enabled(true)),10_000)
            assertNotNull(save); save.click(); assertTrue(device.wait(Until.gone(By.pkg("com.android.documentsui")),30_000))
            var content=""; val deadline=android.os.SystemClock.elapsedRealtime()+30_000
            do {
                val fd=instrumentation.uiAutomation.executeShellCommand("cat /sdcard/Download/$name")
                content=android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                if(!content.contains(token)) Thread.sleep(200)
            } while(!content.contains(token) && android.os.SystemClock.elapsedRealtime()<deadline)
            val rows=Csv.read(content)
            assertEquals(2,rows.size); assertTrue(rows[1].contains(token)); assertTrue(rows[1].contains("00:40:11")); assertTrue(content.contains("kcal")); assertFalse(content.contains("elapsed_ms")); assertTrue(rows[1].contains("149.0"))
        } } finally { runBlocking { c.repo.deleteSession(s.id) }; instrumentation.uiAutomation.executeShellCommand("rm /sdcard/Download/$name").close() }
    }
}
