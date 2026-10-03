package org.openmobifitness.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.*
import java.io.File

/** Foreground-service/recording soak with synthetic V1 readings; not a radio test.
 * Explicit soak_ms enables it, keeping routine instrumentation runs bounded. */
class V020BackgroundTest {
    @Test fun backgroundVideoScreenOffReconnectAndExit() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val duration=InstrumentationRegistry.getArguments().getString("soak_ms")?.toLongOrNull()
        Assume.assumeTrue("Run explicitly with soak_ms for the timed background check",duration!=null)
        val c=(instrumentation.targetContext.applicationContext as OpenMobiApp).controller
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        val root=File(instrumentation.targetContext.getExternalFilesDir(null),"v020-soak").apply { mkdirs() }
        fun event(text: String) { File(root,"journal.txt").appendText("${SystemClock.elapsedRealtime()} $text\n") }
        fun screen(name: String) { Thread.sleep(250); instrumentation.uiAutomation.takeScreenshot()?.let { b -> File(root,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle() } }
        fun main(block: ()->Unit)=instrumentation.runOnMainSync(block)
        val startReady=SystemClock.elapsedRealtime()
        while(!c.state.value.ready && SystemClock.elapsedRealtime()-startReady<30000) Thread.sleep(100)
        assertTrue(c.state.value.ready)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val initial=SystemClock.elapsedRealtime()
        fun sensor(phase: String="ready") {
            val now=SystemClock.elapsedRealtime()
            c.ble.state.value=LinkState(name="V1 · synthetic background fixture",address="00:00:00:00:02:29",phase=phase,machine=Machine.ELLIPTICAL,protocol=Protocol.V1,range=ResistanceRange(1.0,24.0),writable=true,
                metrics=Metrics(cadence=60.0,resistance=12.0,speedMps=1.5,distanceM=(now-initial)*.0015,heartBpm=128,powerW=235.0),motionAt=now,heartAt=now)
        }
        main { c.dismissResult(); c.setDemo(false); c.display.floating(true); c.display.automaticFloating(true); sensor() }
        scenario.onActivity { it.startControl() }
        var until=SystemClock.elapsedRealtime()+30000
        while(!c.state.value.controlOnly && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
        assertTrue(c.state.value.controlOnly); assertNull(c.state.value.session)
        scenario.onActivity { it.startTraining() }
        until=SystemClock.elapsedRealtime()+30000
        while(c.state.value.session==null && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
        val id=c.state.value.session!!.id
        event("record-start id=$id; protocol=V1 synthetic state; no GATT attached; command count=0")
        device.pressHome(); screen("01-launcher")
        val started=SystemClock.elapsedRealtime(); var phase=0; var disconnected=false; var lastReport=0L
        try {
            while((c.state.value.session?.elapsedMs ?: 0)<duration!!) {
                val elapsed=SystemClock.elapsedRealtime()-started
                assertTrue("Recording must make progress",elapsed<duration*3/2+90000)
                main { sensor(if(disconnected) "disconnected" else "ready") }
                when {
                    phase==0 && elapsed>=duration/12 -> {
                        val small=device.wait(Until.findObject(By.desc(instrumentation.targetContext.getString(R.string.expand_panel))),10000)
                        assertNotNull(small); small!!.click()
                        val expanded=device.wait(Until.findObject(By.desc(instrumentation.targetContext.getString(R.string.collapse_panel))),15000)
                        assertNotNull(expanded); screen("02-expanded-on-launcher")
                        expanded!!.click()
                        assertTrue(device.wait(Until.hasObject(By.desc(instrumentation.targetContext.getString(R.string.expand_panel))),15000))
                        event("overlay expanded and collapsed; both UI states observed"); phase++
                    }
                    phase==1 && elapsed>=duration/6 -> {
                        // Always recreate the video task: a previous test's stopped
                        // SurfaceView can retain its accessibility label while black.
                        val launched=device.executeShellCommand("am start -W -f 0x10008000 -n org.openmobifitness.app.debug.test/org.openmobifitness.app.BackgroundVideoActivity")
                        event("video launch: $launched")
                        assertFalse(launched.contains("Error"))
                        assertTrue(device.wait(Until.hasObject(By.desc("Local background test video playing")),15000))
                        event("local video activity opened"); screen("03-local-video"); phase++
                    }
                    phase==2 && elapsed>=duration/3 -> { device.sleep(); event("screen-off"); phase++ }
                    phase==3 && elapsed>=duration/2 -> { device.wakeUp(); device.pressMenu(); event("screen-on"); screen("04-wake"); phase++ }
                    phase==4 && elapsed>=duration*2/3 -> { disconnected=true; main { c.disconnectEquipment() }; event("synthetic disconnect"); phase++ }
                    phase==5 && elapsed>=duration*2/3+5000 -> {
                        assertTrue(c.state.value.paused); disconnected=false; main { sensor() }; event("ready again; explicit resume required"); phase++
                    }
                    phase==6 && elapsed>=duration*2/3+8000 -> {
                        assertTrue(c.state.value.paused)
                        scenario.onActivity { it.page.value=0; it.focusTraining.value=true; it.startActivity(Intent(it,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                        main { c.pauseResume() }; event("explicit resume"); device.pressHome(); phase++
                    }
                }
                if(elapsed-lastReport>=60000) { lastReport=elapsed; event("checkpoint elapsed=${c.state.value.session?.elapsedMs} paused=${c.state.value.paused} service=${c.serviceStarted}"); File(root,"power.txt").writeText(device.executeShellCommand("dumpsys power")) }
                assertTrue("Foreground service must remain alive",c.serviceStarted)
                Thread.sleep(1000)
            }
            main { c.finish() }
            until=SystemClock.elapsedRealtime()+30000
            while(c.state.value.session!=null && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            val record=runBlocking { c.repo.history.session(id) }!!
            assertTrue(record.elapsedMs>=duration)
            val archive=runBlocking { c.repo.archive(id) }
            val gaps=archive.samples.zipWithNext().map { it.second.elapsedMs-it.first.elapsedMs }
            event("saved elapsed=${record.elapsedMs} samples=${archive.samples.size} max_gap_ms=${gaps.maxOrNull()} transport_commands=0 (no GATT fixture)")
            assertTrue(archive.samples.size>=duration/1500)
            assertTrue(archive.samples.zipWithNext().all { it.first.elapsedMs<it.second.elapsedMs })
            main { c.dismissResult(); c.exitControl(); instrumentation.targetContext.stopService(Intent(instrumentation.targetContext,WorkoutService::class.java)) }
            until=SystemClock.elapsedRealtime()+15000
            while(c.serviceStarted && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertFalse(c.serviceStarted); assertFalse(c.state.value.inUse)
            File(root,"power-after-exit.txt").writeText(device.executeShellCommand("dumpsys power"))
            event("PASS; disconnected, exited, service stopped"); screen("05-exited")
        } finally {
            main { if(c.state.value.session!=null) c.finish() }
            until=SystemClock.elapsedRealtime()+30000
            while(c.state.value.session!=null && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            main { c.dismissResult(); c.exitControl(); instrumentation.targetContext.stopService(Intent(instrumentation.targetContext,WorkoutService::class.java)) }
            scenario.close()
        }
    }
}

/** Test APK only. Host supplies a local generated video under this APK external files. */
class BackgroundVideoActivity: android.app.Activity() {
    private lateinit var video: android.widget.VideoView
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        video=android.widget.VideoView(this)
        video.contentDescription="Local background test video loading"
        setContentView(video)
        video.setVideoPath(File(getExternalFilesDir(null),"background-test.mp4").path)
        video.setOnPreparedListener { it.isLooping=true; video.start() }
        video.setOnInfoListener { _,what,_ ->
            if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) video.contentDescription="Local background test video playing"
            false
        }
    }
    override fun onResume() { super.onResume(); video.start() }
    override fun onPause() { video.pause(); super.onPause() }
}
