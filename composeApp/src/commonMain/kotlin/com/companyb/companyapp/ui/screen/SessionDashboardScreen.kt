package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatTimeOfDay
import com.companyb.companyapp.viewmodel.DashboardPollStatus
import com.companyb.companyapp.viewmodel.SessionDashboardViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.time.Instant

data class SessionListArgs(
    val sessions: List<DashboardSessionResponse>,
    val selectedSessionId: String?,
    val onSessionClick: (DashboardSessionResponse) -> Unit,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean,
    // #149 — desktop inline editing (#97 Q4, ADR-0022). Mobile stays read-only; the
    // defaults keep the androidMain actual's call site untouched.
    val canEdit: Boolean = false,
    val edit: DashboardEditState? = null,
    val onEditStart: (sessionId: String, field: DashboardEditField) -> Unit = { _, _ -> },
    val onEditDraftChange: (String) -> Unit = {},
    val onEditCommit: () -> Unit = {},
    val onEditDiscard: () -> Unit = {},
    val onEditReload: () -> Unit = {},
)

// #95 — platform-split list actual: desktopMain = table with header + selectable rows
// (RemittanceScreenParts precedent); androidMain = LazyColumn cards (spec line 111).
@Composable
internal expect fun SessionList(
    args: SessionListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
)

/**
 * #150 — platform-split empty state (the #147 accepted-SOFT gap: "desktop empty/ERRORED
 * lacks a refresh button" — the ERRORED half was already covered by its Retry card, this
 * lands the EMPTY half). Desktop renders a Refresh button in the same top-right position
 * as the table's Refresh row (affordance-position stability between the empty and list
 * states); mobile keeps its shipped #147 text-only state (list pull-to-refresh + auto-poll
 * cover). The #95 platform-split precedent.
 */
@Composable
internal fun MobileDashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    EmptyStateContent(selectedBranchName, modifier)
}

@Composable
internal expect fun DashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
)

/**
 * #150 — the centered empty-state text shared by both [DashboardEmptyState] actuals (the
 * [ClientNameText] precedent — one copy, no copy-paste drift when the copy changes).
 */
@Composable
internal fun EmptyStateContent(
    selectedBranchName: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No sessions yet today",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                "Sessions will appear here as practitioners log them." +
                    (selectedBranchName?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
    }
}

/**
 * #97 session dashboard — summary cards + session list, both backed by the single dashboard
 * fetch (Q6c's "no impossible states if they share an endpoint"). Poll lifecycle: resumed
 * while this screen is composed, paused on leave (mobile detail push; desktop inline pane
 * never leaves). States per Q6: cold spinner / error card with Retry / empty (₱0 cards) /
 * stale banner (Q5b) / ERRORED escalation / forbidden card (attendance gate 403).
 */
