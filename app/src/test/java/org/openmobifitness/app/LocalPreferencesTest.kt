package org.openmobifitness.app

import android.app.Application
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.core.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LocalPreferencesTest {
    private val prefs get()=RuntimeEnvironment.getApplication().getSharedPreferences("preferences",0)
    @Before fun setup() { prefs.edit().clear().commit() }
    @Test fun savedDevicesMigrateLastDeviceAndPreserveNotesAcrossReconnects() {
        prefs.edit().putString("last_address","AA").putString("last_name","MB-EP").commit()
        val p=LocalPreferences(prefs)
        assertEquals("AA",p.devices.value.single().address)
        p.rename("AA","Living room")
        p.remember(SavedDevice("AA","MB-EP",Machine.ELLIPTICAL))
        p.remember(SavedDevice("HR","Pulse",Machine.HEART,true))
        assertEquals("Living room",LocalPreferences(prefs).devices.value.first().note)
        p.remove("AA")
        assertEquals(listOf("HR"),LocalPreferences(prefs).devices.value.map { it.address })
        assertFalse(prefs.contains("last_address"))
    }
    @Test fun presetsAreIsolatedByDeviceAndRevalidatedWithNewCapabilities() {
        val p=LocalPreferences(prefs); val range=ResistanceRange(1.0,24.0)
        p.savePresets("AA",Machine.ELLIPTICAL,range,listOf(4.0,8.0,12.0,16.0))
        assertTrue(p.presets("BB",Machine.ELLIPTICAL,range).isEmpty())
        assertTrue(p.presets("AA",Machine.BIKE,range).isEmpty())
        assertEquals(listOf(4.0,8.0),LocalPreferences(prefs).presets("AA",Machine.ELLIPTICAL,ResistanceRange(0.0,10.0,2.0)))
        assertThrows(IllegalArgumentException::class.java) { p.savePresets("AA",Machine.ELLIPTICAL,range,listOf(4.5)) }
    }
    @Test fun hintsStayDisabledOnOldPreferenceMigrationAndPositionsAreSeparate() {
        prefs.edit().putInt("target_cadence",27).commit()
        val p=LocalPreferences(prefs)
        assertFalse(p.hints.value.heart.enabled); assertTrue(p.hints.value.frequency.isEmpty())
        p.saveHints(HintPreferences(mapOf(Machine.ROWER to PersonalRange(true,20.0,null)),PersonalRange(true,null,160.0),true,false))
        assertEquals(p.hints.value,LocalPreferences(prefs).hints.value)
        assertEquals(27,prefs.getInt("target_cadence",0))
        p.position(false,OverlayPosition(.85f,.5f)); p.position(true,OverlayPosition(.2f,.8f))
        assertEquals(OverlayPosition(.85f,.5f),LocalPreferences(prefs).position(false))
        assertEquals(OverlayPosition(.2f,.8f),LocalPreferences(prefs).position(true))
        p.resetPositions(); assertNull(p.position(true)); assertNull(p.position(false))
    }
}
