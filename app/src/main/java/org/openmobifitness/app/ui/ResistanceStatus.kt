package org.openmobifitness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.openmobifitness.app.R
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.ble.ResistancePhase
import org.openmobifitness.app.service.WorkoutService
import org.openmobifitness.core.Machine

internal fun resistanceStatusResource(phase: ResistancePhase) = when(phase) {
    ResistancePhase.SENDING -> R.string.control_sending
    ResistancePhase.AWAITING_FEEDBACK -> R.string.control_pending
    ResistancePhase.UNCONFIRMED -> R.string.control_unconfirmed
    ResistancePhase.MATCHED -> R.string.control_reading_matches
    ResistancePhase.SENT -> R.string.control_write_only
    ResistancePhase.FAILED -> R.string.control_send_failed
}

/** Shared wording and severity for the connection page and training controls. */
@Composable internal fun ResistanceStatus(link: LinkState, queuedTarget: Double? = null) {
    val phase = if(queuedTarget != null) ResistancePhase.SENDING else link.resistanceControl?.phase ?: return
    val target = queuedTarget ?: link.resistanceControl?.target
    Text(stringResource(resistanceStatusResource(phase), WorkoutService.number(target)),
        Modifier.testTag("resistance-status"), style=MaterialTheme.typography.labelMedium,
        color=if(phase==ResistancePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if(phase==ResistancePhase.UNCONFIRMED) Text(
        stringResource(if(link.machine in setOf(Machine.BIKE,Machine.ELLIPTICAL)) R.string.control_pedal_hint else R.string.control_no_feedback),
        style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
}
