package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class BuiltinProgramsTest {
    @Test fun catalogIsTypedRoundTrippableAndKeepsStableFavorites() {
        val all=WorkoutPolicy.supported.flatMap(WorkoutPolicy::templates)
        assertEquals(70,all.size); assertEquals(all.size,all.map { it.id }.distinct().size)
        all.forEach { w ->
            WorkoutPolicy.validate(w)
            assertEquals(w.copy(builtin=false),Exchange.parse(Exchange.workouts(listOf(w))).workouts.single())
            assertEquals(w.steps.size,w.steps.map { it.id }.distinct().size)
            assertTrue(w.steps.all { it.target>0 })
            assertFalse(w.hints.heart.enabled); assertFalse(w.hints.sound); assertFalse(w.hints.vibration)
        }
        assertTrue(WorkoutPolicy.favorites(setOf("warmup")).all { id -> all.any { it.id==id } })
    }
    @Test fun namedDurationsAndRecoveryPhasesAreAccurate() {
        val minutes=mapOf("warmup" to 8,"recovery" to 10,"steady20" to 20,"steady30" to 30,"endurance" to 45,"interval10" to 10,"interval20" to 20,"interval30" to 30,"pyramid20" to 20,"pyramid30" to 30,"progressive" to 25,"cooldown" to 8,"light" to 20,"moderate" to 30,"hiit" to 20,"hiit30" to 30,"hiit40" to 40)
        WorkoutPolicy.supported.forEach { machine -> WorkoutPolicy.templates(machine).forEach { w ->
            val name=w.id.substringBeforeLast('_')
            minutes[name]?.let { assertEquals(w.id,it*60.0,w.steps.sumOf { it.target },0.0) }
            if(name in listOf("interval20","pyramid20")) { assertEquals(300.0,w.steps.first().target,0.0); assertEquals(300.0,w.steps.last().target,0.0) }
            if(name.startsWith("interval")) assertTrue(w.steps.any { it.kind==StageKind.RECOVERY })
            w.steps.filter { it.kind==StageKind.RECOVERY || it.kind==StageKind.COOLDOWN }.forEach { assertEquals(HintMode.OFF,it.frequency.mode); assertEquals(HintMode.OFF,it.heart.mode) }
        } }
    }
    @Test fun rowingAndRunningNeverReuseEllipticalResistanceOrTargets() {
        WorkoutPolicy.templates(Machine.ROWER).forEach { w ->
            assertTrue(w.steps.all { it.resistancePercent==null && it.speedTargetMps==null && it.inclineTargetPercent==null })
            w.steps.filter { it.kind==StageKind.TRAINING }.forEach { assertTrue(it.frequency.resolve(w.hints.frequency).enabled) }
        }
        WorkoutPolicy.templates(Machine.TREADMILL).forEach { w -> assertTrue(w.steps.all { it.resistancePercent==null && it.speedTargetMps!=null && it.inclineTargetPercent==0.0 }) }
        assertNotEquals(WorkoutPolicy.templates(Machine.BIKE).first { it.id.startsWith("steady20_") }.steps.map { it.resistancePercent },WorkoutPolicy.templates(Machine.ELLIPTICAL).first { it.id.startsWith("steady20_") }.steps.map { it.resistancePercent })
    }
}
