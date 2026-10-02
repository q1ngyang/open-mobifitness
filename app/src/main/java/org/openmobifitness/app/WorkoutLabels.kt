package org.openmobifitness.app

import android.content.Context
import org.openmobifitness.core.Workout

fun Context.workoutName(workout: Workout): String {
    val id=when(workout.id) { "warmup" -> R.string.warmup; "recovery" -> R.string.recovery; "steady20" -> R.string.steady20; "steady30" -> R.string.steady30; "endurance" -> R.string.endurance; "interval10" -> R.string.interval10; "interval20" -> R.string.interval20; "interval30" -> R.string.interval30; "pyramid20" -> R.string.pyramid20; "pyramid30" -> R.string.pyramid30; "progressive" -> R.string.progressive; "cooldown" -> R.string.cooldown; "light" -> R.string.plan_light; "moderate" -> R.string.plan_moderate; "vigorous" -> R.string.plan_vigorous; "strength" -> R.string.plan_strength; "weight" -> R.string.plan_weight; "hiit" -> R.string.plan_hiit; "cardio40" -> R.string.plan_cardio40; "hiit30" -> R.string.plan_hiit30; "hiit40" -> R.string.plan_hiit40; else -> null }
    return if(workout.builtin && id!=null) getString(id) else workout.title
}
