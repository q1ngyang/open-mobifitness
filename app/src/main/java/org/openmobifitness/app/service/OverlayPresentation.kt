package org.openmobifitness.app.service

import android.content.Context
import org.openmobifitness.app.*
import org.openmobifitness.core.MetricId

internal enum class OverlayStatus { ACTIVE, PAUSED, DEMO, DISCONNECTED }

/** Display-only snapshot. A requested resistance never replaces the equipment reading. */
internal data class OverlayPresentation(
    val readings: List<MetricReading>,
    val elapsed: String,
    val stage: String,
    val remaining: String,
    val stageProgress: Float?,
    val resistance: MetricReading,
    val controlMode: String,
    val statusText: String,
    val status: OverlayStatus,
    val paused: Boolean,
    val canAdjust: Boolean,
    val countdownValue: String?=null,
    val controlOnly: Boolean=false,
    val canResume: Boolean=true,
    val pending: String="",
    val userName: String="",
    val showResistance: Boolean=true
)

internal fun overlayClock(ms: Long): String {
    val value=WorkoutService.elapsed(ms.coerceAtLeast(0))
    return if(ms<3_600_000) value.removePrefix("00:") else value
}

internal fun Context.overlayPresentation(c: Controller,expanded: Boolean): OverlayPresentation {
    val state=c.state.value
    val link=c.ble.state.value
    val disconnected=!state.demo && link.phase!="ready"
    val status=when {
        disconnected -> OverlayStatus.DISCONNECTED
        state.demo -> OverlayStatus.DEMO
        state.paused -> OverlayStatus.PAUSED
        else -> OverlayStatus.ACTIVE
    }
    val connectionText=when {
        disconnected -> getString(R.string.disconnected)
        state.controlOnly -> "${getString(R.string.control_only)} · ${getString(R.string.not_recording)}"
        state.demo && state.paused -> "${getString(R.string.demo)} · ${getString(R.string.paused)}"
        state.demo -> getString(R.string.demo)
        state.paused -> getString(R.string.paused)
        else -> getString(R.string.connected)
    }
    val statusText=connectionText
    val userName=state.session?.startedUserName ?: c.currentUser.value?.name.orEmpty()
    val workout=state.selected.takeIf { state.session!=null }
    val stage=when {
        state.done -> getString(R.string.completed)
        workout!=null -> getString(R.string.stage_format,(state.stage+1).coerceAtMost(workout.steps.size),workout.steps.size)
        else -> getString(if(state.controlOnly) R.string.control_only else R.string.free_training)
    }
    val remaining=when {
        state.done -> getString(R.string.overlay_free_pace)
        workout!=null && state.remainingMs!=null -> getString(R.string.overlay_remaining,minutesSeconds(state.remainingMs))
        workout!=null -> "${(state.progress.coerceIn(0f,1f)*100).toInt()}%"
        else -> getString(if(state.controlOnly) R.string.not_recording else if(state.paused) R.string.paused else R.string.overlay_free_pace)
    }
    val elapsed=state.session?.let { overlayClock(it.elapsedMs) } ?: getString(R.string.not_recording)
    val readings=c.display.selected(if(expanded) DisplayScope.EXPANDED else DisplayScope.COMPACT,c.displayMachine(),state.controlOnly).map { id ->
        reading(id,c).let { if(id==MetricId.TIME && state.session!=null) it.copy(value=elapsed) else it }
    }
    val can=state.demo && c.range()!=null || (link.phase=="ready" && link.writable && link.range!=null && c.controlResistance()!=null && !link.busy)
    val controlMode=getString(when {
        state.pendingResistance!=null -> R.string.overlay_pending
        !state.demo && (!link.writable || link.range==null) -> R.string.overlay_monitor
        state.automatic && workout!=null && !state.done -> R.string.overlay_auto
        else -> R.string.overlay_manual
    })
    return OverlayPresentation(readings,elapsed,stage,remaining,
        if(workout!=null) (if(state.done) 1f else state.progress.coerceIn(0f,1f)) else null,
        reading(MetricId.RESISTANCE,c).let { if(link.resistanceFeedback || state.demo) it else it.copy(label=getString(R.string.resistance_commanded),value=WorkoutService.number(c.controlResistance()),unit="") },controlMode,statusText,status,state.paused,can,
        state.remainingMs?.takeIf { workout!=null && !state.done }?.let { minutesSeconds(it) },state.controlOnly,!disconnected,
        (state.pendingResistance ?: link.requested)?.let { "${getString(R.string.target)} ${WorkoutService.number(it)} · ${getString(R.string.overlay_pending)}" } ?: "",userName,c.range()!=null)
}

/** Content geometry only; the service caps it to the usable display and keeps scrolling. */
internal fun overlayContentHeight(expanded: Boolean,model: OverlayPresentation,fontScale: Float): Int {
    val scale=fontScale.coerceAtLeast(1f)
    val count=model.readings.size.coerceAtMost(if(expanded) 6 else 2)
    val rows=if(scale>=1.5f) count else (count+1)/2
    val metrics=if(scale>=1.5f) rows*64*scale else rows*56*scale
    val resistance=if(model.showResistance) 1+maxOf(52f,36*scale)+(if(model.pending.isEmpty()) 0f else 28*scale) else 0f
    val footer=(if(scale>=1.5f) 28 else 24)*scale
    val height=if(expanded) 24+7+48+metrics+rows+resistance+footer+4+48+6+
        (if(model.controlOnly) 0f else 60*scale)+(if(model.stageProgress!=null) 18 else 0)
        else 16+7+48+maxOf(56*scale,metrics)+footer+5+(if(model.stageProgress!=null) 8 else 0)
    return kotlin.math.ceil(height.toDouble()).toInt()
}
