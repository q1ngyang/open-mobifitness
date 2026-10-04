package org.openmobifitness.core

import org.junit.Test
import org.junit.Assert.*

class ReportMetricsTest {
    @Test fun historicalUnitEvidenceRetainsCompatibleExtraAxesWithoutInventingDeviceSpecificCounts() {
        val stats=Statistics.summarize(listOf(Sample("id",1000,Metrics(inclinePercent=0.0,powerW=130.0,strokes=10))))
        val ellipse=ReportMetrics.descriptors(Session(machine=Machine.ELLIPTICAL),stats,setOf(SeriesMetric.INCLINE,SeriesMetric.LOAD))
        assertEquals(0.0,ellipse.first { it.key=="incline.average" }.value!!,0.0)
        assertFalse(ellipse.any { it.metric==MetricId.LOAD || it.metric==MetricId.STROKES })
        val unknown=ReportMetrics.descriptors(Session(machine=Machine.UNKNOWN),stats,setOf(SeriesMetric.POWER,SeriesMetric.CADENCE))
        assertEquals(130.0,unknown.first { it.key=="power.average" }.value!!,0.0)
        assertFalse(unknown.any { it.metric in setOf(MetricId.STROKES,MetricId.CADENCE) })
    }
    @Test fun validZerosStayVisibleAndIncompatibleFieldsAreAbsent() {
        val stats=Statistics.summarize(listOf(Sample("id",1000,Metrics(cadence=80.0,stepCount=500,strokes=300,inclinePercent=0.0,powerW=0.0,loadKg=12.5,jumpCount=100,repetitions=20))))
        val ellipse=ReportMetrics.descriptors(Session(machine=Machine.ELLIPTICAL),stats)
        assertFalse(ellipse.any { it.key=="steps" || it.metric in setOf(MetricId.STROKES,MetricId.INCLINE,MetricId.LOAD) })
        assertEquals(0.0,ellipse.first { it.key=="power.average" }.value!!,0.0)
        val treadmill=ReportMetrics.descriptors(Session(machine=Machine.TREADMILL),stats)
        assertEquals(0.0,treadmill.first { it.key=="incline.average" }.value!!,0.0)
        assertEquals(500.0,treadmill.first { it.key=="steps" }.value!!,0.0)
        val jumps=ReportMetrics.descriptors(Session(machine=Machine.JUMP_ROPE),stats)
        assertFalse(jumps.any { it.metric in setOf(MetricId.DISTANCE,MetricId.POWER,MetricId.STROKES) })
        assertEquals(100.0,jumps.first { it.metric==MetricId.JUMPS }.value!!,0.0)
        val dumbbell=ReportMetrics.descriptors(Session(machine=Machine.DUMBBELL),stats)
        assertEquals(20.0,dumbbell.first { it.metric==MetricId.REPETITIONS }.value!!,0.0)
        assertEquals(12.5,dumbbell.first { it.key=="load.average" }.value!!,0.0)
        assertEquals(listOf(SeriesMetric.HEART),ReportMetrics.series(Machine.UNKNOWN))
    }
    @Test fun paceUsesMatchedTimeAndDistanceAndFastestIsTheMinimum() {
        val stats=Statistics.summarize(listOf(Sample("id",1000,Metrics(speedMps=1.0)),Sample("id",2000,Metrics(speedMps=3.0)),Sample("id",3000,Metrics(speedMps=0.0))))
        val pace=stats.metrics.getValue(SeriesMetric.PACE)
        assertEquals(375.0,pace.average!!,0.0001) // 3 seconds over 4 meters, not an average of reciprocal speeds.
        assertEquals(500.0/3,pace.minimum!!,0.0001)
        assertEquals(3000L,pace.coverageMs)
    }
    @Test fun explicitMissingSamplesSplitTheCurveAndStreamingKeepsExtrema() {
        val accumulator=Statistics.Accumulator()
        repeat(100000) { i -> accumulator.add(Sample("id",i*1000L,Metrics(heartBpm=if(i==10) null else if(i==12345) 250 else 120))) }
        val heart=accumulator.finish().metrics.getValue(SeriesMetric.HEART)
        assertEquals(250.0,heart.maximum!!,0.0); assertTrue(heart.points.size<=240)
        assertTrue(heart.points.any { it.value==250.0 })
        val short=Statistics.summarize(listOf(Sample("id",1000,Metrics(heartBpm=120)),Sample("id",2000,Metrics()),Sample("id",3000,Metrics(heartBpm=125))))
        assertNotEquals(short.metrics.getValue(SeriesMetric.HEART).points[0].segment,short.metrics.getValue(SeriesMetric.HEART).points[1].segment)
    }
}
