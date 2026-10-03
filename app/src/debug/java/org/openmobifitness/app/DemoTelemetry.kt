package org.openmobifitness.app
import org.openmobifitness.core.*
import kotlin.math.sin
internal object DemoTelemetry {
    fun sample(old: ExerciseState,delta: Long,resistance: Double,strokes: Double): Pair<Metrics,Double> {
        val current=old.session; val moving=old.inUse
        val cadence=if(moving) (if(old.demoMachine==Machine.ROWER) 24 else 62)+sin((current?.elapsedMs ?: android.os.SystemClock.elapsedRealtime())/7000.0)*6 else 0.0
        val count=strokes+if(moving) cadence*delta/60000 else 0.0
        return Metrics(cadence,resistance,if(moving) 2.0 else 0.0,
            (old.metrics.distanceM ?: 0.0)+if(moving) delta/500.0 else 0.0,
            if(moving) 115+(cadence/10).toInt() else null,if(moving) 60+resistance*3 else 0.0,
            strokes=if(old.demoMachine==Machine.ROWER) count.toInt() else null,
            inclinePercent=if(old.demoMachine==Machine.TREADMILL) 2.0 else null,
            forceN=if(old.demoMachine==Machine.ROWER) 180.0 else null,
            stepRate=if(old.demoMachine==Machine.TREADMILL) cadence*2 else null,
            strideM=if(old.demoMachine==Machine.TREADMILL) 0.72 else null) to count
    }
}
