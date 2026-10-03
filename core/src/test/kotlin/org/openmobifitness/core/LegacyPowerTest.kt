package org.openmobifitness.core

import org.junit.Test
import org.junit.Assert.*

class LegacyPowerTest {
    private fun frame(interval: Int,level: Int,subtype: Int=17)=byteArrayOf(0xab.toByte(),4,17,11,subtype.toByte(),0,1,0,0,(interval shr 8).toByte(),interval.toByte(),0,1,level.toByte(),0)
    @Test fun official0B11ReferencePointsAndActualResistanceFeedback() {
        // Integer reference outputs independently evaluated from international APK Smali.
        val expected=mapOf(1000 to listOf(20.0,85.0,211.0),500 to listOf(66.0,235.0,900.0))
        expected.forEach { (interval,watts) -> listOf(1,12,24).forEachIndexed { i,level ->
            val m=Protocols.v1Metrics(frame(interval,level))!!
            assertEquals(watts[i],m.powerW!!,0.0); assertTrue(m.powerEstimated)
            assertEquals(30000.0/interval,m.cadence!!,0.0)
        } }
        val source=frame(500,1)
        Protocols.v1Resistance(source,24) // A requested level must not alter the current reading.
        assertEquals(66.0,Protocols.v1Metrics(source)!!.powerW!!,0.0)
        assertEquals(900.0,Protocols.v1Metrics(frame(500,24))!!.powerW!!,0.0)
    }
    @Test fun idleUnknownAndMalformedDataDoNotInventPower() {
        assertEquals(0.0,Protocols.v1Metrics(frame(0,12))!!.powerW!!,0.0)
        for(level in listOf(0,25,255)) assertNull(Protocols.v1Metrics(frame(500,level))!!.powerW)
        assertNull(Protocols.v1Metrics(frame(500,12,99))!!.powerW)
        assertEquals(85.0,Protocols.v1Metrics(frame(1,12))!!.powerW!!,0.0) // Official BaseHandler substitutes 30 rpm for an out-of-range elliptical pulse rate.
        assertNull(Protocols.v1Metrics(frame(500,12).copyOf(11))!!.powerW)
        val good=Protocols.v1Metrics(frame(500,12))!!
        assertEquals(85.0,good.merge(Protocols.v1Metrics(frame(1,12))!!).powerW!!,0.0)
        val measured=good.merge(Metrics(powerW=72.0))
        assertEquals(72.0,measured.powerW!!,0.0); assertFalse(measured.powerEstimated)
        assertTrue(good.merge(Metrics(heartBpm=120)).powerEstimated)
    }
    @Test fun powerProvenanceRoundTripsAndOldSampleSchemasStillImport() {
        val id=Session().id
        val sample=Sample(id,1000,Protocols.v1Metrics(frame(500,12))!!)
        assertEquals(listOf(sample),Exchange.parse(Exchange.samples(listOf(sample))).samples)
        val edge=Sample(id,2000,Metrics(powerW=40000.0,powerEstimated=true)) // Imported high-power samples retain provenance without tying validation to one model.
        assertTrue(edge.metrics.powerW!!>32767)
        assertEquals(listOf(edge),Exchange.parse(Exchange.samples(listOf(edge))).samples)
        val rows=Csv.read(Exchange.samples(listOf(sample)))
        val legacy=Csv.write(listOf(rows[0].take(17),rows[1].take(17).toMutableList().apply { this[0]="2" }))
        assertFalse(Exchange.parse(legacy).samples.single().metrics.powerEstimated)
        assertEquals(235.0,Exchange.parse(legacy).samples.single().metrics.powerW!!,0.0)
    }
    @Test fun officialEnergyRateRespondsToActualPowerAndStopsOnPauseOrMissingPackets() {
        val t=TelemetryTotals()
        val m=Protocols.v1Metrics(frame(500,1))!! // Official integer display: 66 W at 60 rpm, level 1.
        fun tick(value: Metrics=m,active: Boolean=true)=t.update(value,1000,active,Machine.ELLIPTICAL,Protocol.V1,70.0,20.0,Estimates.legacyPower(value.cadence,value.resistance,ResistanceRange(1.0,24.0),true,truncate=false))
        repeat(60) { tick() }
        val low=Protocols.v1Metrics(frame(500,1),truncatePower=false)!!.powerW!!
        assertEquals((70.0/3600+low/1000)*60,t.caloriesKcal!!,1e-9)
        assertEquals("legacy-v1",t.energyModel)
        val before=t.caloriesKcal
        tick(active=false); tick(m.copy(cadence=null,powerW=null)); tick(m.copy(cadence=0.0,powerW=0.0))
        assertEquals(before,t.caloriesKcal)
        tick(Protocols.v1Metrics(frame(500,12))!!)
        assertEquals(before!!+70.0/3600+Protocols.v1Metrics(frame(500,12),truncatePower=false)!!.powerW!!/1000,t.caloriesKcal!!,1e-9)
        assertTrue(t.caloriesEstimated)
        // The raw v4 interchange stays readable and the new provenance survives v5.
        val session=Session(energyModel=t.energyModel,caloriesKcal=t.caloriesKcal,caloriesEstimated=true)
        assertEquals(session,Exchange.parse(Exchange.sessions(listOf(session))).sessions.single())
        val v4=Csv.read(Exchange.sessions(listOf(session))).mapIndexed { i,row -> row.take(20).toMutableList().apply { if(i>0) this[0]="4" } }
        assertEquals(session.copy(energyModel=""),Exchange.parse(Csv.write(v4)).sessions.single())
    }

}
