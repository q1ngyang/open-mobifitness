package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class MetricCatalogTest {
    @Test fun pickerNeverOffersMetricsFromAnotherMachine() {
        val ellip=MetricCatalog.forMachine(Machine.ELLIPTICAL)
        assertTrue(ellip.containsAll(listOf(MetricId.CADENCE,MetricId.RESISTANCE,MetricId.POWER)))
        assertTrue(ellip.intersect(setOf(MetricId.STROKES,MetricId.STROKE_RATE,MetricId.PACE,MetricId.STEP_RATE,MetricId.STRIDE,MetricId.INCLINE)).isEmpty())
        val rowing=MetricCatalog.forMachine(Machine.ROWER)
        assertTrue(rowing.containsAll(listOf(MetricId.STROKES,MetricId.STROKE_RATE,MetricId.PACE)))
        assertTrue(rowing.intersect(setOf(MetricId.CADENCE,MetricId.INCLINE,MetricId.STEP_RATE,MetricId.STRIDE)).isEmpty())
        assertFalse(MetricCatalog.forMachine(Machine.TREADMILL).contains(MetricId.RESISTANCE))
        Machine.entries.forEach { assertTrue(MetricCatalog.forMachine(it).containsAll(MetricCatalog.trainingDefaults(it))) }
    }
}
