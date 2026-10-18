package org.openmobifitness.app.data

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.BuildConfig
import org.openmobifitness.app.Controller
import org.openmobifitness.app.ble.FoundDevice
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class RecoveryDiagnosticTest {
    @Test fun exportRetainsScanFailureAndConnectionAttemptWithBothClientSummaries()=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        AppLog.initialize(app)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=Controller(app,scope,tickAutomatically=false)
        try {
            withTimeout(5000) { c.state.first { it.ready } }
            val adapter=BluetoothAdapter.getDefaultAdapter()
            shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
            c.ble.scan()
            ReflectionHelpers.getField<ScanCallback>(c.ble,"scanCallback").onScanFailed(6)
            shadowOf(adapter).setState(BluetoothAdapter.STATE_OFF)
            c.ble.connect(FoundDevice("00:11:22:33:44:55","MB-EP-private",-40))
            val report=withTimeout(5000) { AppLog.report(c) }
            assertTrue(report.startsWith("OpenMOBI diagnostic report 5"))
            assertTrue(report.contains("package=${BuildConfig.APPLICATION_ID}"))
            for(part in listOf("role=equipment attempt=1", "role=heart", "scan=ScanTrace", "scan_error:6", "connect_request:", "connection_failed:reason=bluetooth_unavailable", "monoMs=")) assertTrue(part,report.contains(part))
            assertFalse(report.contains("00:11:22:33:44:55"));assertFalse(report.contains("MB-EP-private"))
            assertTrue(report.toByteArray().size<128*1024)
        } finally { c.ble.stopScan();c.ble.disconnect();scope.coroutineContext[Job]!!.cancelAndJoin();c.repo.db.close() }
    }
}
