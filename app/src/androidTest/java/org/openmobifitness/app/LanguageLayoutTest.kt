package org.openmobifitness.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.io.File

/** Use the real accessibility tree across Activity recreation; no Compose virtual clock. */
class LanguageLayoutTest {
    private fun awaitCondition(condition: ()->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+30_000
        while(!condition() && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
        assertTrue("Timed out waiting for the workout state",condition())
    }
    @Test fun sixLanguagesAndRecreationKeepTheSameWorkout() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val layout=InstrumentationRegistry.getArguments().getString("layout") ?: "phone"
        val variants=listOf("en" to "Training","zh-Hans" to "运动界面","zh-Hant" to "運動畫面","ja" to "トレーニング","ko" to "운동 화면","de" to "Training")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitCondition { c.state.value.ready }
            scenario.onActivity { c.setDemo(true); c.select(Presets.all[2]); it.startTraining() }
            awaitCondition { (c.state.value.session?.elapsedMs ?: 0)>2500 }
            val id=c.state.value.session!!.id
            scenario.onActivity { c.pauseResume() }
            awaitCondition { c.state.value.paused }
            for((tag,label) in variants) {
                println("Checking layout: $layout $tag")
                scenario.onActivity { it.setLanguage(tag) }
                val requested=java.util.Locale.forLanguageTag(tag)
                awaitCondition {
                    runCatching {
                        var matches=false
                        scenario.onActivity { val locale=it.resources.configuration.locales[0]; matches=locale.language==requested.language && (requested.script.isEmpty() || locale.script==requested.script) }
                        matches
                    }.getOrDefault(false)
                }
                assertTrue("Localized training screen must appear: $tag",device.wait(Until.hasObject(By.text(label)),30_000))
                // Wait for the new Activity, then fetch its localized strings and readings.
                var increase=""; var decrease=""; var minimize=""; var density=1f
                var values=listOf<String>()
                scenario.onActivity {
                    increase=it.getString(R.string.increase); decrease=it.getString(R.string.decrease); minimize=it.getString(R.string.minimize)
                    density=it.resources.displayMetrics.density
                    values=listOf(MetricId.DISTANCE,MetricId.CALORIES).map { metric -> it.reading(metric,c).value }
                }
                for(text in listOf(increase,decrease)) assertNotNull("Visible control: $tag $text",device.wait(Until.findObject(By.desc(text)),15_000))
                assertNotNull("Visible floating-panel action: $tag",device.wait(Until.findObject(By.text(minimize)),15_000))
                for(value in values) {
                    val node=device.wait(Until.findObject(By.text(value)),15_000)
                    assertNotNull("Visible metric: $tag $value",node)
                    assertTrue("Numeric reading must not be clipped: $tag $value",node.visibleBounds.height()>=16*density)
                }
                val image=instrumentation.uiAutomation.takeScreenshot()
                if(image!=null) {
                    val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
                    File(dir,"$layout-$tag.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
                }
                assertEquals(id,c.state.value.session!!.id); assertTrue(c.state.value.paused)
            }
            scenario.onActivity { c.finish() }
            awaitCondition { c.state.value.session==null }
        }
    }
}
