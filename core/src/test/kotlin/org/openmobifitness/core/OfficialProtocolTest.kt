package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class OfficialProtocolTest {
    private fun table(name: String): List<Map<String,String>> {
        val lines=javaClass.getResourceAsStream("/oracle/$name-intl-2.1.14.tsv")!!.bufferedReader().readLines().filterNot { it.startsWith('#') }
        val header=lines.first().split('\t')
        return lines.drop(1).map { header.zip(it.split('\t')).toMap() }
    }
    private fun String.bytes()=chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun remainingCapabilityAndStateCallbacksMatchBothOriginalApks() {
        var scenario=""; var packetIndex=""; var s=MobiSession(Protocol.UNKNOWN,"")
        table("status").forEach { r ->
            if(scenario!=r.getValue("scenario")) {
                scenario=r.getValue("scenario"); packetIndex=""
                s=MobiSession(Protocol.valueOf(r.getValue("protocol")),"")
                if(s.protocol==Protocol.V2) {
                    s.receive("8802",byteArrayOf(r.getValue("kind").toByte(),r.getValue("subtype").toByte()))
                    s.receive("8801",byteArrayOf(r.getValue("upload").toByte()))
                }
            }
            if(packetIndex!=r.getValue("packetIndex")) {
                packetIndex=r.getValue("packetIndex")
                s.receive(r.getValue("characteristic"),r.getValue("packet").bytes())
            }
            val actual: Any?=when(r.getValue("field")) {
                "SpeedAdjustmentMethod" -> s.details.speed?.mode
                "LowSpeed" -> s.details.speed?.minimum?.toInt()
                "HighSpeed" -> s.details.speed?.maximum?.toInt()
                "InclineAdjustmentMethod" -> s.details.incline?.mode
                "InclineMin" -> s.details.incline?.minimum?.toInt()
                "InclineMax" -> s.details.incline?.maximum?.toInt()
                "SupportFold" -> s.details.supportFold
                "SkippingRopControl" -> s.details.skippingControl
                "DumbbellControl" -> s.details.dumbbellControl
                "FoldedState" -> s.details.foldedState
                "machineStatus" -> s.machineStatus
                else -> error(r.toString())
            }
            assertEquals(r.toString(),r.getValue("expected"),actual.toString())
        }
        table("fold").forEach { r ->
            val state=r.getValue("folded").toInt()
            val bytes=if(r["handler"]=="V1Handler") MobiCommands.v1Treadmill(7,if(state==1) 0 else 1) else MobiCommands.v2Fold(state)
            assertArrayEquals(r.toString(),r.getValue("bytes").bytes(),bytes)
        }
    }
    @Test fun optionalTypeOneAndAggregateRowingFormulasMatchOriginalDex() {
        table("alternate").forEach { r ->
            val rpm=r.getValue("rpm").toDouble(); val max=r.getValue("maximum").toInt(); val level=r.getValue("level").toInt()
            assertEquals(r.toString(),r.getValue("power").toDouble(),AlternateCalculations.power(rpm,level,max),1e-9)
            assertEquals(r.toString(),r.getValue("kcalPerHour").toDouble(),AlternateCalculations.caloriesPerHour(rpm,level,max),1e-9)
        }
        table("row-math").forEach { r ->
            val speed=r.getValue("speed").toDouble(); val model=r.getValue("model").toInt()
            val power=AlternateCalculations.rowingPower(speed,model)
            assertEquals(r.toString(),r.getValue("power").toDouble(),power,1e-9)
            assertEquals(r.toString(),r.getValue("kcalPerSecond").toDouble(),AlternateCalculations.rowingCaloriesPerSecond(power,model),1e-9)
        }
    }
    @Test fun writesMatchBytesQueuedByUnmodifiedVendorHandlers() {
        table("commands").forEach { r ->
            val command=r.getValue("command").toInt(); val value=r.getValue("value").toInt()
            val handler=r.getValue("handler")
            val bytes=if(r["unlock"]=="true") MobiCommands.unlock else when(handler) {
                "V1Handler" -> if(command==4) MobiSession(Protocol.V1,"").let { it.receive("ffe4",r.getValue("template").bytes()); it.resistanceCommand(value)!! }
                    else MobiCommands.v1Treadmill(command,value)
                "V2Handler" -> when(command) { 1 -> MobiCommands.v2Status(value); 2 -> MobiCommands.v2Speed(value); 3 -> MobiCommands.v2Incline(value); 4 -> Protocols.v2Resistance(value); else -> MobiCommands.v2EquipmentTarget(command,value) ?: byteArrayOf() }
                "FtmsHandler" -> when(command) { 1 -> MobiCommands.ftmsStatus(value); 2 -> MobiCommands.ftmsSpeed(value); 3 -> MobiCommands.ftmsIncline(value); else -> MobiCommands.ftmsResistance(value.toDouble()) }
                "HuanTongHandler" -> MobiCommands.huantongResistance(value)
                else -> error(handler)
            }
            assertArrayEquals(r.toString(),r.getValue("bytes").bytes(),bytes)
        }
    }
    @Test fun calculationResistanceFollowsOriginalMotionDataEveryActiveSecond() {
        var scenario=""; var model=LegacyResistance()
        table("resistance").forEach { r ->
            if(scenario!=r.getValue("scenario")) { scenario=r.getValue("scenario"); model=LegacyResistance() }
            val max=r.getValue("maximum").toInt()
            if(r.getValue("tick")=="true") model.tick(r.getValue("actual").toInt(),max)
            assertEquals(r.toString(),r.getValue("target").toInt(),model.level)
            val watts=model.power(r.getValue("rpm").toDouble(),ResistanceRange(1.0,max.toDouble()),r["kind"]=="2" && r["subtype"]=="17")!!
            assertEquals(r.toString(),r.getValue("power").toDouble(),watts,1e-9)
            assertEquals(r.toString(),r.getValue("kcalPerSecond").toDouble(),Estimates.legacyKcal(watts,r.getValue("weight").toDouble(),1000),1e-10)
        }
    }
    @Test fun powerAndCaloriesMatchOriginalDexAcrossModelsRangesWeightsAndCadences() {
        table("power").forEach { r ->
            val rpm=r.getValue("rpm").toDouble(); val level=r.getValue("level").toDouble()
            val watts=Estimates.legacyPower(rpm,level,ResistanceRange(1.0,r.getValue("maximum").toDouble()),r["kind"]=="2" && r["subtype"]=="17",false)!!
            assertEquals(r.toString(),r.getValue("power").toDouble(),watts,1e-9)
            assertEquals(r.toString(),r.getValue("kcalPerSecond").toDouble(),Estimates.legacyKcal(watts,r.getValue("weight").toDouble(),1000),1e-10)
        }
    }
    @Test fun everyWireByteMatchesOfficialModeAndSignedInclineDecoding() {
        table("wire").forEach { r ->
            val b=r.getValue("byte").toInt()
            assertEquals(r.toString(),r.getValue("mode").toInt(),MobiProfiles.readWriteMode(b))
            assertEquals(r.toString(),r.getValue("incline").toInt(),MobiCommands.decodeIncline(b))
            assertEquals(r.toString(),r.getValue("will").toInt(),MobiSession.willIncline(b-128))
        }
    }
    @Test fun v2ModelCapabilitiesMatchOriginalDeviceIdentificationCallbacks() {
        table("profiles").forEach { r ->
            val profile=MobiProfiles.identify(Protocol.valueOf(r.getValue("protocol")),r.getValue("kind").toInt(),r.getValue("subtype").toInt())
            assertEquals(r.toString(),r.getValue("mode").toInt(),profile.resistanceMode)
            assertEquals(r.toString(),r.getValue("maximum").toDouble(),profile.range?.max ?: 24.0,0.0)
        }
    }
    @Test fun packetCallbacksMatchOriginalDexWithTheSameOrderedInputs() {
        var scenario=""; var packetIndex=""; var session=MobiSession(Protocol.UNKNOWN,""); var metrics: Metrics?=null
        (table("packets")+table("equipment").map { it+("scenario" to "equipment-${it.getValue("scenario")}") }).forEach { r ->
            if(scenario!=r.getValue("scenario")) {
                scenario=r.getValue("scenario"); packetIndex=""
                val protocol=Protocol.valueOf(r.getValue("protocol")); val kind=r.getValue("kind").toInt(); val subtype=r.getValue("subtype").toInt()
                session=MobiSession(protocol,if(kind==11) "MOBI-E" else "MOBI-B")
                if(protocol==Protocol.V2) {
                    session.receive("8802",byteArrayOf(kind.toByte(),subtype.toByte()))
                    session.receive("8801",byteArrayOf(r.getValue("upload").toByte()))
                    session.receive("8805",byteArrayOf(r.getValue("magnets").toByte()))
                    if(r.getValue("hardware").isNotEmpty()) session.receive("8803",r.getValue("hardware").bytes())
                }
            }
            if(packetIndex!=r.getValue("packetIndex")) {
                packetIndex=r.getValue("packetIndex")
                metrics=session.receive(r.getValue("characteristic"),r.getValue("packet").bytes())
            }
            assertNotNull(r.toString(),metrics)
            val value=Metrics::class.java.getMethod("get${r.getValue("field")}").invoke(metrics)
            assertNotNull(r.toString(),value)
            assertEquals(r.toString(),r.getValue("expected").toDouble(),(value as Number).toDouble(),1e-9)
        }
    }
    @Test fun ftmsOptionalFieldsAndBothCrossTrainerHeaderSizesMatchOriginalDex() {
        table("ftms").forEach { r ->
            val m=Protocols.ftmsCompatibleData(r.getValue("characteristic"),r.getValue("packet").bytes(),r.getValue("name"))
            assertNotNull(r.toString(),m)
            val value=Metrics::class.java.getMethod("get${r.getValue("field")}").invoke(m)
            assertNotNull(r.toString(),value)
            assertEquals(r.toString(),r.getValue("expected").toDouble(),(value as Number).toDouble(),1e-9)
        }
    }
    @Test fun ftmsHandlerConversionsMatchOriginalCallbacksAfterFieldDecoding() {
        table("ftms-handler").forEach { r ->
            val m=MobiFtmsSession(r.getValue("name")).receive(r.getValue("characteristic"),r.getValue("packet").bytes())
            assertNotNull(r.toString(),m)
            val value=Metrics::class.java.getMethod("get${r.getValue("field")}").invoke(m)
            assertNotNull(r.toString(),value)
            assertEquals(r.toString(),r.getValue("expected").toDouble(),(value as Number).toDouble(),1e-9)
        }
    }
    @Test fun ftmsStateAndSmallEquipmentInteractionsMatchOriginalCallbacks() {
        val ftms=MobiFtmsSession("MB-T")
        table("ftms-status").forEach { r ->
            ftms.metadata(r.getValue("characteristic"),r.getValue("packet").bytes())
            val value=when(r.getValue("field")) {
                "LowSpeed" -> ftms.details.speed!!.minimum
                "HighSpeed" -> ftms.details.speed!!.maximum
                "InclineMin" -> ftms.details.incline!!.minimum
                "InclineMax" -> ftms.details.incline!!.maximum
                "machineStatus" -> ftms.machineStatus.toDouble()
                else -> error(r.toString())
            }
            assertEquals(r.toString(),r.getValue("expected").toDouble(),value,0.0)
        }
        val s=MobiSession(Protocol.V2,"")
        table("interactions").forEach { r ->
            s.receive("8902",r.getValue("packet").bytes())
            val value: Number?=when(r.getValue("field")) {
                "command" -> s.details.interaction?.command
                "value" -> s.details.interaction?.value
                "voiceCommand" -> s.details.voiceCommand
                "gamePad" -> s.details.gamePad
                else -> error(r.toString())
            }
            assertNotNull(r.toString(),value)
            assertEquals(r.toString(),r.getValue("expected").toDouble(),value!!.toDouble(),0.0)
        }
    }
    @Test fun ftmsMissingPowerUsesOriginalMotionDataCalculationRamp() {
        var scenario=""; var model=MobiFtmsSession("MB-B")
        table("resistance").filter { it["kind"]=="1" }.forEach { r ->
            if(scenario!=r.getValue("scenario")) {
                scenario=r.getValue("scenario"); model=MobiFtmsSession("MB-B")
                model.range=ResistanceRange(1.0,r.getValue("maximum").toDouble())
            }
            val cadence=r.getValue("rpm").toInt()*2
            // Speed, cadence, resistance and an explicitly zero power field.
            val packet=byteArrayOf(0x64,0,0x84.toByte(),3,cadence.toByte(),(cadence ushr 8).toByte(),r.getValue("actual").toByte(),0,0,0)
            model.receive("2ad2",packet)
            if(r["tick"]=="true") model.activeSecond()
            val m=model.receive("2ad2",packet)!!
            assertTrue(m.powerEstimated)
            assertEquals(r.toString(),r.getValue("power").toDouble().toInt().toDouble(),m.powerW!!,0.0)
            assertEquals(r.toString(),r.getValue("power").toDouble(),model.energyPower!!,1e-9)
        }
    }
}
