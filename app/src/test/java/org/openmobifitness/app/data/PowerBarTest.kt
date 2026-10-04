package org.openmobifitness.app.data

import org.junit.Assert.*
import org.junit.Test
import org.openmobifitness.core.PlotPoint

class PowerBarTest {
    @Test fun peaksRetainRealTimestampsZeroAndMissingSegments() {
        val samples=listOf(PlotPoint(100,0.0,0),PlotPoint(200,50.0,0),PlotPoint(300,10.0,0),PlotPoint(20100,0.0,1),PlotPoint(20200,20.0,2))
        val result=powerBarPeaks(samples,30000,3)
        assertEquals(listOf(samples[1],samples[3],samples[4]),result)
        assertTrue(result.all { it in samples })
        assertTrue(result.none { it.ms in 1000..20000 })
        assertTrue(powerBarPeaks(emptyList(),0).isEmpty())
    }
    @Test fun tappingABarSelectsItsPeakAndAnEmptyBucketSelectsNothing() {
        val earlyPeak=PlotPoint(100,80.0,0)
        val nextPeak=PlotPoint(10100,40.0,0)
        val sameBinAfterGap=PlotPoint(10500,20.0,1)
        val points=listOf(earlyPeak,nextPeak,sameBinAfterGap)
        assertEquals(earlyPeak,powerBarAtTime(points,30000,9900,3))
        assertEquals(nextPeak,powerBarAtTime(points,30000,10500,3))
        assertNull(powerBarAtTime(points,30000,25000,3))
        assertNull(powerBarAtTime(points,30000,-1,3))
        assertNull(powerBarAtTime(points,30000,30001,3))
    }
}
