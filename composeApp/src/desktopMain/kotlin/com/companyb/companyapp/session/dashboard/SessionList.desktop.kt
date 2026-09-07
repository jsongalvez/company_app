package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.DayStatusWarning
import com.companyb.companyapp.ui.screen.ClientNameText
import com.companyb.companyapp.ui.screen.SessionTypeBadge
import com.companyb.companyapp.ui.screen.VOIDED_ROW_ALPHA
import com.companyb.companyapp.ui.screen.VoidedPill
import com.companyb.companyapp.ui.screen.WalkInDot
import com.companyb.companyapp.ui.screen.bookedTimeLabel
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
    // #477 — the row's display flags + edit callbacks ride two LPL-free carriers
    // (the UMParts SlotOrderCallbacks precedent); built once, shared per row.
    val editActions =
        DashboardEditActions(
            onSessionSelect = args.onSessionClick,
            onEditStart = args.onEditStart,
            onEditDraftChange = args.onEditDraftChange,
            onEditCommit = args.onEditCommit,
            onEditDiscard = args.onEditDiscard,
            onEditReload = args.onEditReload,
            onEditReasonChange = args.onEditReasonChange,
        )
    val rowConfig =
        DashboardRowConfig(
            canEdit = args.canEdit,
            canCorrectStatus = args.canCorrectStatus,
            dayStatus = args.dayStatus,
            edit = args.edit,
            requiresReason = args.requiresReason,
        )
    Column(modifier = modifier) {
        if (args.dayStatus != null && args.dayStatus != DayStatus.OPEN) {
            DayStatusWarning(args.dayStatus)
        }
        TableHeaderRow(onRefresh = args.onRefresh)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            args.sessions.forEach { session ->
                DashboardTableRow(session, session.id == args.selectedSessionId, rowConfig, editActions)
            }
        }
    }
}

@Composable
private fun TableHeaderRow(onRefresh: () -> Unit) {
    // Right-aligned action row (pass-2: the Refresh button must NOT occupy the VOIDED
    // column — the spec's 22% slot is data, not chrome). #150 — shared with the desktop
    // empty state (DashboardEmptyState.desktop.kt): the affordance-position stability
    // between the empty and list states rests on this single copy.
    DashboardRefreshRow(onRefresh)
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

/** #150 — the right-aligned Refresh row shared by the table header and the desktop empty state. */
@Composable
internal fun DashboardRefreshRow(onRefresh: () -> Unit) {
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
}

@Composable
private fun DashboardTableRow(
    session: DashboardSessionResponse,
    isSelected: Boolean,
    config: DashboardRowConfig,
    actions: DashboardEditActions,
) {
    // Q3 — voided row: Danger 22% alpha over Surface1; selection uses the surface-3 slot.
    val rowBackground = dashboardRowBackground(session, isSelected)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .clickable(onClick = { actions.onSessionSelect(session) })
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        // Q3 — time + type fonts: InkSubtle when voided, InkMuted (onSurfaceVariant) when active.
        Text(
            text = bookedTimeLabel(session.bookedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        DashboardClientCell(session)
        // Session type is a creation-time snapshot; status and final price remain day-gated.
        Box(modifier = Modifier.weight(1f)) {
            SessionTypeBadge(session)
        }
        DashboardRowEditCells(session, config, actions)
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

// Q3 — voided row: Danger 22% alpha over Surface1; selection uses the surface-3 slot.
@Composable
private fun dashboardRowBackground(
    session: DashboardSessionResponse,
    isSelected: Boolean,
): Color =
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

@Composable
private fun RowScope.DashboardClientCell(session: DashboardSessionResponse) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(2f),
    ) {
        ClientNameText(session)
        if (session.isWalkIn) {
            WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
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

/**
 * #403 — the edit-machine hooks the REMITTED-day dialog forwards (the *CreateParams
 * parameter-object shape; keeps the composable under the LongParameterList budget).
 */
internal data class RemittedEditActions(
    val onDraftChange: (String) -> Unit,
    val onReasonChange: (String) -> Unit,
    val onCommit: () -> Unit,
    val onDiscard: () -> Unit,
    val onReload: () -> Unit,
)

/**
 * #477 — the desktop row's display inputs as one object (the UMParts SlotOrderCallbacks
 * precedent): [DashboardTableRow] and its edit cells stay at ≤5 direct params.
 */
internal data class DashboardRowConfig(
    val canEdit: Boolean,
    val canCorrectStatus: Boolean,
    val dayStatus: DayStatus?,
    val edit: DashboardEditState?,
    val requiresReason: Boolean,
)

/**
 * #477 — the seven row/edit callbacks as one object (data classes are LPL-free):
 * the row click plus the six #149/#403/#425 edit-machine hooks.
 */
internal data class DashboardEditActions(
    val onSessionSelect: (DashboardSessionResponse) -> Unit,
    val onEditStart: (String, DashboardEditField) -> Unit,
    val onEditDraftChange: (String) -> Unit,
    val onEditCommit: () -> Unit,
    val onEditDiscard: () -> Unit,
    val onEditReload: () -> Unit,
    val onEditReasonChange: (String) -> Unit,
)

/**
 * #477 — the three inline-editor hooks shared by [SelectEditor]/[PriceEditor]:
 * the dialog (RemittedEditActions) and the row chain (DashboardEditActions) each
 * wrap their own commit ownership into this shape.
 */
internal data class InlineEditActions(
    val onDraftChange: (String) -> Unit,
    val onCommit: () -> Unit,
    val onDiscard: () -> Unit,
)
