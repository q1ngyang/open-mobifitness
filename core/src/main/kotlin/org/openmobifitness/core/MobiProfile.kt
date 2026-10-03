package org.openmobifitness.core

data class MobiProfile(val machine: Machine, val kind: Int, val subtype: Int, val name: String,
                       val resistanceMode: Int, val range: ResistanceRange?) {
    val canReadResistance get() = resistanceMode == 2 || resistanceMode == 3
    val canWriteResistance get() = resistanceMode == 1 || resistanceMode == 3
}

/** Wire codes are kept separate from application enum ordinals. */
object MobiProfiles {
    fun machine(kind: Int, subtype: Int=0) = when (kind) {
        9 -> Machine.ROWER; 10 -> Machine.BIKE; 11 -> Machine.ELLIPTICAL; 12 -> Machine.TREADMILL
        16 -> when(subtype) { 1 -> Machine.JUMP_ROPE; 2 -> Machine.DUMBBELL; else -> Machine.UNKNOWN }
        else -> Machine.UNKNOWN
    }
    fun readWriteMode(raw: Int) = when (raw) { 1,3 -> 2; 4 -> 1; 5 -> 3; else -> 0 }
    fun identify(protocol: Protocol, kind: Int, subtype: Int): MobiProfile {
        val v2 = protocol == Protocol.V2
        val (mode, maximum) = when (kind) {
            10 -> when (subtype) {
                1 -> 0 to 24
                18,23 -> 3 to 24
                19,20,22 -> 2 to 32
                21 -> 3 to if(v2) 32 else 24
                24,32 -> if(v2) 3 to 32 else 0 to 24
                25 -> 0 to 24
                33 -> if(v2) 2 to 8 else 0 to 24
                else -> 0 to 24
            }
            11 -> when (subtype) {
                17,19 -> 3 to 24
                18 -> 2 to 8
                20,21 -> if(v2) 3 to 24 else 0 to 24
                22 -> if(v2) 3 to 40 else 0 to 24
                else -> 0 to 24
            }
            12 -> 2 to 24
            else -> 0 to 24
        }
        val name = when(kind) {
            9 -> listOf("water", "magnetic", "mini-water", "air", "dynamic-air", "standard-magnetic", "upright-water").getOrNull(subtype)
            10 -> mapOf(1 to "small-magnetic",18 to "large-magnetic",19 to "MBH3204",20 to "MBH3203",21 to "MBH3205",22 to "MBH3206",23 to "MBH3201",24 to "MBH3208",25 to "MBH2201",32 to "spin-bike",33 to "MBH3207")[subtype]
            11 -> mapOf(1 to "small-magnetic",17 to "0B11",18 to "MEH3205",19 to "MEH3204",20 to "MEH3201",21 to "front-drive",22 to "MEH3206")[subtype]
            12 -> mapOf(0 to "powered-climber",1 to "unpowered-treadmill",17 to "Yiwei",18 to "MTH0202_5",19 to "MTH0201",20 to "MTH0203",21 to "incline-treadmill")[subtype]
            16 -> mapOf(1 to "jump-rope",2 to "dumbbell")[subtype]
            else -> null
        } ?: "%02X%02X".format(kind,subtype)
        return MobiProfile(machine(kind,subtype),kind,subtype,name,mode,
            if(kind in 10..11 && (mode!=0 || subtype==1)) ResistanceRange(1.0,maximum.toDouble()) else null)
    }
    fun select(name: String, services: Set<String>): Protocol = when {
        "8800" in services -> Protocol.V2
        "ffe0" in services -> Protocol.V1
        "fff0" in services && name.startsWith("MOBI-") && !name.startsWith("MOBI-C") -> Protocol.HUANTONG
        "1826" in services -> Protocol.FTMS
        else -> Protocol.UNKNOWN
    }
    val scanServices = setOf("8800","ffe0","fff0","ffc0","2902","1826","180d")
    fun officialFtms(name: String) = name.startsWith("MB-") || name.startsWith("MOBI") || name.startsWith("Fibonacci")
}

object MobiCommands {
    val unlock get() = byteArrayOf(0x11,0x82.toByte(),7)
    fun huantongWeight(kg: Int): ByteArray {
        require(kg in 20..300)
        val pounds=(kg*2.2046226).toInt().toByte()
        return byteArrayOf(0x40,0,pounds,kg.toByte(),(0x40+pounds+kg.toByte()).toByte())
    }
    fun huantongResistance(level: Int): ByteArray {
        require(level in 1..24)
        return byteArrayOf(0x20,0xc1.toByte(),level.toByte(),0,(0x20+0xc1+level).toByte())
    }
    fun v2Status(status: Int): ByteArray {
        require(status in 0..2)
        return byteArrayOf((3+status).toByte()) // stopped, started, paused
    }
    fun ftmsStatus(status: Int): ByteArray = when(status) {
        0 -> byteArrayOf(8,1)
        1 -> byteArrayOf(7)
        2 -> byteArrayOf(8,2)
        else -> throw IllegalArgumentException("status")
    }
    /** Both reference APKs encode the target in a single payload byte. Do not wrap an
     * unrepresentable target into a different resistance; generic FTMS uses Protocols.ftmsResistance.
     */
    fun ftmsResistance(level: Double): ByteArray {
        require(level.isFinite() && level in 0.0..25.5)
        return byteArrayOf(4,(level*10).toInt().toByte())
    }
    fun ftmsSpeed(tenthsKmh: Int): ByteArray {
        require(tenthsKmh in 0..6553)
        val v=tenthsKmh*10
        return byteArrayOf(2,v.toByte(),(v ushr 8).toByte())
    }
    fun ftmsIncline(level: Int): ByteArray {
        require(level in -3276..3276)
        val v=level*10
        return byteArrayOf(3,v.toByte(),(v shr 8).toByte())
    }
    fun incline(value: Int) = if(value<0) (128-value).toByte() else value.toByte()
    fun decodeIncline(value: Int) = if(value and 128 != 0) 128-value else value
    fun v2Speed(tenthsKmh: Int): ByteArray {
        require(tenthsKmh in 0..255)
        return byteArrayOf(0,tenthsKmh.toString().length.toByte(),tenthsKmh.toByte())
    }
    fun v2Incline(level: Int): ByteArray {
        require(level in -127..127)
        return byteArrayOf(1,1,incline(level))
    }
    fun v2Fold(foldedState: Int): ByteArray = byteArrayOf(3,1,if(foldedState==1) 0 else 1)
    fun v1Treadmill(command: Int, value: Int): ByteArray {
        require(command in 0..255 && value in -128..255)
        return byteArrayOf(0xab.toByte(),command.toByte(),value.toByte(),0,value.toByte(),value.toByte(),0,0,0,value.toByte())
    }
    /** Original 8903 interactions. Command 9 constructs a value but never writes it
     * in either APK; preserve that no-op instead of inventing a count-target command.
     */
    fun v2EquipmentTarget(command: Int, value: Int): ByteArray? = when(command) {
        8 -> { require(value in 0..65535); byteArrayOf(4,3,0,(value ushr 8).toByte(),value.toByte()) }
        9 -> null
        10 -> { require(value in 0..6553); val scaled=value*10; byteArrayOf(4,3,2,(scaled ushr 8).toByte(),scaled.toByte()) }
        11 -> { require(value in 0..255); byteArrayOf(5,2,0,value.toByte()) }
        else -> throw IllegalArgumentException("equipment command")
    }
}
