package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StatisticsTest {
    @Test fun officialResistanceUsesMaximumAndRoundsToSupportedLevels() {
        val range=ResistanceRange(1.0,24.0)
        assertEquals(4,range.percentage(1.0)); assertEquals(50,range.percentage(12.0)); assertEquals(100,range.percentage(24.0))
        assertEquals(12.0,range.percent(50),0.0); assertEquals(1.0,range.percent(0),0.0)
        for(max in listOf(8,16,24,32)) for(level in 1..max) {
            val r=ResistanceRange(1.0,max.toDouble())
            assertEquals(level.toDouble(),r.percent(r.percentage(level.toDouble())),0.0)
        }
        assertEquals(21.0,ResistanceRange(1.0,24.0,5.0).percent(100),0.0)
        val ftms=ResistanceRange(0.0,10.0,.5)
        assertEquals(2.5,ftms.percent(25),0.0)
        assertEquals(0,ResistanceRange(0.0,0.0).percentage(0.0))
        assertEquals(100,ResistanceRange(1.0,1.0).percentage(1.0))
        Presets.all.flatMap { it.steps }.forEach { s -> val target=range.percent(s.resistancePercent!!); assertTrue(range.contains(target)); assertEquals(target,range.percent(range.percentage(target)),0.0) }
    }
    @Test fun weightedAveragesPreserveMissingDataAndBoundGapExtrapolation() {
        val s=listOf(Sample("id",1000,Metrics(heartBpm=100,powerW=0.0)),Sample("id",3000,Metrics(heartBpm=160)),Sample("id",4000,Metrics(heartBpm=0)),Sample("id",14000,Metrics(heartBpm=140)))
        val stats=Statistics.summarize(s); val heart=stats.metrics.getValue(SeriesMetric.HEART)
        assertEquals(8000L,heart.coverageMs); assertEquals(140.0,heart.average!!,.0001)
        assertEquals(160.0,heart.maximum!!,0.0); assertEquals(100.0,heart.minimum!!,0.0)
        assertNull(stats.metrics.getValue(SeriesMetric.CADENCE).average)
        assertEquals(0.0,stats.metrics.getValue(SeriesMetric.POWER).average!!,0.0)
    }
    @Test fun downsamplingRetainsExtremaAndEndpointsWithinBudget() {
        val points=(0..10000).map { PlotPoint(it*1000L,if(it==3456) 999.0 else (it%100).toDouble()) }
        val reduced=Statistics.decimate(points)
        assertTrue(reduced.size<=240); assertEquals(points.first(),reduced.first()); assertEquals(points.last(),reduced.last()); assertTrue(reduced.any { it.value==999.0 }); assertEquals(reduced.sortedBy { it.ms },reduced)
    }
    @Test fun periodsUseLocalCalendarBoundariesIncludingDstAndLeapYear() {
        val zone=ZoneId.of("America/New_York")
        val march=PeriodWindow.of(HistoryPeriod.MONTH,LocalDate.of(2024,3,17),zone)
        assertEquals(31*24*3600000L-3600000,march.until-march.from)
        val leap=PeriodWindow.of(HistoryPeriod.MONTH,LocalDate.of(2024,2,29),ZoneOffset.UTC)
        assertEquals(29*86400000L,leap.until-leap.from)
        val week=PeriodWindow.of(HistoryPeriod.WEEK,LocalDate.of(2024,3,10),zone)
        assertEquals(DayOfWeek.MONDAY,Instant.ofEpochMilli(week.from).atZone(zone).dayOfWeek)
        assertEquals(7*86400000L-3600000,week.until-week.from)
    }
    @Test fun planDurationAndPeakFiltersHaveNoOverlappingBoundaries() {
        fun w(minutes: Double,p: Int)=Workout(title="test",steps=listOf(Step(target=minutes*60,resistancePercent=p)))
        assertTrue(PlanFilter.matches(w(20.0,25),PlanDuration.MEDIUM,PlanIntensity.LOW))
        assertFalse(PlanFilter.matches(w(20.0,25),PlanDuration.SHORT,PlanIntensity.ALL))
        assertTrue(PlanFilter.matches(w(40.0,50),PlanDuration.MEDIUM,PlanIntensity.MODERATE))
        assertTrue(PlanFilter.matches(w(41.0,51),PlanDuration.LONG,PlanIntensity.HIGH))
        assertFalse(PlanFilter.matches(Workout(title="distance",steps=listOf(Step(Condition.DISTANCE,500.0,20))),PlanDuration.SHORT,PlanIntensity.ALL))
    }
    @Test fun archiveAndWorkoutMetadataRoundTripWithSafeText() {
        val session=Session(status="completed",workoutId="custom",workoutTitle="=danger,\"quoted\"",archived=true)
        assertEquals(session,Exchange.parse(Exchange.sessions(listOf(session))).sessions.single())
        val old="schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status\n1,${session.id},2026-09-01T00:00:00Z,,UTC,'old,ELLIPTICAL,V1,1000,2,false,stopped\n"
        val restored=Exchange.parse(old).sessions.single(); assertFalse(restored.archived); assertEquals("",restored.workoutTitle)
    }
}
