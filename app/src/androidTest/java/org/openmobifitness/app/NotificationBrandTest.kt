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
import org.openmobifitness.core.Protocol
import java.io.File

class NotificationBrandTest {
    @Test fun foregroundNotificationUsesCurrentBrandAndIsVisible() {
        val inst=InstrumentationRegistry.getInstrumentation(); val context=inst.targetContext
        listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS).forEach { inst.uiAutomation.grantRuntimePermission(context.packageName,it) }
        val device=UiDevice.getInstance(inst)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { a ->
                    val c=(a.application as OpenMobiApp).controller
                    c.dismissResult(); c.setDemo(false)
                    // Display fixture only. No Bluetooth GATT connection or packets are created.
                    c.ble.state.value=LinkState(phase="ready",protocol=Protocol.V1)
                    a.ensureService()
                }
                val manager=context.getSystemService(NotificationManager::class.java)
                val deadline=android.os.SystemClock.elapsedRealtime()+20000
                while(manager.activeNotifications.none { it.id==7 } && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(200)
                val notification=manager.activeNotifications.single { it.id==7 }.notification
                assertEquals(R.drawable.ic_notification,notification.smallIcon.resId)
                assertNotNull(notification.getLargeIcon())
                assertNotNull(notification.contentIntent)
                assertTrue(device.openNotification())
                val title=notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
                assertNotNull("Notification shade must actually be visible before capturing it",device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(title)),30000))
                device.waitForIdle(2000)
                val bitmap=inst.uiAutomation.takeScreenshot()!!
                val dir=File(context.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
                File(dir,"alpha7-notification.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                device.pressBack()
            } finally { scenario.onActivity { a -> (a.application as OpenMobiApp).controller.ble.disconnect(); a.stopService(Intent(a,WorkoutService::class.java)) } }
        }
    }
}
