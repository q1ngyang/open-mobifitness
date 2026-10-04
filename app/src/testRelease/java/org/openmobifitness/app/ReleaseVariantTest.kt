package org.openmobifitness.app

import android.app.Application
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.core.Machine

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ReleaseVariantTest {
    @Test fun releaseContainsNeitherDatasetImplementationNorItsStrings() {
        val app=RuntimeEnvironment.getApplication()
        assertEquals(0,app.resources.getIdentifier("debug_samples_load","string",BuildConfig.APPLICATION_ID))
        listOf("DebugDataset","DebugSamples","DebugSampleStore").forEach { name ->
            try { Class.forName("org.openmobifitness.app.data.$name"); fail("Debug dataset leaked into Release") } catch(_: ClassNotFoundException) { }
        }
    }
    @Test fun releaseRejectsEverySimulatedDevice() {
        assertFalse(BuildConfig.DEBUG)
        assertEquals("org.openmobifitness.app",BuildConfig.APPLICATION_ID)
        val c=Controller(RuntimeEnvironment.getApplication())
        try {
            Machine.entries.forEach { c.setDemo(true,it); assertFalse(c.state.value.demo); assertFalse(c.canStart()) }
        } finally { c.scope.cancel(); c.repo.db.close() }
    }
}
