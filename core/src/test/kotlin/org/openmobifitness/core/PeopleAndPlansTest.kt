package org.openmobifitness.core

import org.junit.Test
import org.junit.Assert.*

class PeopleAndPlansTest {
    @Test fun migratedFavoritesCanBeRemovedWithoutReturningOnReload() {
        val migrated=WorkoutPolicy.favorites(setOf("warmup","custom-plan"))
        assertEquals(setOf("warmup_elliptical","warmup_bike","custom-plan"),migrated)
        val saved=migrated-"warmup_elliptical"
        assertEquals(saved,WorkoutPolicy.favorites(saved))
    }
    @Test fun aNameIsNotAnIdentityAndUnknownWeightStaysUnknown() {
        val a=UserProfile(name="阿 林"); val b=UserProfile(name="阿 林")
        assertNotEquals(a.id,b.id); assertNull(a.weightKg)
        assertEquals("alice",UserProfile(name="ＡＬＩＣＥ").normalizedName)
        assertEquals(a,Exchange.parse(Exchange.users(listOf(a))).users.single())
    }
    @Test fun independentRangesAndZeroTargetsSurviveCsvRoundTrip() {
        val user=UserProfile(name="A")
        val w=Workout(title="Run",ownerUserId=user.id,machine=Machine.TREADMILL,hints=WorkoutHints(heart=PersonalRange(true,120.0,150.0)),steps=listOf(Step(target=60.0,id="warmup",kind=StageKind.WARMUP,frequency=StageRange(HintMode.OFF),heart=StageRange(HintMode.CUSTOM,110.0,null),speedTargetMps=0.0,inclineTargetPercent=0.0)))
        assertEquals(w,Exchange.parse(Exchange.workouts(listOf(w))).workouts.single())
        assertFalse(w.steps.single().frequency.resolve(w.hints.frequency).enabled)
        assertEquals(110.0,w.steps.single().heart.resolve(w.hints.heart).lower!!,0.0)
        assertFalse(WorkoutPolicy.compatible(w,Machine.ROWER,user.id))
        assertFalse(WorkoutPolicy.compatible(w,Machine.TREADMILL,UserProfile(name="A").id))
    }
    @Test fun templatesAreTypedAndRecoveryDoesNotImportOldHints() {
        assertEquals(21,WorkoutPolicy.templates(Machine.BIKE).size)
        assertEquals(21,WorkoutPolicy.templates(Machine.ELLIPTICAL).size)
        assertEquals(14,WorkoutPolicy.templates(Machine.ROWER).size)
        assertEquals(14,WorkoutPolicy.templates(Machine.TREADMILL).size)
        WorkoutPolicy.templates(Machine.BIKE).forEach { w ->
            WorkoutPolicy.validate(w); assertFalse(w.hints.frequency.enabled)
            assertTrue(w.steps.map { it.id }.distinct().size==w.steps.size)
            assertEquals(HintMode.OFF,w.steps.last().frequency.mode)
        }
    }
    @Test fun oneSessionThrottleSurvivesStageHintResets() {
        val throttle=SessionHintThrottle()
        assertTrue(throttle.accept(5000,true)); assertFalse(throttle.accept(10000,true))
        assertFalse(throttle.accept(34999,true)); assertTrue(throttle.accept(35000,true))
        throttle.reset(); assertTrue(throttle.accept(36000,true))
    }
    @Test fun identityAndPlanSnapshotRoundTripWithoutChangingTheOriginalName() {
        val u=UserProfile(name="A"); val p=Workout(title="Plan",machine=Machine.ELLIPTICAL,ownerUserId=u.id,steps=listOf(Step(target=60.0,id="one")))
        val s=Session(ownerUserId=u.id,startedUserId=u.id,startedUserName=u.name,identityVersion=1,workoutSnapshot=Exchange.workouts(listOf(p)),weightSource="default",metSource="default")
        assertEquals(s,Exchange.parse(Exchange.sessions(listOf(s))).sessions.single())
        val invalid=s.copy(workoutSnapshot=Exchange.sessions(listOf(s.copy(workoutSnapshot=""))))
        assertThrows(IllegalArgumentException::class.java) { Exchange.parse(Exchange.sessions(listOf(invalid))) }
    }
    @Test fun incompatibleConditionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { WorkoutPolicy.validate(Workout(title="Bad",machine=Machine.BIKE,steps=listOf(Step(Condition.STROKES,60.0)))) }
    }
}
