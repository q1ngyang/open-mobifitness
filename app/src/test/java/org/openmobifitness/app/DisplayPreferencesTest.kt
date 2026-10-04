package org.openmobifitness.app

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class DisplayPreferencesTest {
    private fun prefs()=RuntimeEnvironment.getApplication().getSharedPreferences("metric-test",Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    @Test fun changingMachineKeepsIndependentChoicesAfterRestart() {
        val p=prefs(); val d=DisplayPreferences(p)
        d.save(DisplayScope.TRAINING,listOf(MetricId.CADENCE,MetricId.RESISTANCE),Machine.ELLIPTICAL)
        d.save(DisplayScope.TRAINING,listOf(MetricId.STROKE_RATE,MetricId.STROKES,MetricId.PACE),Machine.ROWER)
        val restarted=DisplayPreferences(p)
        assertEquals(listOf(MetricId.CADENCE,MetricId.RESISTANCE),restarted.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL))
        assertEquals(listOf(MetricId.STROKE_RATE,MetricId.STROKES,MetricId.PACE),restarted.selected(DisplayScope.TRAINING,Machine.ROWER))
        assertFalse(restarted.selected(DisplayScope.TRAINING,Machine.TREADMILL).contains(MetricId.CADENCE))
    }
    @Test fun legacyCustomizationsAreFilteredInEveryDisplayScope() {
        val p=prefs()
        DisplayScope.entries.forEach { p.edit().putString(it.key,"STROKES,PACE,CADENCE,STEP_RATE").commit() }
        val d=DisplayPreferences(p)
        DisplayScope.entries.forEach { scope ->
            assertEquals(listOf(MetricId.CADENCE),d.selected(scope,Machine.ELLIPTICAL))
            assertEquals(listOf(MetricId.STROKES,MetricId.PACE).take(scope.limit),d.selected(scope,Machine.ROWER))
            assertTrue(d.selected(scope,Machine.TREADMILL).all { it in MetricCatalog.forMachine(Machine.TREADMILL) })
        }
    }
    @Test fun corruptedOrIncompatibleSavedChoicesFallBackToThatMachine() {
        val p=prefs(); p.edit().putString("training_metrics.ELLIPTICAL","BOGUS,STROKES,STRIDE").commit()
        val d=DisplayPreferences(p)
        assertEquals(MetricCatalog.trainingDefaults(Machine.ELLIPTICAL),d.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL))
        assertThrows(IllegalArgumentException::class.java) { d.save(DisplayScope.TRAINING,listOf(MetricId.STROKES),Machine.ELLIPTICAL) }
    }
    @Test fun freeRecordingDefaultsFollowTheSixMetricDesignWithoutReplacingSavedChoices() {
        val p=prefs(); val d=DisplayPreferences(p)
        assertEquals(listOf(MetricId.CADENCE,MetricId.HEART,MetricId.DISTANCE,MetricId.CALORIES,MetricId.POWER,MetricId.SPEED),d.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL,freeRecording=true))
        assertEquals(MetricCatalog.trainingDefaults(Machine.ELLIPTICAL),d.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL))
        val custom=listOf(MetricId.POWER,MetricId.SPEED,MetricId.CADENCE,MetricId.RESISTANCE,MetricId.HEART)
        d.save(DisplayScope.TRAINING,custom,Machine.ELLIPTICAL)
        assertEquals(custom,d.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL,freeRecording=true))
        assertEquals(custom,d.selected(DisplayScope.TRAINING,Machine.ELLIPTICAL,controlOnly=true))
    }
}
