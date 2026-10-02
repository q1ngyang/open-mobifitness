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
    @Test fun foregroundNotificationUsesCurrentBrandAndIsVisible() {
        val inst=InstrumentationRegistry.getInstrumentation(); val context=inst.targetContext
        listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS).forEach { inst.uiAutomation.grantRuntimePermission(context.packageName,it) }
        val device=UiDevice.getInstance(inst)
        val controller=(context.applicationContext as OpenMobiApp).controller
        var createdSession: String?=null
        fun awaitState(check: ()->Boolean) {
            val until=android.os.SystemClock.elapsedRealtime()+30000
            while(!check() && android.os.SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertTrue("Expected application/notification state",check())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                awaitState { controller.state.value.ready }
                scenario.onActivity { a ->
                    val c=(a.application as OpenMobiApp).controller
                    c.dismissResult(); c.setDemo(false)
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
                notification.contentIntent.send()
                device.waitForIdle(2000)
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
                manager.activeNotifications.single { it.id==7 }.notification.contentIntent.send()
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