@Composable
fun SessionDashboardScreen(
    viewModel: SessionDashboardViewModel,
    selectedBranchName: String?,
    selectedSessionId: String?,
    onSessionClick: (DashboardSessionResponse) -> Unit,
    // #348 — the dashboard's entry into the start-a-session flow (both platforms).
    onSessionCreateClick: () -> Unit,
    // #351 — the relief-access surface (requester entry + incoming Grant/Deny), slotted so
    // this screen stays agnostic of the feature's VM/state sources.
    reliefAccessContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.dashboardState.collectAsState()
    val lastData by viewModel.lastData.collectAsState()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsState()
    val pollStatus by viewModel.pollStatus.collectAsState()
    val isForbidden by viewModel.isForbidden.collectAsState()
    val canEdit by viewModel.canEdit.collectAsState()
    val edit by viewModel.editState.collectAsState()
    // Q5 "silent polling": the pull-to-refresh indicator must show ONLY for a user-initiated
    // refresh, never for the 30s poll cycle's Loading frame (pass-1 HARD).
    var isManualRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is UiState.Loading) {
            isManualRefreshing = false
        }
    }
    // #150 — the manual-refresh closure shared by the desktop table's Refresh row and the
    // desktop empty state's button. The explicit `() -> Unit` coerces refresh()'s Job away.
    val onManualRefresh: () -> Unit = {
        isManualRefreshing = true
        viewModel.refresh()
    }

    DisposableEffect(Unit) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }

    Box(modifier = modifier) {
        when {
            isForbidden -> {
                InPlaceCard(
                    title = "You are no longer clocked in at this branch",
                    body =
                        "Your clock-in ended on the server (possibly clocked out elsewhere). " +
                            "Use Clock out in the drawer to return to branches.",
                    actionLabel = "Retry",
                    onAction = viewModel::retryAfterForbidden,
                )
            }

            lastData == null && (state is UiState.Idle || state is UiState.Loading) -> {
                // Q6a — cold start: full-screen spinner, no skeletons.
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            lastData == null && state is UiState.Error -> {
                val error = state as UiState.Error
                InPlaceCard(
                    title = "Couldn't load the dashboard",
                    body = error.message,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }

            else -> {
                val data = lastData
                if (data != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SummaryCardsRow(
                            grossCents = grossIncomeCents(data.sessions),
                            commissionCents = moneyToCents(data.commission.amount),
                            productSalesCount = data.commission.productSalesCount,
                        )
                        reliefAccessContent()
                        LastUpdatedRow(
                            lastUpdatedAt,
                            // #348 — "New session" lives beside the timestamp row (trailing).
                            onSessionCreateClick = onSessionCreateClick,
                        )
                        if (pollStatus == DashboardPollStatus.STALE) {
                            StaleBanner()
                        }
                        if (pollStatus == DashboardPollStatus.ERRORED) {
                            // Q5b escalation — error card with Retry (last data preserved
                            // in the VM; the card replaces content until a success resets).
                            InPlaceCard(
                                title = "Dashboard updates stopped",
                                body = "Repeated refresh failures — showing the last loaded data.",
                                actionLabel = "Retry",
                                onAction = viewModel::refresh,
                            )
                        } else if (data.sessions.isEmpty()) {
                            // Q6b — empty state participates in polling (auto-transitions
                            // when sessions appear); ₱0 cards stay visible above. The
                            // desktop actual adds the manual Refresh (#150).
                            DashboardEmptyState(
                                selectedBranchName = selectedBranchName,
                                onRefresh = onManualRefresh,
                            )
                        } else {
                            SessionList(
                                args =
                                    SessionListArgs(
                                        sessions = data.sessions,
                                        selectedSessionId = selectedSessionId,
                                        onSessionClick = onSessionClick,
                                        onRefresh = onManualRefresh,
                                        isRefreshing = isManualRefreshing && state is UiState.Loading,
                                        canEdit = canEdit,
                                        edit = edit,
                                        onEditStart = viewModel::startEdit,
                                        onEditDraftChange = viewModel::updateDraft,
                                        onEditCommit = viewModel::commitEdit,
                                        onEditDiscard = viewModel::discardEdit,
                                        onEditReload = viewModel::reloadAfterConflict,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCardsRow(
    grossCents: Long,
    commissionCents: Long,
    productSalesCount: Int,
) {
    Row(
        // IntrinsicSize.Min: the VerticalDivider's fillMaxHeight must size the Row to the
        // cards' intrinsic height — otherwise the divider forces the Row to the full pane
        // and the list below starves to zero height (the #144 layout class; pass-2 HARD).
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // #97 Q2 Variant A — equal peers: two equal-weight cards + thin vertical hairline
        // between them, hairline border each (pass-2 restored the locked treatment).
        SummaryCard(
            label = "Gross income",
            value = "₱${centsToMoney(grossCents)}",
            sublabel = "Today · completed, non-voided",
            // fillMaxHeight (within the IntrinsicSize.Min row): equal card heights so the
            // divider spans flush even when sublabels wrap to different line counts.
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxHeight(),
        )
        SummaryCard(
            label = "Your commission",
            value = "₱${centsToMoney(commissionCents)}",
            sublabel = commissionLabel(productSalesCount),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.lg),
        colors =
            CardDefaults.cardColors(
                // surfaceVariant = Surface2 (#141516) — the Q2 card surface.
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        // Q2 — hairline border on the card surface.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            // eyebrow — ink-subtle labelSmall above the value (Q2 typography ladder).
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = InkSubtle,
            )
            // no lavender on values — ink only (data, not actions).
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}

@Composable
private fun LastUpdatedRow(
    lastUpdatedAt: Instant?,
    onSessionCreateClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Q5a — timestamp of the LAST SUCCESSFUL refresh, never the last attempt.
            text = if (lastUpdatedAt == null) "" else "Updated ${formatTimeOfDay(lastUpdatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.weight(1f),
        )
        // #348 — the flow's entry point sits in the dashboard chrome row.
        TextButton(onClick = onSessionCreateClick) {
            Text("New session")
        }
    }
}

@Composable
private fun StaleBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.error.copy(alpha = STALE_BANNER_ALPHA),
    ) {
        Text(
            text = "Updates paused — showing the last loaded data",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}

@Composable
private fun InPlaceCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            Text(actionLabel)
        }
    }
}

private const val STALE_BANNER_ALPHA = 0.22f
