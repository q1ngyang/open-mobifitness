package org.openmobifitness.core

import kotlin.math.roundToInt

/** FtmsHandler -> MotionData conversion for identified MOBI equipment. Generic
 * FTMS retains the standard sensor fields without these vendor calculations. */
class MobiFtmsSession(private val name: String) {
    private val calculationResistance=LegacyResistance()
    private var actualResistance=1
    private var cadence: Double?=null
    private var machine=Machine.UNKNOWN
    var range: ResistanceRange?=null
    var energyPower: Double?=null; private set
    var energyRate: Double?=null; private set
    var machineStatus=0; private set
    var details=MobiDeviceDetails(); private set
    fun metadata(characteristic: String,data: ByteArray) {
        fun le(offset: Int)=(data[offset].toInt() and 255) or ((data[offset+1].toInt() and 255) shl 8)
        when(characteristic) {
            "2ad4" -> if(data.size>=4) details=details.copy(speed=MobiAxis(details.speed?.mode ?: 3,
                (le(0)*0.01).roundToInt().toDouble(),(le(2)*0.01).roundToInt().toDouble()))
            "2ad5" -> if(data.size>=4) details=details.copy(incline=MobiAxis(details.incline?.mode ?: 2,
                (le(0).toShort()*0.1).roundToInt().toDouble(),(le(2).toShort()*0.1).roundToInt().toDouble()))
            "2acc" -> if(data.size>=8) {
                val speed=details.speed ?: MobiAxis(3,0.0,12.0)
                val incline=details.incline ?: MobiAxis(2,0.0,12.0)
                details=details.copy(speed=speed.copy(mode=if(data[4].toInt() and 1!=0) 3 else speed.mode),
                    incline=incline.copy(mode=if(data[4].toInt() and 2!=0) 3 else if(data[0].toInt() and 8!=0) 2 else incline.mode))
            }
            "2ada" -> if(data.isNotEmpty()) when(data[0].toInt() and 255) {
                2 -> when(data.getOrNull(1)?.toInt()) { 1 -> machineStatus=0; 2 -> machineStatus=2 }
                3 -> { machineStatus=0; details=details.copy(emergencyStop=true) }
                4 -> { machineStatus=1; details=details.copy(emergencyStop=false) }
            }
        }
    }
    fun resetTrainingCalculations() { calculationResistance.reset() }
    fun activeSecond() {
        if(machine in setOf(Machine.BIKE,Machine.ELLIPTICAL)) {
            calculationResistance.tick(actualResistance,(range?.max ?: 24.0).toInt())
            energyPower=calculationResistance.power(cadence,range ?: ResistanceRange(1.0,24.0),false)
        }
    }
    fun receive(characteristic: String,data: ByteArray): Metrics? {
        val m=Protocols.ftmsCompatibleData(characteristic,data,name,handlerValues=true) ?: return null
        machine=when(characteristic) { "2ace" -> Machine.ELLIPTICAL; "2ad2" -> Machine.BIKE; "2ad1" -> Machine.ROWER; else -> Machine.TREADMILL }
        m.resistance?.takeIf { it>0 && (range?.contains(it)!=false) }?.let { actualResistance=it.toInt() }
        cadence=m.cadence ?: cadence
        val calculated=when(machine) {
            Machine.BIKE,Machine.ELLIPTICAL -> calculationResistance.power(cadence,range ?: ResistanceRange(1.0,24.0),false).also { energyPower=it }
            Machine.ROWER -> m.speedMps?.let { AlternateCalculations.rowingPower(it) }.also {
                energyRate=(m.powerW?.takeIf { watts -> watts!=0.0 } ?: it)?.let { watts -> AlternateCalculations.rowingCaloriesPerSecond(watts) }
            }
            else -> null
        }
        return if(m.powerW==null || m.powerW==0.0) m.copy(powerW=calculated?.toInt()?.toDouble() ?: m.powerW,powerEstimated=calculated!=null) else m
    }
}
