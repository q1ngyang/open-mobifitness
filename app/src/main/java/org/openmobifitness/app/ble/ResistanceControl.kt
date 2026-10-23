package org.openmobifitness.app.ble

enum class ResistanceSend { SENDING, SENT, FAILED }
enum class ResistancePhase { SENDING, AWAITING_FEEDBACK, UNCONFIRMED, MATCHED, SENT, FAILED }

/** One local request, never a wire sequence number or proof of mechanical execution. */
data class ResistanceControl(
    val id: Long,
    val target: Double,
    val expectsFeedback: Boolean = true,
    val send: ResistanceSend = ResistanceSend.SENDING,
    val windowExpired: Boolean = false,
    // Only a new, validated resistance field received after this request may populate this.
    val reading: Double? = null
) {
    val phase: ResistancePhase get() = when {
        send == ResistanceSend.FAILED -> ResistancePhase.FAILED
        send == ResistanceSend.SENDING -> ResistancePhase.SENDING
        !expectsFeedback -> ResistancePhase.SENT
        reading?.let { kotlin.math.abs(it - target) < 0.05 } == true -> ResistancePhase.MATCHED
        windowExpired -> ResistancePhase.UNCONFIRMED
        else -> ResistancePhase.AWAITING_FEEDBACK
    }
    val pending: Double? get() = target.takeIf { phase in setOf(ResistancePhase.SENDING, ResistancePhase.AWAITING_FEEDBACK) }
    val unconfirmedTarget: Double? get() = target.takeIf { pending != null || phase == ResistancePhase.UNCONFIRMED }
}
