package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

internal const val VOIDED_ROW_ALPHA = 0.22f

private const val DETAIL_LABEL_WEIGHT = 0.35f
private const val DETAIL_VALUE_WEIGHT = 0.65f

/**
 * #382 — the editable-session affordance set: the strict gate ([SessionEditGate]) plus the
 * optional mutation callbacks. The read-only path is the all-default pair — every callback
 * null — which renders exactly as before #382.
 */
data class SessionEditGate(
    val canEdit: Boolean = false,
    val mutating: Boolean = false,
)

/**
 * #382 — post-create affordance callbacks for [SessionDetailContent]; destructive actions
 * confirm at the pane layer before invoking.
 */
data class SessionDetailActions(
    val onRemoveConcern: ((ConcernResponse) -> Unit)? = null,
    val onPromoteOtherConcern: (() -> Unit)? = null,
    val onAddPractitioner: (() -> Unit)? = null,
    val onUpdatePractitionerRemarks: ((DashboardPractitionerResponse) -> Unit)? = null,
    val onRemovePractitioner: ((DashboardPractitionerResponse) -> Unit)? = null,
)

/**
 * Shared detail pane — desktop master-detail inline pane AND the mobile pushed
 * SessionDetail route render the same content (Q1 secondary fields: base price,
 * practitioners, next appointment, remarks, concerns).
 *
 * #382 — when [gate].canEdit is true (strict BRANCH-context `EDIT_BRANCH_DATA` or an active
 * day grant — the backend's branch-or-day gate), the practitioners section grows
 * add/remarks/remove actions and the concerns section grows remove/promote, wired through
 * [actions]' optional callbacks; destructive actions confirm at the pane layer before
 * invoking. The read-only path renders exactly as before this ticket. [gate].mutating
 * disables every action while a mutation round-trip is in flight (ADR-0022 pessimism: no
 * local commit, the authoritative reload repaints).
 */
@Composable
fun SessionDetailContent(
    session: DashboardSessionResponse?,
    modifier: Modifier = Modifier,
    gate: SessionEditGate = SessionEditGate(),
    actions: SessionDetailActions = SessionDetailActions(),
) {
    if (session == null) {
        EmptySessionPlaceholder(modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SessionDetailHeader(session)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        DetailRow("Base price", "₱${session.basePrice}")
        DetailRow("Final price", "₱${session.finalPrice}")
        if (session.bookedAt != null) {
            DetailRow("Booked time", bookedTimeLabel(session.bookedAt))
        }
        session.nextAppointmentDate?.let { nextAppointment ->
            DetailRow("Next appointment", nextAppointment)
        }
        PractitionersSection(session, gate, actions)
        // #366 — who the client asked for, when one was recorded; nothing when unset.
        session.requestedPractitionerName?.takeIf { it.isNotBlank() }?.let { requested ->
            DetailRow("Requested", requested)
        }
        session.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            DetailRow("Remarks", remarks)
        }
        ConcernsSection(session, gate, actions)
    }
}

/** Client heading row (name + walk-in dot) and the type/status/voided badge strip. */
@Composable
private fun SessionDetailHeader(session: DashboardSessionResponse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = session.clientName ?: "Unknown client",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (session.isWalkIn) {
            WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SessionTypeBadge(session)
        SessionStatusBadge(session)
        if (session.isVoided) {
            VoidedPill()
        }
    }
}

@Composable
private fun PractitionersSection(
    session: DashboardSessionResponse,
    gate: SessionEditGate,
    actions: SessionDetailActions,
) {
    if (session.practitioners.isEmpty() && !gate.canEdit) return
    SectionHeader(
        label = "Practitioners",
        actionLabel = if (gate.canEdit) "Add" else null,
        enabled = !gate.mutating,
        onAction = actions.onAddPractitioner,
    )
    session.practitioners.forEach { practitioner ->
        if (gate.canEdit) {
            EditablePractitionerRow(
                practitioner = practitioner,
                mutating = gate.mutating,
                onUpdateRemarks = { actions.onUpdatePractitionerRemarks?.invoke(practitioner) },
                onRemove = { actions.onRemovePractitioner?.invoke(practitioner) },
            )
        } else {
            PractitionerRow(practitioner)
        }
    }
    if (session.practitioners.isEmpty()) {
        EmptySectionHint("No practitioners yet")
    }
}

@Composable
private fun ConcernsSection(
    session: DashboardSessionResponse,
    gate: SessionEditGate,
    actions: SessionDetailActions,
) {
    val otherConcerns = session.otherConcerns?.takeIf { it.isNotBlank() }
    if (!gate.canEdit) {
        // Legacy read-only rendering — one joined row, unchanged from #152.
        if (session.concerns.isNotEmpty()) {
            DetailRow("Concerns", session.concerns.joinToString { it.label })
        }
    } else if (session.concerns.isNotEmpty() || otherConcerns != null) {
        DetailRow("Concerns", "")
        session.concerns.forEach { concern ->
            EditableConcernRow(
                label = concern.label,
                mutating = gate.mutating,
                actionLabel = "Remove",
                onAction = { actions.onRemoveConcern?.invoke(concern) },
            )
        }
        otherConcerns?.let { other ->
            EditableConcernRow(
                label = "Other: $other",
                mutating = gate.mutating,
                actionLabel = "Promote",
                onAction = actions.onPromoteOtherConcern,
            )
        }
    } else {
        DetailRow("Concerns", "")
        EmptySectionHint("No concerns yet")
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.weight(DETAIL_LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(DETAIL_VALUE_WEIGHT),
        )
    }
}

@Composable
private fun PractitionerRow(practitioner: DashboardPractitionerResponse) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg)) {
        Text(
            text = practitioner.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        practitioner.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            Text(
                text = remarks,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}

/** #382 — section label row carrying the section's Add action when editable. */
@Composable
private fun SectionHeader(
    label: String,
    actionLabel: String?,
    enabled: Boolean,
    onAction: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, enabled = enabled) {
                Text(actionLabel)
            }
        }
    }
}

/** #382 — one practitioner with Remarks/Remove affordances while the surface is editable. */
@Composable
private fun EditablePractitionerRow(
    practitioner: DashboardPractitionerResponse,
    mutating: Boolean,
    onUpdateRemarks: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = practitioner.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            practitioner.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
                Text(
                    text = remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        }
        TextButton(onClick = onUpdateRemarks, enabled = !mutating) {
            Text("Remarks")
        }
        TextButton(onClick = onRemove, enabled = !mutating) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** #382 — one concern/free-text line with a Remove/Promote affordance. */
@Composable
private fun EditableConcernRow(
    label: String,
    mutating: Boolean,
    actionLabel: String,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onAction != null) {
            TextButton(onClick = onAction, enabled = !mutating) {
                val destructive = actionLabel == "Remove"
                Text(
                    actionLabel,
                    color =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

/** #382 — muted empty-state hint shown in editable sections that have no rows. */
@Composable
private fun EmptySectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = InkSubtle,
        modifier = Modifier.padding(start = Spacing.lg),
    )
}
