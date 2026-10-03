package org.openmobifitness.core

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class Archive(val sessions: List<Session> = emptyList(), val samples: List<Sample> = emptyList(), val workouts: List<Workout> = emptyList())
class ImportProblem(val row: Int, val reason: String) : IllegalArgumentException("$row:$reason")

object Csv {
    /** Bounded row parser for large files. Limits cells/columns, never total row count. */
    fun stream(reader: java.io.Reader,consume: (List<String>)->Unit) {
        val input=java.io.PushbackReader(reader.buffered(),1)
        var row=mutableListOf<String>(); val cell=StringBuilder(); var quoted=false; var afterQuote=false; var line=1; var first=true
        fun endCell() { row.add(cell.toString()); cell.setLength(0); afterQuote=false; if(row.size>32) throw ImportProblem(line,"size") }
        fun endRow() { endCell(); if(row.any { it.isNotEmpty() }) { consume(row); line++ }; row=mutableListOf() }
        while(true) {
            val code=input.read(); if(code<0) break
            val c=code.toChar(); if(first) { first=false; if(c=='\uFEFF') continue }
            if(quoted) {
                if(c=='"') { val next=input.read(); if(next=='"'.code) cell.append('"') else { quoted=false; afterQuote=true; if(next>=0) input.unread(next) } }
                else cell.append(c)
            } else when(c) {
                '"' -> { if(cell.isNotEmpty() || afterQuote) throw ImportProblem(line,"quote"); quoted=true }
                ',' -> endCell()
                '\r','\n' -> { if(c=='\r') { val next=input.read(); if(next>=0 && next!='\n'.code) input.unread(next) }; endRow() }
                else -> { if(afterQuote) throw ImportProblem(line,"quote"); cell.append(c) }
            }
            if(cell.length>4096) throw ImportProblem(line,"size")
        }
        if(quoted) throw ImportProblem(line,"quote")
        if(cell.isNotEmpty() || row.isNotEmpty() || afterQuote) endRow()
    }
    fun write(rows: List<List<String>>): String = "\uFEFF" + rows.joinToString("\r\n", postfix="\r\n") { row ->
        row.joinToString(",") { value -> if(value.any { it in ",\"\r\n" }) "\"${value.replace("\"","\"\"") }\"" else value }
    }
    fun read(text: String): List<List<String>> {
        require(text.length <= 32 * 1024 * 1024) { "file_too_large" }
        val t = text.removePrefix("\uFEFF")
        val result = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var afterQuote = false; var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0); afterQuote=false }
        fun endRow() { endCell(); if(row.any { it.isNotEmpty() }) result.add(row); row=mutableListOf(); require(result.size <= 200_001) }
        while(i < t.length) {
            val c=t[i++]
            if(quoted) {
                if(c=='"') { if(i<t.length && t[i]=='"') { cell.append('"'); i++ } else { quoted=false; afterQuote=true } }
                else cell.append(c)
            } else when(c) {
                '"' -> { if(cell.isNotEmpty() || afterQuote) throw ImportProblem(result.size+1,"quote"); quoted=true }
                ',' -> endCell()
                '\r','\n' -> { if(c=='\r' && i<t.length && t[i]=='\n') i++; endRow() }
                else -> { if(afterQuote) throw ImportProblem(result.size+1,"quote"); cell.append(c) }
            }
            if(cell.length > 4096 || row.size > 32) throw ImportProblem(result.size+1,"size")
        }
        if(quoted) throw ImportProblem(result.size+1,"quote")
        if(cell.isNotEmpty() || row.isNotEmpty() || afterQuote) endRow()
        return result
    }
}

