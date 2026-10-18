package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

/** Product invariants supplement, and intentionally do not replace, the original DEX tables. */
class ProtocolBoundaryTest {
    @Test fun counterSpikesRebaseWithoutRejectingPlausibleBatchedReports() {
        val totals=TelemetryTotals(Metrics(strokes=0,distanceM=0.0))
        fun tick(count: Int,distance: Double)=totals.update(Metrics(strokes=count,distanceM=distance),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        tick(65535,65535.0)
        assertEquals(0,totals.strokes);assertEquals(0.0,totals.distanceM!!,0.0)
        tick(0,0.0);tick(1,10.0)
        assertEquals(1,totals.strokes);assertEquals(10.0,totals.distanceM!!,0.0)
        repeat(10) { tick(1,10.0) }
        tick(21,510.0)
        assertEquals(21,totals.strokes);assertEquals(510.0,totals.distanceM!!,0.0)
    }
    @Test fun foldFeedbackRequiresItsDeclaredOneBytePayload() {
        val s=MobiSession(Protocol.V2,"rower")
        s.receive("8901",byteArrayOf(4))
        s.receive("8902",byteArrayOf(3,0,1))
        assertNull(s.details.foldedState)
        s.receive("8902",byteArrayOf(3,1,1))
        assertEquals(1,s.details.foldedState)
        s.receive("8902",byteArrayOf(3,2,0))
        assertEquals(1,s.details.foldedState)
    }
    @Test fun capabilitiesOwnZeroAndUnsignedResistanceAcrossFamilies() {
        for(kind in listOf(9,10,11)) for(mode in listOf(1,3,4,5)) for(minimum in listOf(0,1)) {
            val s=MobiSession(Protocol.V2,"MOBI")
            s.receive("8802",byteArrayOf(kind.toByte(),18)); s.receive("8806",byteArrayOf(mode.toByte(),minimum.toByte(),255.toByte()))
            for(level in listOf(0,1,24,127,128,254,255)) {
                val r=s.receive("8812",byteArrayOf(level.toByte()))?.resistance
                if(mode in setOf(1,3,5) && level>=minimum) assertEquals(level.toDouble(),r!!,0.0) else assertNull(r)
                val command=s.resistanceCommand(level)
                if(mode in setOf(4,5) && level>=minimum) assertArrayEquals(byteArrayOf(2,level.toString().length.toByte(),level.toByte()),command) else assertNull(command)
            }
        }
    }
    @Test fun declaredCounterWidthsRecoverWrapWithoutTurningResetsIntoHugeWorkouts() {
        val s=MobiSession(Protocol.V2,"rower")
        s.receive("8802",byteArrayOf(9,3)); s.receive("8801",byteArrayOf(2))
        fun frame(count: Int,duration: Int)=byteArrayOf(30,0,30,(duration ushr 8).toByte(),duration.toByte(),(count ushr 8).toByte(),count.toByte(),0,0,0,100,(count ushr 8).toByte(),count.toByte())
        val totals=TelemetryTotals(s.receive("8813",frame(65534,100))!!)
        fun update(count: Int,time: Int) { totals.update(s.receive("8813",frame(count,time))!!,1000,true,Machine.ROWER,Protocol.V2,70.0,5.0) }
        update(1,101); update(2,102)
        assertEquals(4,totals.strokes); assertEquals(4.0,totals.distanceM!!,0.0)
        update(0,0); update(2,1)
        assertEquals(6,totals.strokes)
        totals.rebase(); update(1000,100); assertEquals(6,totals.strokes)
        val untyped=TelemetryTotals(Metrics(strokes=65534))
        untyped.update(Metrics(strokes=1),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        assertEquals(0,untyped.strokes)
    }
    @Test fun invalidMagnetMetadataClearsCachedCadenceAndPower() {
        val s=MobiSession(Protocol.V2,"bike")
        s.receive("8802",byteArrayOf(10,18)); s.receive("8801",byteArrayOf(1))
        var merged=s.receive("8811",byteArrayOf(3,0xe8.toByte()))!!
        assertEquals(60.0,merged.cadence!!,0.0)
        merged=merged.merge(s.receive("8805",byteArrayOf(0))!!)
        assertNull(merged.cadence);assertNull(merged.powerW)
        assertNull(s.receive("8811",byteArrayOf(3,0xe8.toByte())))
        s.receive("8805",byteArrayOf(2))
        assertEquals(30.0,s.receive("8811",byteArrayOf(3,0xe8.toByte()))!!.cadence!!,0.0)
    }
    @Test fun partialPacketsNeverExtendOtherMeasurementsAndInvalidValuesClearImmediately() {
        var metrics=Metrics(cadence=60.0,speedMps=10.0,powerW=200.0,heartBpm=120)
        var times=MetricTimes().update(metrics,1000)
        val partial=Metrics(cadence=0.0)
        metrics=metrics.merge(partial);times=times.update(partial,6000)
        val fresh=times.fresh(metrics,6000)
        assertEquals(0.0,fresh.cadence!!,0.0);assertNull(fresh.speedMps);assertNull(fresh.powerW);assertEquals(120,fresh.heartBpm)
        val invalid=Protocols.ftmsData("2ad2",byteArrayOf(5,0,-1,-1))!!
        assertNull(metrics.merge(invalid).cadence)
        assertNull(times.fresh(metrics,11000).heartBpm)
    }
    @Test fun stopAndExpiredSignalsClearDerivedStride() {
        val moving=Metrics(speedMps=2.0,stepRate=120.0,strideM=1.0)
        val times=MetricTimes().update(moving,1000)
        assertEquals(1.0,times.fresh(moving,1000).strideM!!,0.0)
        assertNull(times.fresh(moving.copy(speedMps=0.0),1001).strideM)
        assertNull(times.fresh(moving,6000).strideM)
    }
    @Test fun atrCounterDistanceDoesNotIntegrateDuringSilenceAndOtherBikesStayReadOnly() {
        fun packet(count: Int)=byteArrayOf(0xab.toByte(),4,1,10,1,100,1,0,0,3,0xe8.toByte(),(count ushr 8).toByte(),count.toByte())
        val s=MobiSession(Protocol.V1,"AT-R66146")
        val first=s.receive("ffe4",packet(100))!!;val totals=TelemetryTotals(first)
        assertTrue(s.canWriteResistance);assertFalse(s.canReadResistance)
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,10,1,1,1),s.resistanceCommand(1));assertNull(s.resistanceCommand(0))
        val next=s.receive("ffe4",packet(101))!!
        repeat(5) { totals.update(next,1000,true,Machine.BIKE,Protocol.V1,70.0,5.0) }
        assertEquals(1000/(2.68*60),totals.distanceM!!,1e-9)
        val calories=totals.caloriesKcal
        repeat(20) { totals.update(next,1000,true,Machine.BIKE,Protocol.V1,70.0,5.0) }
        assertEquals(calories,totals.caloriesKcal)
        val other=MobiSession(Protocol.V1,"Other");other.receive("ffe4",packet(101));assertFalse(other.canWriteResistance)
    }
    @Test fun timedPlanDeadlineCarriesAcrossStagesButDoesNotPredictSensorStages() {
        val engine=TrainingEngine(Workout(title="Plan",steps=listOf(Step(target=1.5),Step(target=2.0))))
        engine.resetBaseline(0,0.0,0)
        assertEquals(3500L,engine.completionDeadlineMs())
        engine.tick(3000,0.0,0);assertEquals(1,engine.index);assertEquals(3500L,engine.completionDeadlineMs())
        engine.tick(3500,0.0,0);assertTrue(engine.done)
        val sensor=TrainingEngine(Workout(title="Distance",steps=listOf(Step(target=1.0),Step(Condition.DISTANCE,100.0))))
        assertNull(sensor.completionDeadlineMs())
    }
    @Test fun fullFtmsRangeKeepsFractionsAndEveryReachableTenthEncodesExactly() {
        val range=Protocols.mobiFtmsRange(byteArrayOf(1,0,245.toByte(),0,1,0))!!
        assertEquals(ResistanceRange(0.1,24.5,0.1),range)
        for(n in 0..244) {
            val target=range.next(range.min,n)
            assertTrue(range.contains(target))
            assertArrayEquals(byteArrayOf(4,(n+1).toByte()),MobiCommands.ftmsResistance(target))
        }
        assertNull(Protocols.mobiFtmsRange(byteArrayOf(1,0,245.toByte(),0,0,0)))
        assertNull(Protocols.mobiFtmsRange(byteArrayOf(1,0,245.toByte(),0,1)))
        assertEquals(ResistanceRange(1.0,24.0),Protocols.mobiFtmsRange(byteArrayOf(10,0,240.toByte(),0)))
    }

    @Test fun completionMetadataRoundTripsAndRejectsInvalidCombinations() {
        val plan=Workout(title="Plan",steps=listOf(Step(target=10.0)))
        val s=Session(elapsedMs=10000,planCompletedMs=10000,completionPending=true,workoutSnapshot=Exchange.workouts(listOf(plan)))
        assertEquals(s,Exchange.parse(Exchange.sessions(listOf(s))).sessions.single())
        for(invalid in listOf(s.copy(planCompletedMs=10001),s.copy(planCompletedMs=null),s.copy(status="completed"))) {
            assertTrue(runCatching { Exchange.parse(Exchange.sessions(listOf(invalid))) }.isFailure)
        }
    }
}
