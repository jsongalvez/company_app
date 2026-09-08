package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.ui.screen.ClientNameText
import com.companyb.companyapp.ui.screen.SessionStatusBadge
import com.companyb.companyapp.ui.screen.SessionTypeBadge
import com.companyb.companyapp.ui.screen.VOIDED_ROW_ALPHA
import com.companyb.companyapp.ui.screen.VoidedPill
import com.companyb.companyapp.ui.screen.WalkInDot
import com.companyb.companyapp.ui.screen.bookedTimeLabel
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover

// #97 spec line 111 (strict) — mobile card: client name + walk-in dot, type badge, status
// badge, final price; VOIDED pill inline with the price (right-aligned, same row). Q3
// voided card: Danger 22% tint + strikethrough client name. Pull-to-refresh (Q5c manual
// refresh on mobile) wraps the list.
// #672 — stacked identity rows: client first, status/type + booked time secondary,
// price end-aligned in a consistent right column (the compact-rows contract).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileSessionList(
    args: SessionListArgs,
    modifier: Modifier,
) {
    PullToRefreshBox(
        isRefreshing = args.isRefreshing,
        onRefresh = args.onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            // #672 — the workspace-owned anchor when provided (Back/section-return
            // restores the row); otherwise the list keeps its own remembered state.
            state = args.lazyListState ?: rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(Spacing.md),
        ) {
            items(args.sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    // #672 — the retained selection highlights on return (Back restores
                    // the row); the wide desktop table renders the same slot.
                    isSelected = session.id == args.selectedSessionId,
                    onClick = { args.onSessionClick(session) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: DashboardSessionResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // Q3 voided priority + selection slot (the desktop table's dashboardRowBackground
    // order): Danger 22% alpha over Surface1 when voided, surface-3 when selected.
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color =
            when {
                session.isVoided -> {
                    MaterialTheme.colorScheme.error.copy(alpha = VOIDED_ROW_ALPHA)
                }

                isSelected -> {
                    MaterialTheme.colorScheme.secondary
                }

                else -> {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .rowHover(shape = RoundedCornerShape(CornerRadius.md)),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClientNameText(session)
                        if (session.isWalkIn) {
                            WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
                        }
                    }
                }
                Text(
                    text = "₱${session.finalPrice}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SessionTypeBadge(session)
                SessionStatusBadge(session)
                Text(
                    text = bookedTimeLabel(session.bookedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (session.isVoided) {
                    VoidedPill()
                }
            }
        }
    }
}
