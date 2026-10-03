package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class ConnectionProtocolTest {
    @Test fun identificationUsesOfficialPriorityEvenWithMultipleServices() {
        assertEquals(Protocol.V2,MobiProfiles.select("MOBI-E",setOf("8800","ffe0","fff0","1826","180d")))
        assertEquals(Protocol.V1,MobiProfiles.select("MOBI-E",setOf("ffe0","fff0","1826")))
        assertEquals(Protocol.HUANTONG,MobiProfiles.select("MOBI-E",setOf("fff0","1826")))
        assertEquals(Protocol.FTMS,MobiProfiles.select("MOBI-C",setOf("fff0","1826")))
    }
    @Test fun initialReadsAndNotificationsFollowDiscoveredOrderAndShareTheSameQueue() {
        val endpoints=listOf(GattEndpoint("180a","2a26",2),GattEndpoint("180f","2a19",18),
            GattEndpoint("8800","8801",2),GattEndpoint("8800","8802",2),GattEndpoint("8800","8812",18),
            GattEndpoint("8800","8813",16),GattEndpoint("8800","8814",16),GattEndpoint("8800","880f",8))
        val sequence=GattPlan.initialization(Protocol.V2,"",endpoints).map { "${it.action}:${it.endpoint?.characteristic ?: "128"}" }
        assertEquals(listOf("MTU:128","READ:2a26","READ:2a19","NOTIFY:2a19","READ:8801","READ:8802","READ:8812","NOTIFY:8812","NOTIFY:8813","NOTIFY:8814"),sequence)
        val water=GattPlan.initialization(Protocol.V1,"MB-HW",listOf(GattEndpoint("ffe0","ffe4",16),GattEndpoint("ffe0","ffe1",16)))
        assertEquals(2000L,water[1].delayMs); assertEquals(0L,water[2].delayMs)
    }
    @Test fun huantongSubscriptionPrecedesOtherServicesAndCommandsMatchSmaliBytes() {
        val endpoints=listOf(GattEndpoint("ffc0","ffe1",16),GattEndpoint("fff0","fff1",16),GattEndpoint("fff0","fff2",8))
        assertEquals(listOf(null,"fff1","ffe1"),GattPlan.initialization(Protocol.HUANTONG,"MOBI-E",endpoints).map { it.endpoint?.characteristic })
        assertArrayEquals(byteArrayOf(0x40,0,0x9a.toByte(),0x46,0x20),MobiCommands.huantongWeight(70))
        assertArrayEquals(byteArrayOf(0x20,0xc1.toByte(),1,0,0xe2.toByte()),MobiCommands.huantongResistance(1))
        assertArrayEquals(byteArrayOf(0x20,0xc1.toByte(),24,0,0xf9.toByte()),MobiCommands.huantongResistance(24))
        assertArrayEquals(byteArrayOf(0x11,0x82.toByte(),7),MobiCommands.unlock)
        assertArrayEquals(byteArrayOf(2,2,24),Protocols.v2Resistance(24))
        assertArrayEquals(byteArrayOf(0,3,120),MobiCommands.v2Speed(120))
        assertArrayEquals(byteArrayOf(1,1,0x81.toByte()),MobiCommands.v2Incline(-1))
        assertArrayEquals(byteArrayOf(3),MobiCommands.v2Status(0))
        assertArrayEquals(byteArrayOf(4),MobiCommands.v2Status(1))
        assertArrayEquals(byteArrayOf(5),MobiCommands.v2Status(2))
    }
    @Test fun explicitReadOnlyCapabilityNeverBecomesWritableAfterIdentificationOrFeedback() {
        val m=MobiSession(Protocol.V2,"MB-E")
        m.receive("8806",byteArrayOf(1,1,32)); m.receive("8802",byteArrayOf(11,17))
        assertFalse(m.canWriteResistance); assertTrue(m.canReadResistance); assertEquals(32.0,m.range!!.max,0.0)
        assertEquals(24.0,m.receive("8812",byteArrayOf(24))!!.resistance!!,0.0)
        assertNull(m.resistanceCommand(10))
        m.receive("8806",byteArrayOf(5,1,24)); assertTrue(m.canWriteResistance)
        assertArrayEquals(byteArrayOf(2,2,24),m.resistanceCommand(24))
        val disconnectedReplacement=MobiSession(Protocol.V2,"MB-E")
        assertFalse(disconnectedReplacement.canWriteResistance); assertNull(disconnectedReplacement.resistanceCommand(10))
    }
    @Test fun aggregateAndPulseUploadModesDoNotOverwriteEachOther() {
        val m=MobiSession(Protocol.V2,"MB-R")
        m.receive("8802",byteArrayOf(9,3)); m.receive("8801",byteArrayOf(3))
        val metrics=m.receive("8813",byteArrayOf(30,0,24,0,30,0,100,0,30,0,200.toByte(),0,12))!!
        val combined=metrics.merge(m.receive("8811",byteArrayOf(1,0xf4.toByte(),0,12))!!)
        assertEquals(3.0,combined.speedMps!!,0.0); assertEquals(24.0,combined.cadence!!,0.0)
        assertEquals(200.0,combined.powerW!!,0.0); assertNull(m.energyRate)
        m.receive("8801",byteArrayOf(1)); assertNull(m.receive("8813",ByteArray(13)))
    }
    @Test fun truncatedFramesCannotChangeAValidDeviceProfileOrCommandTemplate() {
        val m=MobiSession(Protocol.V1,"MB-E")
        val good=byteArrayOf(0xab.toByte(),4,0,11,17,0,9,0,0,1,0xf4.toByte(),0,0,3)
        assertNotNull(m.receive("ffe4",good))
        for(n in 0..10) assertNull(m.receive("ffe4",good.copyOf(n)))
        assertArrayEquals(byteArrayOf(0xab.toByte(),3,0,11,17,4,9),m.resistanceCommand(4))
        assertNull(m.receive("ffe4",good.copyOf().apply { this[0]=0xac.toByte() }))
    }
    @Test fun oldMagneticRowerUsesItsSecondaryValueEvenWhenTheUnusedIntervalIsZero() {
        val m=MobiSession(Protocol.V1,"MB-R")
        // AB03 + subtype bits 001xxxxx is old magnetic; bytes 11/12 supply its signal.
        val frame=byteArrayOf(0xab.toByte(),3,0,0,0,0,32,0,0,0,0,0,20)
        val first=m.receive("ffe4",frame)!!
        assertTrue(first.speedMps!!>0); assertTrue(first.cadence!!>0)
        assertEquals(1,first.strokes)
        assertEquals(2,m.receive("ffe4",frame)!!.strokes)
        assertNull(m.receive("ffe4",frame.copyOf(10)))
    }
}
