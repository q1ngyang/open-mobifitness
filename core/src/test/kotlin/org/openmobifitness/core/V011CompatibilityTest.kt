package org.openmobifitness.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class V011CompatibilityTest {
    private fun String.bytes()=chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun replay(lines: List<String>) {
        val data=lines.filterNot { it.startsWith('#') }
        val keys=data.first().split('\t')
        data.drop(1).forEachIndexed { index,line ->
            val r=keys.zip(line.split('\t')).toMap()
            val frame=r.getValue("packet").bytes()
            val m=Protocols.v1Metrics(frame)!!
            fun check(key: String,actual: Number?) {
                val expected=r.getValue(key).toDoubleOrNull()
                if(expected==null) assertNull("frame $index $key",actual)
                else { assertNotNull("frame $index $key",actual); assertEquals("frame $index $key",expected,actual!!.toDouble(),1e-9) }
            }
            check("Cadence",m.cadence); check("Resistance",m.resistance); check("HeartBpm",m.heartBpm)
            check("PowerW",m.powerW); check("energyPower",Protocols.v1Metrics(frame,false)!!.powerW)
            val s=MobiSession(Protocol.V1,"MB-EP")
            val live=s.receive("ffe4",frame)!!
            check("Cadence",live.cadence); check("Resistance",live.resistance); check("HeartBpm",live.heartBpm)
            check("maximum",s.range!!.max)
            assertEquals(Machine.ELLIPTICAL,s.machine); assertTrue(s.canWriteResistance)
            r.getValue("commands").split(',').forEachIndexed { level,hex ->
                assertArrayEquals("frame $index level ${level+1}",hex.bytes(),s.resistanceCommand(level+1))
            }
            assertNull(s.resistanceCommand(0)); assertNull(s.resistanceCommand(25))
            if(live.resistance!=null) {
                s.activeSecond()
                check("PowerW",s.receive("ffe4",frame)!!.powerW)
                check("energyPower",s.energyPower)
            }
        }
    }
    @Test fun shippedV011ApkMatchesCurrentV1EllipticalOnEverySupportedLevel() {
        val publicRows=javaClass.getResourceAsStream("/oracle/v011-baseline.tsv")!!.bufferedReader().readLines()
        replay(publicRows)
        // User captures remain on the development volume. Supplying this variable
        // adds their actual v0.1.1 APK results to the same strict regression.
        val privateRows=System.getenv("OPENMOBI_PRIVATE_V1_BASELINE_TSV")?.let { File(it).readLines() }
        privateRows?.let { replay(it) }
        println("v0.1.1 baseline replay: public=${publicRows.count { !it.startsWith('#') }-1}, private=${privateRows?.count { !it.startsWith('#') }?.minus(1) ?: 0}")
    }
    @Test fun rejectedOldRowerFrameCannotReplaceAnEllipticalControlTemplate() {
        val s=MobiSession(Protocol.V1,"MB-EP")
        val good="ab04110b110009000001f40000037d".bytes()
        s.receive("ffe4",good)
        val before=s.resistanceCommand(4)
        for(length in 0..12) {
            val bad="ab030000000020000000000001".bytes().copyOf(length)
            assertNull(s.receive("ffe4",bad))
            assertEquals(Machine.ELLIPTICAL,s.machine)
            assertArrayEquals(before,s.resistanceCommand(4))
        }
    }
}