object Exchange {
    private val sessionsHeader = "schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status".split(',')
    private val samplesHeader = "schema,session_id,elapsed_ms,cadence_rpm,resistance,speed_mps,distance_m,heart_bpm,power_w,strokes".split(',')
    private val sessionsV2 = sessionsHeader + listOf("calories_kcal","calories_estimated","distance_estimated","weight_kg","met")
    private val sessionsV4 = sessionsV2 + listOf("workout_id","workout_title","archived")
    private val sessionsV5 = sessionsV4 + "energy_model"
    private val samplesV2 = samplesHeader + listOf("calories_kcal","incline_percent","stride_m","force_n","step_rate","step_count","target_cadence")
    private val samplesV3 = samplesV2 + "power_estimated"
    private val samplesV6 = samplesV3 + listOf("jump_count","continuous_jumps","jump_interruptions","repetitions","load_kg","device_duration_sec","dumbbell_few_actions","dumbbell_action_number")
    private val workoutsHeader = "schema,workout_id,title,step_index,condition,target,resistance_percent".split(',')
    private fun text(v: String) = "'$v" // Always escape text, including an original leading apostrophe; reversible.
    private fun untext(v: String) = v.removePrefix("'")
    private fun Any?.cell() = this?.toString() ?: ""
    fun sessions(items: List<Session>) = Csv.write(listOf(sessionsV5) + items.map {
        listOf("5",it.id,it.start,it.end,it.zone,text(it.device),it.machine.name,it.protocol.name,it.elapsedMs.toString(),it.distanceM.cell(),it.demo.toString(),it.status,it.caloriesKcal.cell(),it.caloriesEstimated.toString(),it.distanceEstimated.toString(),it.weightKg.cell(),it.met.cell(),text(it.workoutId),text(it.workoutTitle),it.archived.toString(),it.energyModel)
    })
    fun samples(items: List<Sample>) = Csv.write(listOf(samplesV6) + items.map { s -> s.metrics.let {
        listOf("6",s.sessionId,s.elapsedMs.toString(),it.cadence.cell(),it.resistance.cell(),it.speedMps.cell(),it.distanceM.cell(),it.heartBpm.cell(),it.powerW.cell(),it.strokes.cell(),it.caloriesKcal.cell(),it.inclinePercent.cell(),it.strideM.cell(),it.forceN.cell(),it.stepRate.cell(),it.stepCount.cell(),it.targetCadence.cell(),it.powerEstimated.toString(),it.jumpCount.cell(),it.continuousJumps.cell(),it.jumpInterruptions.cell(),it.repetitions.cell(),it.loadKg.cell(),it.deviceDurationSec.cell(),it.dumbbellFewActions.cell(),it.dumbbellActionNumber.cell())
    } })
    fun workouts(items: List<Workout>) = Csv.write(listOf(workoutsHeader) + items.flatMap { w -> w.steps.mapIndexed { i,s ->
        listOf("1",w.id,text(w.title),i.toString(),s.condition.name,s.target.toString(),s.resistancePercent.cell())
    } })
    fun parse(content: String): Archive {
        val rows=Csv.read(content); if(rows.isEmpty()) throw ImportProblem(1,"header")
        val header=rows.first()
        if(header !in listOf(sessionsHeader,samplesHeader,sessionsV2,sessionsV4,sessionsV5,samplesV2,samplesV3,samplesV6,workoutsHeader)) throw ImportProblem(1,"header")
        val sessions=mutableListOf<Session>(); val samples=mutableListOf<Sample>()
        val sessionIds=HashSet<String>()
        val steps=linkedMapOf<String,Pair<String,MutableList<Step>>>()
        rows.drop(1).forEachIndexed { index,r ->
            try {
                require(r.size==header.size && r[0]==when(header) { samplesV6 -> "6"; sessionsV5 -> "5"; sessionsV4 -> "4"; samplesV3 -> "3"; sessionsV2,samplesV2 -> "2"; else -> "1" })
                fun number(i: Int,max: Double,min: Double=0.0): Double? = r[i].takeIf { it.isNotEmpty() }?.toDouble()?.also { require(it.isFinite() && it in min..max) }
                fun integer(i: Int,max: Int): Int? = r[i].takeIf { it.isNotEmpty() }?.toInt()?.also { require(it in 0..max) }
                fun bool(i: Int): Boolean { require(r[i] in listOf("true","false")); return r[i].toBoolean() }
                fun ms(i: Int) = r[i].toLong().also { require(it in 0..604800000L) }
                when(header) {
                    sessionsHeader,sessionsV2,sessionsV4,sessionsV5 -> {
                        UUID.fromString(r[1]); Instant.parse(r[2]); if(r[3].isNotEmpty()) Instant.parse(r[3]); ZoneId.of(r[4])
                        require(r[10] in listOf("true","false") && r[11] in listOf("active","completed","interrupted","stopped"))
                        val s=Session(r[1],r[2],r[3],r[4],untext(r[5]),Machine.valueOf(r[6]),Protocol.valueOf(r[7]),ms(8),number(9,10_000_000.0),r[10].toBoolean(),r[11],
                            if(header in listOf(sessionsV2,sessionsV4,sessionsV5)) number(12,100000.0) else null,header in listOf(sessionsV2,sessionsV4,sessionsV5) && bool(13),header in listOf(sessionsV2,sessionsV4,sessionsV5) && bool(14),
                            if(header in listOf(sessionsV2,sessionsV4,sessionsV5)) number(15,300.0,20.0) else null,if(header in listOf(sessionsV2,sessionsV4,sessionsV5)) number(16,20.0,1.0) else null,
                            if(header in listOf(sessionsV4,sessionsV5)) untext(r[17]) else "",if(header in listOf(sessionsV4,sessionsV5)) untext(r[18]) else "",header in listOf(sessionsV4,sessionsV5) && bool(19),if(header==sessionsV5) r[20].also { require(it in setOf("","device","legacy-v1","legacy-mobi","legacy-rowing","met","mixed")) } else "")
                        require(s.workoutId.length<=80 && s.workoutTitle.length<=120 && s.device.length<=120 && sessionIds.add(s.id)); sessions.add(s)
                    }
                    samplesHeader,samplesV2,samplesV3,samplesV6 -> {
                        UUID.fromString(r[1]); val time=ms(2)
                        val extended=header!=samplesHeader
                        val estimatedPower=header in listOf(samplesV3,samplesV6) && bool(17)
                        // The legacy model can exceed SINT16 near its upper cadence bound.
                        // Keep our own v3 exports round-trippable without relaxing measured FTMS values.
                        val power=number(8,if(estimatedPower) 50000.0 else 32767.0,if(estimatedPower) 0.0 else -32768.0)
                        samples.add(Sample(r[1],time,Metrics(number(3,1000.0),number(4,3276.7),number(5,100.0),number(6,10_000_000.0),integer(7,255),power,integer(9,10_000_000),
                            if(extended) number(10,100000.0) else null,if(extended) number(11,100.0,-100.0) else null,
                            if(extended) number(12,10.0) else null,if(extended) number(13,32767.0,-32768.0) else null,
                            if(extended) number(14,1000.0) else null,if(extended) integer(15,10_000_000) else null,
                            if(extended) number(16,300.0) else null,estimatedPower,
                            if(header==samplesV6) integer(18,10_000_000) else null,if(header==samplesV6) integer(19,65535) else null,
                            if(header==samplesV6) integer(20,10_000_000) else null,if(header==samplesV6) integer(21,10_000_000) else null,
                            if(header==samplesV6) number(22,6553.5) else null,if(header==samplesV6) integer(23,65535) else null,
                            if(header==samplesV6) integer(24,255) else null,if(header==samplesV6) integer(25,255) else null)))
                    }
                    workoutsHeader -> {
                        require(r[1].matches(Regex("[A-Za-z0-9_-]{1,80}")))
                        val title=untext(r[2]); val pair=steps.getOrPut(r[1]) { title to mutableListOf() }
                        require(pair.first==title && r[3].toInt()==pair.second.size && pair.second.size<200)
                        pair.second.add(Step(Condition.valueOf(r[4]),r[5].toDouble(),integer(6,100)))
                    }
                }
            } catch(e: Exception) { throw ImportProblem(index+2,"value") }
        }
        val keys=HashSet<Pair<String,Long>>()
        samples.forEach { require(keys.add(it.sessionId to it.elapsedMs)) { "duplicate_sample" } }
        val workouts=steps.map { (id,pair) -> Workout(id,pair.first,pair.second) }
        return Archive(sessions,samples,workouts)
    }
    fun validateReferences(archive: Archive, existing: List<Session>) {
        val ids=(existing+archive.sessions).associateBy { it.id }
        archive.samples.forEach { require(ids.containsKey(it.sessionId) && it.elapsedMs <= ids.getValue(it.sessionId).elapsedMs) { "sample_reference" } }
    }
}
