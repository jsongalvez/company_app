package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatTimeOfDay
import kotlin.time.Instant

internal const val VOIDED_ROW_ALPHA = 0.22f

private const val WALK_IN_DOT_SIZE = 8
private const val WALK_IN_DOT_ALPHA = 0.7f
private const val MUTED_BADGE_BG_ALPHA = 0.4f
private const val MUTED_BADGE_FG_ALPHA = 0.5f
private const val VOIDED_PILL_BG_ALPHA = 0.35f

/**
 * Shared detail pane — desktop master-detail inline pane AND the mobile pushed
 * SessionDetail route render the same content (Q1 secondary fields: base price,
 * practitioners, next appointment, remarks, concerns).
 *
 * #382 — post-create affordances: when [canEdit] is true (strict BRANCH-context
 * `EDIT_BRANCH_DATA` or an active day grant — the backend's branch-or-day gate), the
 * practitioners section grows add/remarks/remove actions and the concerns section grows
 * remove/promote, wired through the optional callbacks; destructive actions confirm at the
 * pane layer before invoking. The read-only path (all callbacks null) renders exactly as
 * before this ticket. [mutating] disables every action while a mutation round-trip is in
 * flight (ADR-0022 pessimism: no local commit, the authoritative reload repaints).
 */
@Composable
fun SessionDetailContent(
    session: DashboardSessionResponse?,
    modifier: Modifier = Modifier,
    canEdit: Boolean = false,
    mutating: Boolean = false,
    onRemoveConcern: ((ConcernResponse) -> Unit)? = null,
    onPromoteOtherConcern: (() -> Unit)? = null,
    onAddPractitioner: (() -> Unit)? = null,
    onUpdatePractitionerRemarks: ((DashboardPractitionerResponse) -> Unit)? = null,
    onRemovePractitioner: ((DashboardPractitionerResponse) -> Unit)? = null,
) {
    if (session == null) {
        EmptySessionPlaceholder(modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        DetailRow("Base price", "₱${session.basePrice}")
        DetailRow("Final price", "₱${session.finalPrice}")
        if (session.bookedAt != null) {
            DetailRow("Booked time", bookedTimeLabel(session.bookedAt))
        }
        session.nextAppointmentDate?.let { nextAppointment ->
            DetailRow("Next appointment", nextAppointment)
        }
        if (session.practitioners.isNotEmpty() || canEdit) {
            SectionHeader(
                label = "Practitioners",
                actionLabel = if (canEdit) "Add" else null,
                enabled = !mutating,
                onAction = onAddPractitioner,
            )
            session.practitioners.forEach { practitioner ->
                if (canEdit) {
                    EditablePractitionerRow(
                        practitioner = practitioner,
                        mutating = mutating,
                        onUpdateRemarks = { onUpdatePractitionerRemarks?.invoke(practitioner) },
                        onRemove = { onRemovePractitioner?.invoke(practitioner) },
                    )
                } else {
                    PractitionerRow(practitioner)
                }
            }
            if (session.practitioners.isEmpty()) {
                EmptySectionHint("No practitioners yet")
            }
        }
        // #366 — who the client asked for, when one was recorded; nothing when unset.
        session.requestedPractitionerName?.takeIf { it.isNotBlank() }?.let { requested ->
            DetailRow("Requested", requested)
        }
        session.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            DetailRow("Remarks", remarks)
        }
        val otherConcerns = session.otherConcerns?.takeIf { it.isNotBlank() }
        if (!canEdit) {
            // Legacy read-only rendering — one joined row, unchanged from #152.
            if (session.concerns.isNotEmpty()) {
                DetailRow("Concerns", session.concerns.joinToString { it.label })
            }
        } else if (session.concerns.isNotEmpty() || otherConcerns != null) {
            DetailRow("Concerns", "")
            session.concerns.forEach { concern ->
                EditableConcernRow(
                    label = concern.label,
                    mutating = mutating,
                    actionLabel = "Remove",
                    onAction = { onRemoveConcern?.invoke(concern) },
                )
            }
            otherConcerns?.let { other ->
                EditableConcernRow(
                    label = "Other: $other",
                    mutating = mutating,
                    actionLabel = "Promote",
                    onAction = onPromoteOtherConcern,
                )
            }
        } else {
            DetailRow("Concerns", "")
            EmptySectionHint("No concerns yet")
        }
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
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
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

@Composable
internal fun WalkInDot(
    voided: Boolean,
    modifier: Modifier = Modifier,
) {
    // Q3 — walk-in dot: lavender 70% when active, grey (InkSubtle) when voided.
    Surface(
        shape = CircleShape,
        color =
            if (voided) {
                InkSubtle
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = WALK_IN_DOT_ALPHA)
            },
        modifier = modifier.size(WALK_IN_DOT_SIZE.dp),
    ) {}
}

@Composable
internal fun SessionTypeBadge(session: DashboardSessionResponse) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (session.isVoided) {
                MaterialTheme.colorScheme.secondary.copy(alpha = MUTED_BADGE_BG_ALPHA)
            } else {
                MaterialTheme.colorScheme.secondary
            },
    ) {
        Text(
            text = session.sessionType.name,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (session.isVoided) {
                    InkSubtle.copy(alpha = MUTED_BADGE_FG_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun SessionStatusBadge(session: DashboardSessionResponse) {
    // Q3 — status pill stays when voided (status is real — COMPLETED is still COMPLETED),
    // just visually deferred to the VOIDED pill; the two axes are not collapsed.
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (session.isVoided) {
                MaterialTheme.colorScheme.secondary.copy(alpha = MUTED_BADGE_BG_ALPHA)
            } else {
                MaterialTheme.colorScheme.secondary
            },
    ) {
        Text(
            text = session.sessionStatus.name,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (session.isVoided) {
                    InkSubtle.copy(alpha = MUTED_BADGE_FG_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun VoidedPill() {
    // Q3 — VOIDED pill: Danger 35% bg + bright ink text (light rose, high contrast);
    // an annotation, not a colored status chip (chip vocabulary stays owned by status).
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.error.copy(alpha = VOIDED_PILL_BG_ALPHA),
    ) {
        Text(
            text = "VOIDED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/**
 * #97 Q3 — client name treatment: strikethrough + dimmest ink when voided; active rows use
 * full ink. Shared by the desktop table row and the mobile card.
 */
@Composable
internal fun ClientNameText(session: DashboardSessionResponse) {
    Text(
        text = session.clientName ?: "Unknown client",
        style = MaterialTheme.typography.bodyMedium,
        color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (session.isVoided) TextDecoration.LineThrough else TextDecoration.None,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun EmptySessionPlaceholder(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Select a session",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
    }
}

internal fun bookedTimeLabel(bookedAt: String?): String =
    if (bookedAt == null) {
        "—"
    } else {
        runCatching { formatTimeOfDay(Instant.parse(bookedAt)) }.getOrDefault("—")
    }
