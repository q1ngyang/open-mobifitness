package org.openmobifitness.core

/** Original OpenMOBI sessions, informed by the sources in docs/BUILTIN_WORKOUTS.md.
 * Levels are device-range percentages, never a physiological intensity prescription.
 * Treadmill targets are guidance only; no motor, speed or incline commands are added.
 */
object BuiltinPrograms {
    // Presentation order is independent of schedules: its first 14 entries define
    // the existing rowing/treadmill catalog, not a difficulty or display order.
    private val displayOrder=listOf(
        "warmup","recovery","cooldown",
        "light","moderate","vigorous",
        "steady20","steady30","endurance",
        "weight","cardio40","progressive",
        "pyramid20","pyramid30","strength",
        "interval10","interval20","interval30",
        "hiit","hiit30","hiit40"
    ).withIndex().associate { it.value to it.index }
    private data class Block(val minutes: Double,val effort: Int,val recovery: Boolean=false)
    private fun b(minutes: Number,effort: Int,recovery: Boolean=false)=Block(minutes.toDouble(),effort,recovery)
    private fun intervals(warm: Int,rounds: Int,work: Double,easy: Double,effort: Int=3)=
        listOf(b(warm,0))+List(rounds) { listOf(b(work,effort),b(easy,0,true)) }.flatten()+b(warm,0)
    private val schedules=linkedMapOf(
        "warmup" to listOf(b(2,0),b(2,1),b(2,1),b(2,2)),
        "recovery" to listOf(b(2,0),b(6,1),b(2,0)),
        "steady20" to listOf(b(5,0),b(10,2),b(5,0)),
        "steady30" to listOf(b(5,0),b(20,2),b(5,0)),
        "endurance" to listOf(b(5,0),b(35,1),b(5,0)),
        // A short technique block, with separate warm-up/cool-down explained in the UI.
        "interval10" to intervals(2,3,1.0,1.0,2),
        "interval20" to intervals(5,5,1.0,1.0),
        "interval30" to intervals(5,4,3.0,2.0),
        "pyramid20" to listOf(b(5,0),b(2,1),b(2,2),b(2,3),b(2,2),b(2,1),b(5,0)),
        "pyramid30" to listOf(b(5,0),b(4,1),b(4,2),b(4,3),b(4,2),b(4,1),b(5,0)),
        "progressive" to listOf(b(5,0),b(5,1),b(5,2),b(5,3),b(5,0)),
        "cooldown" to listOf(b(2,2),b(2,1),b(2,1),b(2,0)),
        "light" to listOf(b(5,0),b(10,1),b(5,0)),
        "moderate" to listOf(b(5,0),b(20,2),b(5,0)),
        "vigorous" to listOf(b(5,0),b(5,2),b(10,4),b(5,2),b(5,0)),
        "strength" to intervals(5,3,2.0,2.0,4),
        "weight" to listOf(b(5,0),b(10,2),b(10,3),b(10,2),b(5,0)),
        "hiit" to intervals(5,5,1.0,1.0,4),
        "cardio40" to listOf(b(5,0),b(5,2),b(10,3),b(10,4),b(5,2),b(5,0)),
        "hiit30" to intervals(5,10,1.0,1.0,4),
        "hiit40" to (listOf(b(5,0),b(5,2))+List(4) { listOf(b(3,4),b(2,0,true)) }.flatten()+listOf(b(5,2),b(5,0)))
    )
    private fun kind(id: String,index: Int,last: Int,recovery: Boolean)=when {
        id=="warmup" -> StageKind.WARMUP
        id=="cooldown" -> StageKind.COOLDOWN
        index==0 -> StageKind.WARMUP
        index==last -> StageKind.COOLDOWN
        recovery || id=="recovery" -> StageKind.RECOVERY
        else -> StageKind.TRAINING
    }
    private fun originalElliptical(): List<Workout> = Presets.all.map { w ->
        // Retain stable identifiers and familiar curves; classify the low interval phases.
        val source=when(w.id) {
            "interval20" -> intervals(5,5,1.0,1.0).map { Step(target=it.minutes*60,resistancePercent=if(it.effort==0) 10 else 40) }
            "pyramid20" -> schedules.getValue("pyramid20").map { Step(target=it.minutes*60,resistancePercent=listOf(10,20,30,40,50)[it.effort]) }
            else -> w.steps
        }
        val steps=source.mapIndexed { index,step ->
            val previous=source.getOrNull(index-1)?.resistancePercent
            val next=source.getOrNull(index+1)?.resistancePercent
            val recovery=previous!=null && next!=null && step.resistancePercent!!<previous && step.resistancePercent<next
            step.copy(id="${w.id}-elliptical-$index",kind=kind(w.id,index,source.lastIndex,recovery),frequency=StageRange(HintMode.OFF),heart=StageRange(HintMode.OFF))
        }
        w.copy(id="${w.id}_elliptical",machine=Machine.ELLIPTICAL,steps=steps)
    }
    private fun authored(machine: Machine): List<Workout> = schedules.entries.filter { machine==Machine.BIKE || it.key in schedules.keys.take(14) }.map { (id,blocks) ->
        val name=machine.name.lowercase()
        val steps=blocks.mapIndexed { index,block ->
            val stageKind=kind(id,index,blocks.lastIndex,block.recovery)
            val low=stageKind in setOf(StageKind.WARMUP,StageKind.RECOVERY,StageKind.COOLDOWN)
            val rate=when(machine) {
                Machine.BIKE -> listOf(60.0 to 75.0,65.0 to 80.0,70.0 to 85.0,80.0 to 95.0,85.0 to 100.0)[block.effort]
                Machine.ROWER -> listOf(18.0 to 20.0,18.0 to 22.0,20.0 to 24.0,22.0 to 26.0,24.0 to 28.0)[block.effort]
                else -> null
            }
            Step(target=block.minutes*60,id="$id-$name-$index",kind=stageKind,
                resistancePercent=if(machine==Machine.BIKE) listOf(8,16,24,32,40)[block.effort] else null,
                frequency=if(low || rate==null) StageRange(HintMode.OFF) else StageRange(HintMode.CUSTOM,rate.first,rate.second),
                heart=StageRange(HintMode.OFF),
                speedTargetMps=if(machine==Machine.TREADMILL) listOf(3.0,4.0,5.0,6.0,7.5)[block.effort]/3.6 else null,
                inclineTargetPercent=if(machine==Machine.TREADMILL) 0.0 else null)
        }
        Workout(id="${id}_$name",title=id,steps=steps,builtin=true,machine=machine)
    }
    private val all: Map<Machine,List<Workout>> by lazy {
        mapOf(Machine.ELLIPTICAL to originalElliptical(),Machine.BIKE to authored(Machine.BIKE),Machine.ROWER to authored(Machine.ROWER),Machine.TREADMILL to authored(Machine.TREADMILL))
            .mapValues { (_,plans) -> plans.sortedBy { displayOrder.getValue(it.id.substringBeforeLast('_')) } }
    }
    fun forMachine(machine: Machine): List<Workout> = all[machine].orEmpty()
}
