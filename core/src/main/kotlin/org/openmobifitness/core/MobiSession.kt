package org.openmobifitness.core

private fun Byte.unsigned() = toInt() and 255

/** One device connection owns its profile, pulse history and command template. */
class MobiSession(val protocol: Protocol, val name: String) {
    var profile = MobiProfiles.identify(protocol,0,0); private set
    var machine = Protocols.machine(name); private set
    var range: ResistanceRange? = null; private set
    var resistanceMode = 0; private set
    var uploadMode = 0; private set
    var hardwareCode: Int? = null; private set
    var magnets = 1; private set
    var machineStatus = 0; private set
    var energyPower: Double? = null; private set
    var energyRate: Double? = null; private set
    var lastMotion = false; private set
    var template: ByteArray? = null; private set
    var details = MobiDeviceDetails(); private set
    private var rowing: LegacyRowing = LegacyRowing()
    private var strokeCount = 0
    private var aggregateStrokesReported = false
    private var actualResistance = 1.0 // MotionData's initial calculation input, not reported feedback.
    private val calculationResistance = LegacyResistance()
    private var explicitResistance = false
    private var lastRpm: Double? = null
    private var treadmillSpeed = 0.0
    private var treadmillRate: Double? = null
    val canWriteResistance get() = resistanceMode in setOf(1,3) && range != null
    val canReadResistance get() = resistanceMode in setOf(2,3)

