package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatTimeOfDay
import kotlin.time.Instant

private const val WALK_IN_DOT_SIZE = 8
private const val WALK_IN_DOT_ALPHA = 0.7f
private const val MUTED_BADGE_BG_ALPHA = 0.4f
private const val MUTED_BADGE_FG_ALPHA = 0.5f
private const val VOIDED_PILL_BG_ALPHA = 0.35f

/**
 * #557 — shared voided-row dimming: dashboard lists and the detail content render the same
 * voided affordance, so the constant stays in shared `ui/screen` as a deliberate edge
 * (detail does not own dashboard rendering).
 */
internal const val VOIDED_ROW_ALPHA = 0.22f

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
