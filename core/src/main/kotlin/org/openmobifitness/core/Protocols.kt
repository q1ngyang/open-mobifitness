package org.openmobifitness.core

import kotlin.math.roundToInt

private fun Byte.u() = toInt() and 255
private class Packet(val data: ByteArray) {
    var pos = 0
    fun u8(): Int { require(pos < data.size); return data[pos++].u() }
    fun u16() = u8() or (u8() shl 8)
    fun s16() = u16().toShort().toInt()
    fun u24() = u16() or (u8() shl 16)
    fun skip(n: Int) { require(pos + n <= data.size); pos += n }
}
object Protocols {
    fun machine(name: String) = when {
        name.startsWith("MB-E",true) || name.startsWith("MB-MEH",true) || name.startsWith("MOBI-E",true) -> Machine.ELLIPTICAL
        name.startsWith("MB-B",true) || name.startsWith("MB-MBH",true) -> Machine.BIKE
        name.startsWith("MB-R",true) || name.startsWith("MB-MRH",true) -> Machine.ROWER
        name.startsWith("MB-T",true) || name.startsWith("MB-MTH",true) -> Machine.TREADMILL
        else -> Machine.UNKNOWN
    }
    fun v2Resistance(value: Int): ByteArray { require(value in 0..255); return byteArrayOf(2, value.toString().length.toByte(), value.toByte()) }
    fun v1Resistance(template: ByteArray, value: Int): ByteArray {
        require(template.size >= 11 && template[0].u() == 0xac && template[1].u() == 4 && template[3].u() in 10..11)
        require(value in 1..32)
        return byteArrayOf(0xab.toByte(),3,0,template[3],template[4],value.toByte(),template[6])
    }
    fun ftmsResistance(value: Double): ByteArray {
        require(value.isFinite() && value in 0.0..3276.7)
        val scaled = (value * 10).roundToInt()
        return byteArrayOf(4,scaled.toByte(),(scaled shr 8).toByte())
    }
    fun ftmsRange(data: ByteArray): ResistanceRange? = runCatching {
        require(data.size == 6); val p = Packet(data)
        ResistanceRange(p.s16() / 10.0, p.s16() / 10.0, p.u16() / 10.0)
    }.getOrNull()
    fun v2Range(data: ByteArray): ResistanceRange? = runCatching {
        require(data.size >= 3 && data[0].u() in listOf(1,3,4,5))
        ResistanceRange(data[1].u().toDouble(),data[2].u().toDouble())
    }.getOrNull()
    // Only named subtypes whose ranges are supported by the decoded legacy switch.
    fun v1Range(data: ByteArray): ResistanceRange? {
        if(data.size < 11 || data[0].u() != 0xac || data[1].u() != 4) return null
        val max = when(data[3].u()) {
            11 -> when(data[4].u()) { 17,19 -> 24; 18 -> 8; else -> return null }
            10 -> when(data[4].u()) { 18,21,23 -> 24; 19,20,22 -> 32; else -> return null }
            else -> return null
        }
        return ResistanceRange(1.0,max.toDouble())
    }
    fun v1Metrics(data: ByteArray): Metrics? = runCatching {
        require(data.size >= 11 && data[0].u() == 0xac && data[1].u() == 4 && data[3].u() in 10..11)
        val interval = (7..10).fold(0L) { acc,i -> (acc shl 8) or data[i].u().toLong() }
        val magneticElliptical=data[3].u()==11 && data[4].u()==17
        val rpm = if(interval == 0L) 0.0 else 60000.0 / interval / if(magneticElliptical) 2 else 1
        Metrics(cadence = rpm.takeIf { it <= 300 },resistance = if(data.size > 13) data[13].u().toDouble() else null,
            heartBpm=if(magneticElliptical && data.size>14) data[14].u().takeIf { it>0 } else null)
    }.getOrNull()
    fun v2Metrics(data: ByteArray): Metrics? = runCatching {
        require(data.size in listOf(9,11,13))
        fun be(i: Int) = data[i].u() * 256 + data[i+1].u()
        Metrics(cadence=data[2].u().toDouble(), speedMps=data[0].u() / 36.0, distanceM=be(5).toDouble(),
            powerW=if(data.size >= 11) be(9).toDouble() else null, strokes=if(data.size >= 13) be(11) else null)
    }.getOrNull()
    fun intervalCadence(data: ByteArray, magnets: Int): Double? {
        if(data.size < 2 || magnets !in 1..255) return null
        val ms=data[0].u()*256+data[1].u()
        return (if(ms==0) 0.0 else 60000.0/ms/magnets).takeIf { it <= 300 }
    }
    fun heart(data: ByteArray): Int? = runCatching {
        val p=Packet(data); val flags=p.u8(); (if(flags and 1 == 0) p.u8() else p.u16()).takeIf { it in 1..255 }
    }.getOrNull()
    /** FTMS optional fields are bounds checked before publishing any part of a frame. */
    fun ftmsData(characteristic: String, data: ByteArray): Metrics? = runCatching {
        val p=Packet(data)
        val f=if(characteristic == "2ace") p.u24() else p.u16()
        fun flag(bit: Int) = f and (1 shl bit) != 0
        var speed: Double?=null; var cadence: Double?=null; var distance: Double?=null
        var resistance: Double?=null; var power: Double?=null; var heart: Int?=null; var strokes: Int?=null
        when(characteristic) {
            "2ad2" -> {
                if(!flag(0)) speed=p.u16()/360.0
                if(flag(1)) p.skip(2)
                if(flag(2)) cadence=p.u16()/2.0
                if(flag(3)) p.skip(2)
                if(flag(4)) distance=p.u24().toDouble()
                if(flag(5)) resistance=p.s16()/10.0
                if(flag(6)) power=p.s16().toDouble()
                if(flag(7)) p.skip(2)
                if(flag(8)) p.skip(5)
                if(flag(9)) heart=p.u8()
                if(flag(10)) p.skip(1)
                if(flag(11)) p.skip(2)
                if(flag(12)) p.skip(2)
            }
            "2ace" -> {
                if(!flag(0)) speed=p.u16()/360.0
                if(flag(1)) p.skip(2)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) { cadence=p.u16().toDouble(); p.skip(2) }
                if(flag(4)) p.skip(2)
                if(flag(5)) p.skip(4)
                if(flag(6)) p.skip(4)
                if(flag(7)) resistance=p.s16()/10.0
                if(flag(8)) power=p.s16().toDouble()
                if(flag(9)) p.skip(2)
                if(flag(10)) p.skip(5)
                if(flag(11)) heart=p.u8()
                if(flag(12)) p.skip(1)
                if(flag(13)) p.skip(2)
                if(flag(14)) p.skip(2)
            }
            "2ad1" -> {
                if(!flag(0)) { cadence=p.u8()/2.0; strokes=p.u16() }
                if(flag(1)) p.skip(1)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) { val pace=p.u16(); if(pace>0) speed=500.0/pace }
                if(flag(4)) p.skip(2)
                if(flag(5)) power=p.s16().toDouble()
                if(flag(6)) p.skip(2)
                if(flag(7)) resistance=p.s16()/10.0
                if(flag(8)) p.skip(5)
                if(flag(9)) heart=p.u8()
                if(flag(10)) p.skip(1)
                if(flag(11)) p.skip(2)
                if(flag(12)) p.skip(2)
            }
            "2acd" -> {
                if(!flag(0)) speed=p.u16()/360.0
                if(flag(1)) p.skip(2)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) p.skip(4)
                if(flag(4)) p.skip(4)
                if(flag(5)) p.skip(1)
                if(flag(6)) p.skip(1)
                if(flag(7)) p.skip(5)
                if(flag(8)) heart=p.u8()
                if(flag(9)) p.skip(1)
                if(flag(10)) p.skip(2)
                if(flag(11)) p.skip(2)
                if(flag(12)) p.skip(4)
            }
            else -> error("unknown_characteristic")
        }
        Metrics(cadence,resistance,speed,distance,heart,power,strokes)
    }.getOrNull()
}
