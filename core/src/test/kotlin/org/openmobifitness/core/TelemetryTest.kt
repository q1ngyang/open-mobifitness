package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class TelemetryTest {
    @Test fun countersRebaseAcrossPauseResetAndReconnect() {
        val t=TelemetryTotals(Metrics(distanceM=900.0,caloriesKcal=100.0,strokes=400))
        fun tick(d: Double,e: Double,s: Int,active: Boolean=true)=t.update(Metrics(distanceM=d,caloriesKcal=e,strokes=s),1000,active,Machine.ROWER,Protocol.FTMS,70.0,5.0)
        tick(920.0,102.0,402); assertEquals(20.0,t.distanceM!!,0.0); assertEquals(2,t.strokes)
        tick(950.0,105.0,406,false); tick(960.0,106.0,407)
        assertEquals(30.0,t.distanceM!!,0.0); assertEquals(3.0,t.caloriesKcal!!,0.0); assertEquals(3,t.strokes)
        tick(0.0,0.0,0); tick(5.0,1.0,1); assertEquals(35.0,t.distanceM!!,0.0)
        t.rebase(); tick(500.0,90.0,200); assertEquals(35.0,t.distanceM!!,0.0); assertEquals(4,t.strokes)
        assertFalse(t.distanceEstimated); assertFalse(t.caloriesEstimated)
    }
    @Test fun estimatesOnlyAccumulateWhileMovingAndNeverExtrapolateLongGaps() {
        val t=TelemetryTotals()
        fun tick(cadence: Double?,ms: Long=1000,active: Boolean=true)=t.update(Metrics(cadence=cadence),ms,active,Machine.ELLIPTICAL,Protocol.V1,70.0,5.0)
        tick(60.0)
        assertEquals(60/2.68/4/3.6,t.distanceM!!,1e-9)
        assertEquals(0.102083333,t.caloriesKcal!!,1e-8)
        val energy=t.caloriesKcal
        tick(60.0,1000,false); tick(0.0); tick(null)
        assertEquals(energy,t.caloriesKcal)
        tick(60.0,60_000); assertEquals(energy!!*6,t.caloriesKcal!!,1e-9)
        assertTrue(t.caloriesEstimated); assertTrue(t.distanceEstimated)
        assertNull(Estimates.legacySpeed(60.0,Machine.ROWER))
        assertEquals(7.0,Estimates.kcal(5.0,80.0,60000),1e-9)
    }
    @Test fun deviceEnergyWinsOverMetAndMissingSensorsStayUnknown() {
        val t=TelemetryTotals(Metrics(caloriesKcal=25.0))
        t.update(Metrics(cadence=80.0,caloriesKcal=26.5),1000,true,Machine.BIKE,Protocol.FTMS,70.0,5.0)
        assertEquals(1.5,t.caloriesKcal!!,0.0); assertFalse(t.caloriesEstimated)
        assertNull(t.distanceM)
        val empty=TelemetryTotals(); empty.update(Metrics(),1000,true,Machine.UNKNOWN,Protocol.UNKNOWN,70.0,5.0)
        assertNull(empty.caloriesKcal); assertNull(empty.distanceM)
    }
    @Test fun phaseCountdownAndRangePercentageRespectTheirBoundaries() {
        val e=TrainingEngine(Workout(title="timer",steps=listOf(Step(target=60.0),Step(target=30.0))))
        assertEquals(60000L,e.remainingMs(0)); assertEquals(1L,e.remainingMs(59999))
        e.tick(60500,null,null); assertEquals(29500L,e.remainingMs(60500)); e.tick(90000,null,null); assertEquals(0L,e.remainingMs(90000))
        val r=ResistanceRange(1.0,24.0); assertEquals(0,r.percentage(1.0)); assertEquals(100,r.percentage(24.0)); assertEquals(52,r.percentage(r.percent(50)))
    }
    @Test fun newPlansHaveWarmupRecoveryAndNamedDurations() {
        val minutes=mapOf("light" to 20,"moderate" to 30,"vigorous" to 30,"strength" to 22,"weight" to 40,"hiit" to 20)
        minutes.forEach { (id,duration) ->
            val w=Presets.all.single { it.id==id }
            assertEquals(duration*60.0,w.steps.sumOf { it.target },0.0)
            assertEquals(300.0,w.steps.first().target,0.0); assertEquals(300.0,w.steps.last().target,0.0)
            assertTrue(w.steps.first().resistancePercent!!<=10); assertTrue(w.steps.last().resistancePercent!!<=10)
        }
    }
    @Test fun ftmsEnergyInclineAndForceAreDecodedAtCorrectOffsets() {
        // Bike: cadence, energy and heart. Values after the energy fields must stay aligned.
        val bike=byteArrayOf(0x04,3,0x10,0x0e,120,0,42,0,100,0,7,140.toByte())
        val b=Protocols.ftmsData("2ad2",bike)!!
        assertEquals(42.0,b.caloriesKcal!!,0.0); assertEquals(140,b.heartBpm); assertEquals(60.0,b.cadence!!,0.0)
        for(n in 0 until bike.size) assertNull(Protocols.ftmsData("2ad2",bike.copyOf(n)))
        val treadmill=byteArrayOf(0x88.toByte(),0x10,0x10,0x0e,0xec.toByte(),0xff.toByte(),0,0,50,0,100,0,7,0xf6.toByte(),0xff.toByte(),0x2c,1)
        val t=Protocols.ftmsData("2acd",treadmill)!!
        assertEquals(-2.0,t.inclinePercent!!,0.0); assertEquals(50.0,t.caloriesKcal!!,0.0)
        assertEquals(-10.0,t.forceN!!,0.0); assertEquals(300.0,t.powerW!!,0.0)
        for(n in 0 until treadmill.size) assertNull(Protocols.ftmsData("2acd",treadmill.copyOf(n)))
        assertNull(Protocols.ftmsData("2ad2",bike.copyOf().apply { this[6]=(-1).toByte(); this[7]=(-1).toByte() })!!.caloriesKcal)
    }
    @Test fun v2EnergyUsesTheObservedTenthsOfKcalField() {
        val b=byteArrayOf(100,0,60,0,30,1,44,0,123,0,100,0,5)
        val m=Protocols.v2Metrics(b)!!
        assertEquals(12.3,m.caloriesKcal!!,0.0); assertEquals(300.0,m.distanceM!!,0.0); assertEquals(100.0,m.powerW!!,0.0); assertEquals(5,m.strokes)
    }
    @Test fun csvV2PreservesAllNewReadingsAndAcceptsV1() {
        val s=Session(elapsedMs=1000,caloriesKcal=7.0,caloriesEstimated=true,distanceEstimated=true,weightKg=80.0,met=5.0)
        val m=Metrics(caloriesKcal=7.0,inclinePercent=-2.0,strideM=0.8,forceN=-10.0,stepRate=150.0,stepCount=600,targetCadence=24.0)
        val sample=Sample(s.id,1000,m)
        assertEquals(s,Exchange.parse(Exchange.sessions(listOf(s))).sessions.single())
        assertEquals(sample,Exchange.parse(Exchange.samples(listOf(sample))).samples.single())
        val oldRows=Csv.read(Exchange.sessions(listOf(s))).mapIndexed { i,row -> row.take(12).toMutableList().apply { if(i>0) this[0]="1" } }
        assertEquals(s.copy(caloriesKcal=null,caloriesEstimated=false,distanceEstimated=false,weightKg=null,met=null),Exchange.parse(Csv.write(oldRows)).sessions.single())
    }
    @Test fun prereleaseUpdatesUseSemanticOrdering() {
        assertTrue(Version.parse("0.1.0-alpha.10")!!>Version.parse("0.1.0-alpha.2")!!)
        assertTrue(Version.parse("0.1.0")!!>Version.parse("0.1.0-rc.1")!!)
        assertTrue(Version.parse("0.2.0-alpha.1")!!>Version.parse("0.1.0")!!)
        assertEquals(Version.parse("0.1.0-alpha.2"),Version.parse("v0.1.0-alpha.2-debug"))
        assertNull(Version.parse("unknown"))
    }
    @Test fun rowerAndCrossTrainerEnergyDoNotShiftFollowingFields() {
        val rower=byteArrayOf(0x8c.toByte(),3,48,123,0,0xe8.toByte(),3,0,0x96.toByte(),0,100,0,42,0,44,1,5,(-126).toByte())
        val r=Protocols.ftmsData("2ad1",rower)!!
        assertEquals(24.0,r.cadence!!,0.0); assertEquals(123,r.strokes); assertEquals(1000.0,r.distanceM!!,0.0)
        assertEquals(500.0/150,r.speedMps!!,1e-9); assertEquals(42.0,r.caloriesKcal!!,0.0); assertEquals(130,r.heartBpm)
        for(n in 0 until rower.size) assertNull(Protocols.ftmsData("2ad1",rower.copyOf(n)))
        val cross=byteArrayOf(0xcc.toByte(),13,0,16,14,0xe8.toByte(),3,0,60,0,56,0,25,0,0,0,0xb4.toByte(),0,0xc8.toByte(),0,42,0,44,1,5,0x8e.toByte())
        val e=Protocols.ftmsData("2ace",cross)!!
        assertEquals(60.0,e.stepRate!!,0.0); assertNull(e.cadence) // Steps/minute are not revolutions/minute.
        assertEquals(2.5,e.inclinePercent!!,0.0); assertEquals(18.0,e.resistance!!,0.0)
        assertEquals(200.0,e.powerW!!,0.0); assertEquals(42.0,e.caloriesKcal!!,0.0); assertEquals(142,e.heartBpm)
        for(n in 0 until cross.size) assertNull(Protocols.ftmsData("2ace",cross.copyOf(n)))
    }

    @Test fun aShortPauseWithoutASampleDoesNotChargeHiddenMovementOnResume() {
        val t=TelemetryTotals(Metrics(distanceM=100.0,caloriesKcal=10.0))
        t.update(Metrics(distanceM=110.0,caloriesKcal=11.0),1000,true,Machine.BIKE,Protocol.FTMS,70.0,5.0)
        t.rebase() // Controller does this on resume, even if no paused tick ran.
        t.update(Metrics(distanceM=150.0,caloriesKcal=15.0),1000,true,Machine.BIKE,Protocol.FTMS,70.0,5.0)
        assertEquals(10.0,t.distanceM!!,0.0); assertEquals(1.0,t.caloriesKcal!!,0.0)
        t.update(Metrics(distanceM=153.0,caloriesKcal=15.5),1000,true,Machine.BIKE,Protocol.FTMS,70.0,5.0)
        assertEquals(13.0,t.distanceM!!,0.0); assertEquals(1.5,t.caloriesKcal!!,0.0)
    }

}
