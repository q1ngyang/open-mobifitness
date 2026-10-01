package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun controlPacketsPreserveObservedV2AndFixStandardFtms() {
        assertArrayEquals(byteArrayOf(2,1,5),Protocols.v2Resistance(5))
        assertArrayEquals(byteArrayOf(2,2,12),Protocols.v2Resistance(12))
        assertArrayEquals(byteArrayOf(4,0x78,0),Protocols.ftmsResistance(12.0))
        assertArrayEquals(byteArrayOf(4,0x90.toByte(),1),Protocols.ftmsResistance(40.0))
    }
    @Test fun legacyControlRequiresMatchingTemplate() {
        val original=byteArrayOf(0xab.toByte(),4,0,11,17,80,9,0,0,3,0)
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,5,9),Protocols.v1Resistance(original,5))
        assertThrows(IllegalArgumentException::class.java) { Protocols.v1Resistance(ByteArray(20),5) }
        assertEquals(24.0,Protocols.v1Range(original)!!.max,0.0)
        assertNull(Protocols.v1Range(original.copyOf().apply { this[4]=99 }))
    }
    @Test fun sensorFlagsAndTruncatedPacketsDoNotInventMetrics() {
        // Synthetic FTMS bike: cadence + resistance + power + heart; includes speed.
        val frame=byteArrayOf(0x64,2,0x10,0x0e,120,0,0x78,0,100,0,140.toByte())
        val parsed=Protocols.ftmsData("2ad2",frame)!!
        assertEquals(60.0,parsed.cadence!!,0.0); assertEquals(12.0,parsed.resistance!!,0.0)
        assertEquals(140,parsed.heartBpm); assertNull(parsed.distanceM)
        for(n in 0 until frame.size) assertNull(Protocols.ftmsData("2ad2",frame.copyOf(n)))
        assertEquals(200,Protocols.heart(byteArrayOf(1,200.toByte(),0)))
    }
    @Test fun rangeRejectsInvalidAndQuantizesRelativeToMinimum() {
        assertNull(Protocols.ftmsRange(byteArrayOf(10,0,100,0,0,0)))
        val r=ResistanceRange(1.0,8.0,0.5)
        assertTrue(r.contains(r.percent(33))); assertFalse(r.contains(3.3)); assertEquals(8.0,r.next(8.0,1),0.0)
    }
    @Test fun legacyMagneticEllipticalUsesTwoPulsesPerRevolution() {
        val frame=byteArrayOf(0xab.toByte(),4,0,11,17,80,9,0,0,1,0xf4.toByte(),0,0,12,125)
        val result=Protocols.v1Metrics(frame)!!
        assertEquals(60.0,result.cadence!!,0.0); assertEquals(125,result.heartBpm)
        assertEquals(60.0,Protocols.intervalCadence(byteArrayOf(1,0xf4.toByte(),0,42),2)!!,0.0)
    }
    @Test fun mbEpAbStateRegressionRejectsEventsAndControlEchoes() {
        // Shape from the MB-EP report; sensor values normalized, heart rate omitted.
        val state=byteArrayOf(0xab.toByte(),4,0x11,11,17,0,1,0,0,1,0xf4.toByte(),0,0,1,0)
        val metrics=Protocols.v1Metrics(state)!!
        assertEquals(60.0,metrics.cadence!!,0.0)
        assertEquals(1.0,metrics.resistance!!,0.0)
        assertNull(metrics.heartBpm)
        assertEquals(ResistanceRange(1.0,24.0),Protocols.v1Range(state))
        val command=Protocols.v1Resistance(state,2)
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,2,1),command)
        assertNull(Protocols.v1Metrics(command))
        assertNull(Protocols.v1Metrics(state.copyOf().apply { this[0]=0xac.toByte() }))
        assertNull(Protocols.v1Range(state.copyOf().apply { this[0]=0xac.toByte() }))
        assertThrows(IllegalArgumentException::class.java) { Protocols.v1Resistance(state,25) }
        for(n in 0..10) assertNull(Protocols.v1Metrics(state.copyOf(n)))
    }
    @Test fun csvRoundTripsQuotedUnicodeNewlinesAndFormulaText() {
        val s=Session(device="=SUM(1,2)\n莫比\"",elapsedMs=3000,demo=true,status="completed")
        assertEquals(s,Exchange.parse(Exchange.sessions(listOf(s))).sessions.single())
        assertTrue(Exchange.sessions(listOf(s)).contains("'=SUM"))
        val w=Workout(title="'间歇,训练",steps=listOf(Step(target=30.0,resistancePercent=20)))
        assertEquals(w,Exchange.parse(Exchange.workouts(listOf(w))).workouts.single())
        // FTMS power is signed; our own backups must preserve a negative sample.
        val sample=Sample(s.id,1000,Metrics(powerW=-15.0))
        assertEquals(sample,Exchange.parse(Exchange.samples(listOf(sample))).samples.single())
    }
    @Test fun malformedImportsFailBeforeMutation() {
        assertThrows(ImportProblem::class.java) { Csv.read("a,b\n\"unfinished") }
        assertThrows(ImportProblem::class.java) { Csv.read("a,b\n\"closed\"x,b") }
        val s=Session(elapsedMs=1000)
        assertThrows(IllegalArgumentException::class.java) { Exchange.validateReferences(Archive(samples=listOf(Sample(s.id,2000,Metrics()))),listOf(s)) }
        assertThrows(ImportProblem::class.java) { Exchange.parse(Exchange.sessions(listOf(s)).replace(",1000,",",-1,")) }
    }
    @Test fun trainingUsesActiveTimeAndRequiresSensorData() {
        val engine=TrainingEngine(Workout(title="test",steps=listOf(Step(target=10.0),Step(Condition.DISTANCE,20.0))))
        assertFalse(engine.tick(9999,null,null)); assertTrue(engine.tick(10000,null,null))
        assertFalse(engine.tick(20000,null,null)); assertFalse(engine.tick(21000,500.0,null))
        assertTrue(engine.tick(22000,520.0,null)); assertTrue(engine.done)
    }
    @Test fun allPresetDurationsAndTargetsAreBounded() {
        assertEquals(21,Presets.all.size)
        assertEquals(20*60.0,Presets.all.first { it.id=="interval20" }.steps.sumOf { it.target },0.0)
        assertTrue(Presets.all.all { w -> w.steps.all { it.resistancePercent!! in 0..80 } })
        for(id in listOf("cardio40","hiit40")) assertEquals(40*60.0,Presets.all.first { it.id==id }.steps.sumOf { it.target },0.0)
        assertEquals(30*60.0,Presets.all.first { it.id=="hiit30" }.steps.sumOf { it.target },0.0)
        for(id in listOf("hiit","hiit30","hiit40")) {
            val w=Presets.all.first { it.id==id }
            assertTrue(w.steps.filter { it.resistancePercent!!>=45 }.map { it.resistancePercent }.distinct().size>=3)
            assertTrue(w.steps.first().target>=300 && w.steps.last().target>=300)
        }
    }
    @Test fun delayedTicksDoNotStretchTimedIntervals() {
        val engine=TrainingEngine(Workout(title="intervals",steps=List(3) { Step(target=2.0) }))
        assertTrue(engine.tick(4500,null,null))
        assertEquals(2,engine.index)
        assertEquals(0.25,engine.progress(4500,null,null),0.0)
        assertTrue(engine.tick(6000,null,null))
        assertTrue(engine.done)
    }
}
