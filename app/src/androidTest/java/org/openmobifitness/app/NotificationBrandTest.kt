package org.openmobifitness.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.Machine
import org.openmobifitness.core.Protocol
import kotlinx.coroutines.runBlocking
import java.io.File

class NotificationBrandTest {
    @org.junit.Before fun nativeUpdatesDoNotRequireTenSecondsOfIdle() {
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout=100
    }
    @Test fun controlOnlyAndRecordingNotificationActionsFollowTheLiveState() {
        val inst=InstrumentationRegistry.getInstrumentation(); val context=inst.targetContext
        if(android.os.Build.VERSION.SDK_INT>=31) listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT).forEach { inst.uiAutomation.grantRuntimePermission(context.packageName,it) }
        if(android.os.Build.VERSION.SDK_INT>=33) inst.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val device=UiDevice.getInstance(inst)
        val c=(context.applicationContext as OpenMobiApp).controller
        val manager=context.getSystemService(NotificationManager::class.java)
        val dir=File(context.getExternalFilesDir(null),"v020-notifications").apply { mkdirs() }
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        fun text(id: Int)=context.localized().getString(id)
        fun await(check: ()->Boolean) {
            val end=android.os.SystemClock.elapsedRealtime()+30000
            while(!check() && android.os.SystemClock.elapsedRealtime()<end) Thread.sleep(100)
            assertTrue(check())
        }
        fun current()=manager.activeNotifications.single { it.id==7 }.notification
        fun actionSelector(label: String)=androidx.test.uiautomator.By.pkg("com.android.systemui").text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(label),java.util.regex.Pattern.CASE_INSENSITIVE))
        fun closeShade(title: Int) {
            device.pressBack()
            assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.pkg("com.android.systemui").text(text(title))),15000))
            device.waitForIdle(1000)
            // Android 10 can accept the next expansion while its closing animation
            // is still running, then finish closing over that new request.
            Thread.sleep(600)
        }
        fun openExpanded(title: Int) {
            assertTrue(device.openNotification())
            val titleNode=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.pkg("com.android.systemui").text(text(title))),15000)
            assertNotNull("SystemUI must show the notification, not a matching title behind the shade",titleNode)
            if(!device.hasObject(actionSelector(text(R.string.notification_open)))) {
                var row=titleNode
                var expand: androidx.test.uiautomator.UiObject2?=null
                while(row!=null && expand==null) {
                    expand=row.findObject(androidx.test.uiautomator.By.res("android","expand_button"))
                        ?: row.findObject(androidx.test.uiautomator.By.res("com.android.systemui","expand_button"))
                    row=row.parent
                }
                assertNotNull("Notification expansion control",expand)
                val bounds=expand!!.visibleBounds
                device.click(bounds.centerX(),bounds.centerY())
            }
            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(actionSelector(text(R.string.notification_open))),15000))
            device.waitForIdle(1000)
            Thread.sleep(600) // Let the shade/expansion finish moving before real touch input.
        }
        fun clickAction(title: Int,action: Int) {
            openExpanded(title)
            // Use current SystemUI bounds for a real touch, including after a
            // state change or expansion that replaced the accessibility node.
            var bounds: android.graphics.Rect?=null
            await {
                bounds=runCatching { device.findObject(actionSelector(text(action)))?.visibleBounds }.getOrNull()
                bounds?.isEmpty==false
            }
            val target=bounds!!
            File(dir,"touches.txt").appendText("${text(action)} bounds=$target paused=${c.state.value.paused} phase=${c.ble.state.value.phase}\n")
            device.click(target.centerX(),target.centerY())
            Thread.sleep(500)
            if(device.hasObject(actionSelector(text(R.string.notification_open)))) closeShade(title)
        }
        fun capture(title: Int,name: String) {
            await { manager.activeNotifications.any { it.id==7 && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()==text(title) } }
            openExpanded(title)
            device.waitForIdle(1000); Thread.sleep(350)
            val bitmap=inst.uiAutomation.takeScreenshot()!!
            File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            closeShade(title)
        }
        val ready=LinkState(name="MB-EP · notification fixture",address="00:00:00:00:02:34",phase="ready",protocol=Protocol.V1,machine=Machine.ELLIPTICAL,writable=true,range=org.openmobifitness.core.ResistanceRange(1.0,24.0))
        var id: String?=null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getArguments().getString("language")?.let { language ->
                scenario.onActivity { if(it.currentLanguage()!=language) it.setLanguage(language) }
            }
            try {
                await { c.state.value.ready }
                val before=runBlocking { c.repo.db.records().sessionCount() }
                scenario.onActivity { c.dismissResult(); c.exitControl(); c.setDemo(false); c.display.automaticFloating(false); c.ble.state.value=ready; it.startControl() }
                await { c.state.value.controlOnly }
                capture(R.string.control_only,"01-control-only")
                assertNull(c.state.value.session)
                assertEquals(before,runBlocking { c.repo.db.records().sessionCount() })
                assertEquals(listOf(text(R.string.disconnect),text(R.string.notification_open)),current().actions.map { it.title.toString() })
                clickAction(R.string.control_only,R.string.notification_open)
                scenario.onActivity { assertTrue(it.focusTraining.value); it.startTraining() }
                await { c.state.value.session!=null }; id=c.state.value.session!!.id
                capture(R.string.notification_active,"02-recording")
                assertTrue(current().extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
                val activePosted=manager.activeNotifications.single { it.id==7 }.postTime
                val activeWhen=current().`when`
                Thread.sleep(2200)
                assertEquals("SystemUI advances the timer without rebinding action buttons",activePosted,manager.activeNotifications.single { it.id==7 }.postTime)
                assertEquals(activeWhen,current().`when`)
                clickAction(R.string.notification_active,R.string.pause_short)
                await { c.state.value.paused }
                capture(R.string.notification_paused,"03-paused")
                assertFalse(current().extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
                val pausedWhen=current().`when`
                Thread.sleep(1200)
                assertEquals("A stationary paused notification keeps its timestamp and native controls",pausedWhen,current().`when`)
                scenario.onActivity { c.disconnectEquipment() }
                capture(R.string.notification_disconnected,"04-disconnected")
                assertTrue(current().actions.any { it.title.toString()==text(R.string.reconnect) })
                scenario.onActivity { c.ble.state.value=ready }
                capture(R.string.notification_paused,"05-reconnected-paused")
                assertTrue(c.state.value.paused)
                clickAction(R.string.notification_paused,R.string.notification_resume)
                await { !c.state.value.paused }
                scenario.onActivity { c.finish() }
                await { c.state.value.session==null }
                capture(R.string.control_only,"06-return-control")
                clickAction(R.string.control_only,R.string.disconnect)
                await { !c.state.value.inUse && !c.serviceStarted }
            } catch(error: Throwable) {
                val bitmap=inst.uiAutomation.takeScreenshot()!!
                File(dir,"failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                device.dumpWindowHierarchy(File(dir,"failure-hierarchy.xml"))
                File(dir,"failure-state.txt").writeText("paused=${c.state.value.paused} controlOnly=${c.state.value.controlOnly} error=${c.state.value.error} phase=${c.ble.state.value.phase} machine=${c.displayMachine()} sessionMachine=${c.state.value.session?.machine} service=${c.serviceStarted}")
                throw error
            } finally {
                scenario.onActivity { if(c.state.value.session!=null) c.finish() }
                await { c.state.value.session==null }
                scenario.onActivity { c.dismissResult(); c.exitControl(); it.stopService(Intent(it,WorkoutService::class.java)) }
                id?.let { runBlocking { c.repo.deleteSession(it) } }
            }
        }
    }
    @Test fun foregroundNotificationUsesCurrentBrandAndIsVisible() {
        val inst=InstrumentationRegistry.getInstrumentation(); val context=inst.targetContext
        if(android.os.Build.VERSION.SDK_INT>=31) listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT).forEach { inst.uiAutomation.grantRuntimePermission(context.packageName,it) }
        if(android.os.Build.VERSION.SDK_INT>=33) inst.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val device=UiDevice.getInstance(inst)
        val controller=(context.applicationContext as OpenMobiApp).controller
        var createdSession: String?=null
        fun awaitState(check: ()->Boolean) {
            val until=android.os.SystemClock.elapsedRealtime()+30000
            while(!check() && android.os.SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertTrue("Expected application/notification state",check())
        }
        fun openFromNotification(title: String) {
            assertTrue(device.openNotification())
            val selector=androidx.test.uiautomator.By.pkg("com.android.systemui").text(title)
            var bounds: android.graphics.Rect?=null
            awaitState {
                bounds=runCatching { device.findObject(selector)?.visibleBounds }.getOrNull()
                bounds?.isEmpty==false
            }
            device.click(bounds!!.centerX(),bounds!!.centerY())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getArguments().getString("language")?.let { language ->
                scenario.onActivity { if(it.currentLanguage()!=language) it.setLanguage(language) }
            }
            try {
                awaitState { controller.state.value.ready }
                scenario.onActivity { a ->
                    val c=(a.application as OpenMobiApp).controller
                    c.dismissResult(); c.exitControl(); c.setDemo(false)
                    // Display fixture only. No Bluetooth GATT connection or packets are created.
                    c.ble.state.value=LinkState(name="MB-EP · UI fixture",phase="ready",protocol=Protocol.V1,machine=Machine.ELLIPTICAL)
                    a.ensureService()
                }
                val manager=context.getSystemService(NotificationManager::class.java)
                val deadline=android.os.SystemClock.elapsedRealtime()+20000
                while(manager.activeNotifications.none { it.id==7 } && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(200)
                val notification=manager.activeNotifications.single { it.id==7 }.notification
                assertEquals(R.drawable.ic_openmobi_status,notification.smallIcon.resId)
                assertNotNull(notification.getLargeIcon())
                assertNotNull(notification.contentIntent)
                assertEquals(context.localized().getString(R.string.notification_ready),notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
                val service=context.packageManager.getServiceInfo(android.content.ComponentName(context,WorkoutService::class.java),0)
                assertEquals(R.mipmap.ic_openmobi,service.icon)
                assertEquals("OpenMOBI",service.loadLabel(context.packageManager).toString())
                device.waitForIdle(2000)
                assertTrue(device.openNotification())
                val title=notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
                assertNotNull("Notification shade must actually be visible before capturing it",device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(title)),30000))
                device.waitForIdle(2000)
                val bitmap=inst.uiAutomation.takeScreenshot()!!
                val dir=File(context.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
                File(dir,"v011-notification.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                device.pressBack()
                // SystemUI can defer foreground-service icons. Capture after the
                // notification has actually reached the shade, not just the manager.
                assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text(title)),10000))
                device.waitForIdle(2000); Thread.sleep(1000)
                val statusImage=inst.uiAutomation.takeScreenshot()!!
                File(dir,"v011-status-bar.png").outputStream().use { statusImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };statusImage.recycle()
                openFromNotification(title)
                awaitState { !controller.state.value.inUse && scenario.state==androidx.lifecycle.Lifecycle.State.RESUMED }
                Thread.sleep(500)
                scenario.onActivity { a -> assertEquals(2,a.page.value); assertFalse(a.focusTraining.value); a.page.value=0 }
                scenario.recreate()
                scenario.onActivity { a -> assertEquals(0,a.page.value) }
                scenario.onActivity { a ->
                    assertNull(controller.state.value.session)
                    controller.ble.state.value=LinkState(name="MB-EP · UI fixture",phase="ready",protocol=Protocol.V1,machine=Machine.ELLIPTICAL)
                    controller.select(null); a.startTraining()
                }
                awaitState { controller.state.value.session!=null }
                createdSession=controller.state.value.session!!.id
                awaitState { manager.activeNotifications.any { it.id==7 && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()==context.localized().getString(R.string.notification_active) } }
                device.pressHome()
                openFromNotification(context.localized().getString(R.string.notification_active))
                device.waitForIdle(2000)
                scenario.onActivity { a -> assertTrue(a.focusTraining.value); assertEquals(0,a.page.value); controller.pauseResume() }
                awaitState { manager.activeNotifications.any { it.id==7 && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()==context.localized().getString(R.string.notification_paused) } }
            } finally {
                scenario.onActivity { a -> if(createdSession!=null && controller.state.value.session!=null) controller.finish(); controller.ble.disconnect(); a.stopService(Intent(a,WorkoutService::class.java)) }
                if(createdSession!=null) {
                    awaitState { controller.state.value.session==null }
                    scenario.onActivity { controller.dismissResult() }
                    runBlocking { controller.repo.deleteSession(createdSession!!) }
                }
            }
        }
    }
}
