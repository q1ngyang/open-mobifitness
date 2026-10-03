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
    // AB is the V1 state header. AC is a separate command/event family, not motion data.
    // Verified against international APK Smali, the CN handler and a physical MB-EP capture.
    fun isV1State(data: ByteArray)=data.size>=11 && data[0].u()==0xab && data[1].u()==4
    fun machine(name: String) = when {
        name.startsWith("MB-E",true) || name.startsWith("MB-MEH",true) || name.startsWith("MOBI-E",true) -> Machine.ELLIPTICAL
        name.startsWith("MB-B",true) || name.startsWith("MB-MBH",true) -> Machine.BIKE
        name.startsWith("MB-R",true) || name.startsWith("MB-MRH",true) -> Machine.ROWER
        name.startsWith("MB-T",true) || name.startsWith("MB-MTH",true) -> Machine.TREADMILL
        else -> Machine.UNKNOWN
    }
    fun v2Resistance(value: Int): ByteArray { require(value in 0..255); return byteArrayOf(2, value.toString().length.toByte(), value.toByte()) }
    fun v1Resistance(template: ByteArray, value: Int): ByteArray {
        require(isV1State(template) && template[3].u() in 10..11)
        require(MobiProfiles.identify(Protocol.V1,template[3].u(),template[4].u()).canWriteResistance)
        require(v1Range(template)?.contains(value.toDouble())==true)
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
    fun mobiFtmsRange(data: ByteArray): ResistanceRange? = runCatching {
        require(data.size>=4); val p=Packet(data)
        ResistanceRange((p.s16()*0.1).roundToInt().toDouble(),(p.s16()*0.1).roundToInt().toDouble())
    }.getOrNull()
    fun v2Range(data: ByteArray): ResistanceRange? = runCatching {
        require(data.size >= 3 && data[0].u() in listOf(1,3,4,5))
        ResistanceRange(data[1].u().toDouble(),data[2].u().toDouble())
    }.getOrNull()
    // Only named subtypes whose ranges are supported by the decoded legacy switch.
    fun v1Range(data: ByteArray): ResistanceRange? {
        if(!isV1State(data)) return null
        return MobiProfiles.identify(Protocol.V1,data[3].u(),data[4].u()).range
    }
    fun v1Metrics(data: ByteArray,truncatePower: Boolean=true): Metrics? {
        if(!isV1State(data) || data[3].u() !in 10..11) return null
        val session=MobiSession(Protocol.V1,"")
        val metrics=session.receive("ffe4",data) ?: return null
        // Stateless compatibility helper: a settled resistance snapshot. The live
        // connection uses MobiSession's per-second MotionData calculation state.
        return metrics.copy(powerW=Estimates.legacyPower(metrics.cadence,metrics.resistance,session.range,data[3].u()==11 && data[4].u()==17,truncatePower))
    }
    fun v2Metrics(data: ByteArray): Metrics? = runCatching {
        require(data.size in listOf(9,11,13))
        fun be(i: Int) = data[i].u() * 256 + data[i+1].u()
        Metrics(cadence=data[2].u().toDouble(), speedMps=data[0].u() / 10.0, distanceM=be(5).toDouble(),
            powerW=if(data.size >= 11) be(9).toDouble() else null, strokes=if(data.size >= 13) be(11) else null,
            caloriesKcal=be(7)/10.0)
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
    fun ftmsData(characteristic: String, data: ByteArray,officialRounding: Boolean=false,handlerValues: Boolean=false): Metrics? = runCatching {
        val p=Packet(data)
        val f=if(characteristic == "2ace") p.u24() else p.u16()
        fun flag(bit: Int) = f and (1 shl bit) != 0
        fun speed(): Double { val raw=p.u16(); return if(officialRounding) (raw*0.01*10).roundToInt()/10.0/3.6 else raw/360.0 }
        var speed: Double?=null; var cadence: Double?=null; var distance: Double?=null
        var resistance: Double?=null; var power: Double?=null; var heart: Int?=null; var strokes: Int?=null
        var calories: Double?=null; var incline: Double?=null; var force: Double?=null; var steps: Double?=null
        var duration: Int?=null; var vendorTreadmillFrequency: Double?=null
        fun energy() { val total=p.u16(); calories=total.takeIf { it!=65535 }?.toDouble(); p.skip(3) }
        when(characteristic) {
            "2ad2" -> {
                if(!flag(0)) speed=speed()
                if(flag(1)) p.skip(2)
                if(flag(2)) cadence=p.u16()/2.0
                if(flag(3)) p.skip(2)
                if(flag(4)) distance=p.u24().toDouble()
                if(flag(5)) resistance=p.s16().toDouble()
                if(flag(6)) power=p.s16().toDouble()
                if(flag(7)) p.skip(2)
                if(flag(8)) energy()
                if(flag(9)) heart=p.u8()
                if(flag(10)) p.skip(1)
                if(flag(11)) duration=p.u16()
                if(flag(12)) p.skip(2)
            }
            "2ace" -> {
                if(!flag(0)) speed=speed()
                if(flag(1)) p.skip(2)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) { steps=p.u16().toDouble(); p.skip(2) }
                if(flag(4)) p.skip(2)
                if(flag(5)) p.skip(4)
                if(flag(6)) { incline=p.s16()/10.0; p.skip(2) }
                if(flag(7)) resistance=p.s16()/10.0
                if(flag(8)) power=p.s16().toDouble()
                if(flag(9)) p.skip(2)
                if(flag(10)) energy()
                if(flag(11)) heart=p.u8()
                if(flag(12)) p.skip(1)
                if(flag(13)) duration=p.u16()
                if(flag(14)) p.skip(2)
            }
            "2ad1" -> {
                if(!flag(0)) { cadence=p.u8()/2.0; strokes=p.u16() }
                if(flag(1)) p.skip(1)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) { val pace=p.u16(); if(pace>0) speed=(500.0/pace).let { if(handlerValues) (it*10).roundToInt()/10.0 else it } }
                if(flag(4)) p.skip(2)
                if(flag(5)) power=p.s16().toDouble()
                if(flag(6)) p.skip(2)
                if(flag(7)) resistance=p.s16().toDouble()
                if(flag(8)) energy()
                if(flag(9)) heart=p.u8()
                if(flag(10)) p.skip(1)
                if(flag(11)) duration=p.u16()
                if(flag(12)) p.skip(2)
            }
            "2acd" -> {
                if(!flag(0)) speed=speed()
                if(flag(1)) p.skip(2)
                if(flag(2)) distance=p.u24().toDouble()
                if(flag(3)) { incline=p.s16()/10.0; p.skip(2) }
                if(flag(4)) p.skip(4)
                if(flag(5)) vendorTreadmillFrequency=p.u8()/10.0
                if(flag(6)) p.skip(1)
                if(flag(7)) energy()
                if(flag(8)) heart=p.u8()
                if(flag(9)) p.skip(1)
                if(flag(10)) duration=p.u16()
                if(flag(11)) p.skip(2)
                if(flag(12)) { force=p.s16().toDouble(); power=p.s16().toDouble() }
                if(flag(13)) steps=p.u24().toDouble()
            }
            else -> error("unknown_characteristic")
        }
        if(handlerValues) { resistance=resistance?.toInt()?.toDouble(); incline=incline?.toInt()?.toDouble() }
        Metrics(cadence,resistance,speed,distance,heart,power,strokes,calories,incline,forceN=force,
            stepRate=if(characteristic!="2acd") steps else vendorTreadmillFrequency.takeIf { handlerValues },
            strideM=if(handlerValues && characteristic=="2acd" && (speed ?: 0.0)>0 && (vendorTreadmillFrequency ?: 0.0)>0)
                speed!!*6/vendorTreadmillFrequency!! else null,
            stepCount=steps?.toInt()?.takeIf { characteristic=="2acd" },deviceDurationSec=duration)
    }.getOrNull()

    /** MOBI's cross-trainer handler accepts both two- and three-byte flag headers.
     * Select only a layout whose declared fields consume the entire packet.
     * This is data-format negotiation; no trial control commands are transmitted.
     */
    fun ftmsCompatibleData(characteristic: String,data: ByteArray,name: String,handlerValues: Boolean=false): Metrics? {
        if(characteristic!="2ace") return ftmsData(characteristic,data,officialRounding=true,handlerValues=handlerValues)
        if(data.size<2) return null
        val flags=data[0].u() or (data[1].u() shl 8)
        val sizes=intArrayOf(2,2,3,4,2,4,4,2,2,2,5,1,1,2,2)
        var body=if(flags and 1 == 0) sizes[0] else 0
        for(bit in 1..14) if(flags and (1 shl bit)!=0) body+=sizes[bit]
        val preferThree=name.startsWith("MB-E1010") || name.startsWith("Fibonacci")
        val headers=if(preferThree) listOf(3,2) else listOf(2,3)
        val header=headers.firstOrNull { body+it==data.size } ?: return null
        val standard=if(header==3) data else data.copyOfRange(0,2)+byteArrayOf(0)+data.copyOfRange(2,data.size)
        return ftmsData(characteristic,standard,officialRounding=true,handlerValues=handlerValues)?.let { it.copy(cadence=it.stepRate) }
    }
}