    init {
        if(protocol==Protocol.HUANTONG) {
            machine=if(name.startsWith("MOBI-E")) Machine.ELLIPTICAL else Machine.BIKE
            resistanceMode=1; range=ResistanceRange(1.0,24.0)
            actualResistance=1.0
        }
    }
    private fun identify(kind: Int, subtype: Int) {
        if(profile.kind==kind && profile.subtype==subtype) return
        profile=MobiProfiles.identify(protocol,kind,subtype)
        machine=profile.machine
        if(protocol==Protocol.V1) details=details.copy(supportFold=machine==Machine.TREADMILL && subtype==18)
        if(!explicitResistance) { range=profile.range; resistanceMode=profile.resistanceMode }
        rowing.changeType(subtype)
        if(uploadMode in 2..3) rowing.reverseMode=false
    }
    fun receive(characteristic: String, data: ByteArray): Metrics? {
        lastMotion=false
        return runCatching {
            when(protocol) {
                Protocol.V1 -> if(characteristic in setOf("ffe1","ffe4","ffea","ffeb","fff4")) v1(data) else null
                Protocol.V2 -> v2(characteristic,data)
                Protocol.HUANTONG -> if(characteristic=="fff1") huantong(data) else null
                else -> null
            }
        }.getOrNull()
    }
    private fun rpm(raw: Double, max140: Boolean=false): Double {
        if(max140) return if(raw.toInt() in 1..140) raw else (raw/2).coerceAtMost(140.0)/if(machine==Machine.ELLIPTICAL) 2 else 1
        return if(raw.toInt() in 1..200) raw else if(machine==Machine.BIKE) 60.0 else 30.0
    }
    private fun resistance(level: Int): Double? = level.toDouble().takeIf { canReadResistance && it>0 && range?.contains(it)==true }?.also { actualResistance=it }
    private fun bike(rpm: Double?, feedback: Double?=null, heart: Int?=null): Metrics {
        lastRpm=rpm ?: lastRpm
        val correction=profile.kind==11 && profile.subtype==17
        energyPower=calculationResistance.power(lastRpm,range,correction)
        return Metrics(cadence=rpm,resistance=feedback,heartBpm=heart,powerW=energyPower?.toInt()?.toDouble(),powerEstimated=range!=null)
    }
    private fun rower(interval: Int, second: Int?=null, oldWater: Boolean=false, oldMagnet: Boolean=false, animationOnly: Boolean=false): Metrics {
        when {
            oldWater -> rowing.appendOldWater(interval,requireNotNull(second))
            oldMagnet -> if(second!=0) rowing.appendOldMagnet(requireNotNull(second))
            rowing is LegacyRowing3209 -> (rowing as LegacyRowing3209).append(interval,second ?: 0)
            else -> rowing.append(interval,!animationOnly)
        }
        val completed=if(oldWater || oldMagnet) rowing.speed>0 else rowing.state==3
        if(completed && (!animationOnly || !aggregateStrokesReported)) strokeCount++
        if(rowing.state==10 && second!=null && second>0) strokeCount=second
        val moving=(if(oldMagnet) second!=null && second>0 else interval>0) && rowing.speed>0
        energyRate=if(animationOnly) null else if(moving) rowing.caloriePerSecond else 0.0
        // Mode 3 pulse packets drive the stroke curve; 8813 owns pace, totals and power.
        if(animationOnly) return Metrics(forceN=rowing.avgF.takeIf { it.isFinite() },strokes=strokeCount.takeIf { !aggregateStrokesReported })
        return Metrics(cadence=if(moving) rowing.spm else 0.0,speedMps=if(moving) rowing.speed.takeIf { it.isFinite() } else 0.0,
            strokes=strokeCount,
            powerW=if(moving) rowing.power.takeIf { it.isFinite() }?.toInt()?.toDouble() else 0.0,powerEstimated=true,
            forceN=if(moving) rowing.avgF.takeIf { it.isFinite() } else 0.0)
    }
    private fun v1(data: ByteArray): Metrics? {
        if(data.size>=4 && data[0].unsigned()==0xae && data[1].unsigned()==1) {
            details=details.copy(foldedState=data[3].unsigned()); return null
        }
        if(data.size>=5 && data[0].unsigned()==0xac && data[1].unsigned()==1 && data[2].unsigned()==3) {
            details=details.copy(voiceCommand=data[4].toInt()); return null
        }
        if(data.size>=3 && data[0].unsigned()==0xad) return treadmillCadence(data[1].unsigned()*256+data[2].unsigned())
        require(data.size>=11 && data[0].unsigned()==0xab && data[1].unsigned() in 3..4)
        val old=data[1].unsigned()==3
        val kind=if(old) 9 else data[3].unsigned()
        val subtype=if(old) data[6].unsigned() ushr 5 else data[4].unsigned()
        require(kind in 9..12)
        // Validate the complete old-rower signal before changing connection state.
        if(old && (subtype==1 || data[6].unsigned()<7)) require(data.size>=13)
        identify(kind,subtype)
        template=data.copyOf()
        details=details.copy(battery=if(data[2].unsigned() in 0..3) data[5].unsigned() else null)
        val interval=(7..10).fold(0) { acc,i -> (acc shl 8) or data[i].unsigned() }
        lastMotion=true
        return when(machine) {
            Machine.BIKE,Machine.ELLIPTICAL -> {
                val feedback=if(data.size>13) resistance(data[13].toInt()) else null
                val cadence=if(interval==0) 0.0 else rpm(60000.0/interval/if(kind==11 && subtype==17) 2 else 1)
                bike(cadence,feedback,if(subtype==17 && data.size>14) heart(data[14].unsigned()) else null)
            }
            Machine.ROWER -> {
                val legacyMagnet=old && subtype==1
                val legacyWater=old && data[6].unsigned()<7
                if(legacyMagnet || legacyWater) { require(data.size>=13); rower(interval,data[11].unsigned()*256+data[12].unsigned(),legacyWater,legacyMagnet) }
                else rower(interval)
            }
            Machine.TREADMILL -> {
                machineStatus=data[5].unsigned()
                if(data.size>17) details=details.copy(errorCode=data[17].unsigned())
                val kmh=(data[7].unsigned()/10.0).let { if(it>20) 5.0 else it }
                treadmillSpeed=kmh/3.6
                if(subtype==18) treadmillRate=data[10].unsigned().toDouble()
                Metrics(speedMps=treadmillSpeed,inclinePercent=MobiCommands.decodeIncline(data[8].unsigned()).toDouble(),
                    heartBpm=heart(data[9].unsigned()),stepRate=treadmillRate,
                    deviceDurationSec=if(data.size>12) data[11].unsigned()*256+data[12].unsigned() else null,
                    strideM=treadmillRate?.takeIf { it>0 && treadmillSpeed>0 }?.let { treadmillSpeed*60/it },
                    distanceM=if(data.size>14) (data[13].unsigned()*256+data[14].unsigned())*10.0 else null,
                    caloriesKcal=if(data.size>16) (data[15].unsigned()*256+data[16].unsigned()).toDouble() else null)
            }
            else -> null
        }
    }
    private fun v2(characteristic: String,data: ByteArray): Metrics? {
        when(characteristic) {
            "8801" -> { require(data.isNotEmpty()); uploadMode=data[0].unsigned(); if(uploadMode in 2..3) rowing.reverseMode=false }
            "8802" -> { require(data.size>=2); identify(data[0].unsigned(),data[1].unsigned()) }
            "8803" -> { require(data.isNotEmpty()); hardwareCode=data.singleOrNull()?.unsigned() }
            "8805" -> { require(data.isNotEmpty()); magnets=data[0].unsigned() }
            "8806" -> {
                require(data.size>=3)
                explicitResistance=true
                resistanceMode=MobiProfiles.readWriteMode(data[0].unsigned())
                range=runCatching { ResistanceRange(data[1].unsigned().toDouble(),data[2].unsigned().toDouble()) }.getOrNull()
            }
            "8807" -> {
                require(data.size>=3)
                // The original uses integer division before storing its km/h bounds.
                details=details.copy(speed=MobiAxis(MobiProfiles.readWriteMode(data[0].unsigned()),
                    (data[1].unsigned()/10).toDouble(),(data[2].unsigned()/10).toDouble()))
            }
            "8808" -> {
                require(data.size>=3)
                details=details.copy(incline=MobiAxis(MobiProfiles.readWriteMode(data[0].unsigned()),
                    MobiCommands.decodeIncline(data[1].unsigned()).toDouble(),data[2].unsigned().toDouble()))
            }
            "880e" -> {
                require(data.isNotEmpty())
                if(machine==Machine.TREADMILL || uploadMode in 2..3) {
                    if(data[0].unsigned() in 0..2) machineStatus=data[0].unsigned()
                    if(machine==Machine.TREADMILL) {
                        val stopped=data.getOrNull(1)?.unsigned()==2
                        details=details.copy(emergencyStop=stopped)
                        if(stopped) machineStatus=0
                    }
                }
            }
            "8901" -> {
                require(data.isNotEmpty()); val bits=data[0].unsigned()
                details=details.copy(tmallControl=bits and 1!=0,supportFold=bits and 4!=0,
                    skippingControl=bits and 16!=0,dumbbellControl=bits and 32!=0)
            }
            "8902" -> {
                require(data.size>=3)
                details=when(data[0].unsigned()) {
                    1 -> if(data[1].unsigned()==3 && data.size>=5) details.copy(voiceCommand=data[4].toInt()) else details
                    2 -> details.copy(gamePad=data[2].toInt())
                    3 -> details.copy(foldedState=data[2].unsigned())
                    4 -> if(data[1].unsigned()==3 && data.size>=5 && data[2].unsigned() in 0..2) {
                        val code=data[2].unsigned()
                        // Both APKs use copyOfRange(3,4), whose upper bound is
                        // exclusive: the feedback callback consumes ONE byte.
                        // The outbound command still carries two bytes; do not
                        // silently "repair" that vendor asymmetry.
                        val raw=data[3].unsigned()
                        details.copy(interaction=MobiInteraction(8+code,raw.toDouble()/if(code==2) 10 else 1))
                    } else details
                    5 -> if(data[1].unsigned()==2 && data[2].unsigned()==0 && data.size>=4)
                        details.copy(interaction=MobiInteraction(11,data[3].unsigned().toDouble())) else details
                    else -> details
                }
            }
            "8812" -> {
                if(machine==Machine.DUMBBELL) {
                    require(data.size==2)
                    return Metrics(loadKg=(data[0].unsigned()*256+data[1].unsigned())/10.0)
                }
                require(data.isNotEmpty()); val feedback=resistance(data[0].toInt()) ?: return null
                return if(machine in setOf(Machine.BIKE,Machine.ELLIPTICAL) && uploadMode==1) bike(null,feedback) else Metrics(resistance=feedback)
            }
            "8811" -> {
                require(data.size>=2)
                if(uploadMode !in setOf(1,3)) return null
                val interval=data[0].unsigned()*256+data[1].unsigned()
                val second=if(data.size>=4) data[2].unsigned()*256+data[3].unsigned() else 0
                lastMotion=true
                return when(machine) {
                    Machine.ROWER -> rower(interval,second,animationOnly=uploadMode==3)
                    Machine.BIKE,Machine.ELLIPTICAL -> if(uploadMode==1) bike(if(interval==0) 0.0 else rpm(60000.0/interval/magnets,profile.subtype==0)) else null
                    else -> null
                }
            }
            "8813" -> {
                if(uploadMode !in setOf(2,3)) return null
                fun be(i: Int)=data[i].unsigned()*256+data[i+1].unsigned()
                if(machine==Machine.DUMBBELL) {
                    require(data.size==7); lastMotion=true
                    return Metrics(repetitions=be(5),deviceDurationSec=be(3),dumbbellFewActions=data[0].unsigned(),dumbbellActionNumber=data[1].unsigned())
                }
                if(machine==Machine.JUMP_ROPE) {
                    require(data.size==15); lastMotion=true
                    return Metrics(cadence=data[2].unsigned().toDouble(),deviceDurationSec=be(3),jumpCount=be(5),caloriesKcal=be(7)/10.0,continuousJumps=be(13),jumpInterruptions=be(11))
                }
                require(data.size in setOf(9,11,13))
                lastMotion=true
                val rawSpeed=data[0].unsigned()/10.0
                val frequency=data[2].unsigned().toDouble()
                val rawIncline=MobiCommands.decodeIncline(data[1].unsigned())
                if(machine==Machine.TREADMILL) {
                    treadmillSpeed=rawSpeed/3.6
                    val scale=if(hardwareCode==0x18) 10 else 1
                    val incline=if(hardwareCode==0x16 && profile.subtype==19) willIncline(rawIncline) else rawIncline
                    return Metrics(speedMps=treadmillSpeed,stepRate=frequency,inclinePercent=incline.toDouble(),
                        deviceDurationSec=be(3),
                        distanceM=be(5).toDouble()*scale,caloriesKcal=be(7)/10.0*scale,
                        strideM=if(rawSpeed>0 && frequency>0) treadmillSpeed*60/frequency else null)
                }
                val measuredPower=if(data.size>=11) be(9).toDouble().takeIf { it!=0.0 } else null
                if(machine==Machine.ROWER && data.size>=13 && be(11)>0) aggregateStrokesReported=true
                val fallback=if(machine==Machine.ROWER) AlternateCalculations.rowingPower(rawSpeed).toInt().toDouble()
                    else calculationResistance.power(frequency,range,profile.kind==11 && profile.subtype==17)?.toInt()?.toDouble()
                // MotionData.upGeneralSportData accepts m/s directly; only treadmills convert km/h.
                return Metrics(cadence=frequency,speedMps=rawSpeed,distanceM=be(5).toDouble(),caloriesKcal=be(7)/10.0,
                    deviceDurationSec=be(3),
                    powerW=measuredPower ?: fallback,powerEstimated=measuredPower==null && fallback!=null,
                    strokes=if(machine==Machine.ROWER && data.size>=13) be(11) else null,
                    inclinePercent=rawIncline.toDouble())
            }
            "8814" -> if(machine==Machine.TREADMILL && uploadMode==1) {
                require(data.size>=2)
                return treadmillCadence(data[0].unsigned()*256+data[1].unsigned())
            }
        }
        return null
    }
    private fun treadmillCadence(interval: Int): Metrics? {
        if(machine!=Machine.TREADMILL) return null
        if(interval>0) treadmillRate=30000.0/interval
        lastMotion=true
        return Metrics(stepRate=treadmillRate,strideM=treadmillRate?.takeIf { it>0 && treadmillSpeed>0 }?.let { treadmillSpeed*60/it })
    }
    private fun huantong(data: ByteArray): Metrics? {
        require(data.size==12 && data[1].toInt()!=0)
        // Vendor concatenates hexadecimal bytes, then parses DECIMAL digits (packed BCD).
        val digits="%X%02X".format(data[2],data[3])
        val value=digits.toDoubleOrNull() ?: 0.0
        lastMotion=true
        return bike(rpm(value))
    }
    fun commandedResistance(level: Int) {
        if(protocol==Protocol.HUANTONG && range?.contains(level.toDouble())==true) {
            actualResistance=level.toDouble()
            bike(null)
        }
    }
    fun resetTrainingCalculations() { calculationResistance.reset() }
    fun activeSecond() {
        if(machine !in setOf(Machine.BIKE,Machine.ELLIPTICAL)) return
        range?.let { calculationResistance.tick(actualResistance.toInt(),it.max.toInt()) }
        energyPower=calculationResistance.power(lastRpm,range,profile.kind==11 && profile.subtype==17)
    }
    fun resistanceCommand(level: Int): ByteArray? {
        if(!canWriteResistance || range?.contains(level.toDouble())!=true) return null
        return when(protocol) {
            Protocol.V1 -> template?.let { byteArrayOf(0xab.toByte(),3,0,it[3],it[4],level.toByte(),it[6]) }
            Protocol.V2 -> Protocols.v2Resistance(level)
            Protocol.HUANTONG -> MobiCommands.huantongResistance(level)
            else -> null
        }
    }
    companion object {
        fun heart(value: Int): Int? = (if(value>300) value%300 else if(value in 30..300) value else 0).takeIf { it>0 }
        fun willIncline(value: Int): Int = when {
            value<0 -> -6
            value<=16 -> value
            value>=26 -> 36
            else -> (value-16)*2+16
        }
    }
}
