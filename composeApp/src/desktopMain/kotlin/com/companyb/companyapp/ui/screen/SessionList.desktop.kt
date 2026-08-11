package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

// #97 Q1 — desktop table: 6 primary columns (booked time, client + walk-in dot, type, status,
// final price, VOIDED slot right-aligned 22%); secondary fields live in the detail pane
// (master-detail Row, #91). Practitioners demoted to the pane (Q1 pressure-test lock).
@Composable
internal actual fun SessionList(
    args: SessionListArgs,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        TableHeaderRow(onRefresh = args.onRefresh)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            args.sessions.forEach { session ->
                DashboardTableRow(
                    session = session,
                    isSelected = session.id == args.selectedSessionId,
                    onClick = { args.onSessionClick(session) },
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow(onRefresh: () -> Unit) {
    // Right-aligned action row (pass-2: the Refresh button must NOT occupy the VOIDED
    // column — the spec's 22% slot is data, not chrome).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        TableCell("Booked", modifier = Modifier.weight(1f), header = true)
        TableCell("Client", modifier = Modifier.weight(2f), header = true)
        TableCell("Type", modifier = Modifier.weight(1f), header = true)
        TableCell("Status", modifier = Modifier.weight(1f), header = true)
        TableCell("Final", modifier = Modifier.weight(1f), header = true)
        // Q3 — VOIDED slot: 22% of the row width, right-aligned separation from the price.
        Box(
            modifier =
                Modifier
                    .weight(DESKTOP_VOIDED_SLOT_WEIGHT)
                    .padding(end = Spacing.xs),
            contentAlignment = Alignment.CenterEnd,
        ) {}
    }
}

@Composable
private fun DashboardTableRow(
    session: DashboardSessionResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // Q3 — voided row: Danger 22% alpha over Surface1; selection uses the surface-3 slot.
    val rowBackground =
        when {
            session.isVoided -> {
                MaterialTheme.colorScheme.error.copy(alpha = VOIDED_ROW_ALPHA)
            }

            isSelected -> {
                MaterialTheme.colorScheme.secondary
            }

            else -> {
                MaterialTheme.colorScheme.surface
            }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        // Q3 — time + type fonts: InkSubtle when voided, InkMuted (onSurfaceVariant) when active.
        Text(
            text = bookedTimeLabel(session.bookedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(2f),
        ) {
            ClientNameText(session)
            if (session.isWalkIn) {
                WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            SessionTypeBadge(session)
        }
        Box(modifier = Modifier.weight(1f)) {
            SessionStatusBadge(session)
        }
        Text(
            text = "₱${session.finalPrice}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Q3 — VOIDED pill right-aligned in the dedicated 22% slot.
        Box(
            modifier = Modifier.weight(DESKTOP_VOIDED_SLOT_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (session.isVoided) {
                VoidedPill()
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    header: Boolean,
) {
    Text(
        text = text,
        style =
            if (header) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier,
    )
}

private const val DESKTOP_VOIDED_SLOT_WEIGHT = 0.22f
