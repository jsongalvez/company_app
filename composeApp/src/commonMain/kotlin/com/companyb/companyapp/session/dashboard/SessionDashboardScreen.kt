package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.app.navigation.NavigationContextStore
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.contract.operationalTouchTarget
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatTimeOfDay
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
    // #425 — Coordinator-only status corrections; false is the fail-closed default.
    val canCorrectStatus: Boolean = false,
    // #425 — unknown day state is fail-closed for desktop mutation affordances.
    val dayStatus: DayStatus? = null,
    val edit: DashboardEditState? = null,
    val onEditStart: (sessionId: String, field: DashboardEditField) -> Unit = { _, _ -> },
    val onEditDraftChange: (String) -> Unit = {},
    // #403 — REMITTED-day reason plumbing (desktop only; defaults keep androidMain untouched).
    val requiresReason: Boolean = false,
    val onEditReasonChange: (String) -> Unit = {},
    val onEditCommit: () -> Unit = {},
    val onEditDiscard: () -> Unit = {},
    val onEditReload: () -> Unit = {},
    // #672 — hoisted card-list scroll state: the workspace owns the anchor (saved to
    // the section context on dispose), the card list renders it. Null keeps the
    // list's own remembered state (desktop table ignores this leg).
    val lazyListState: LazyListState? = null,
)

// #95 — platform-split list actual: desktopMain = table with header + selectable rows
// (RemittanceScreenParts precedent); androidMain = LazyColumn cards (spec line 111).
// #672 — the desktop actual renders the shared card list below the compact-rows
// breakpoint instead of a shrunken table (the #671 width-driven precedent).
@Composable
internal expect fun SessionList(
    args: SessionListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
)

/** #672 — the workspace empty states: a day with no sessions vs a filter with no matches. */
internal data class DashboardEmptyArgs(
    val branchName: String?,
    val dateLabel: String?,
    val kind: DashboardEmptyKind,
    val canCreate: Boolean = false,
    val onClearFilters: () -> Unit = {},
    val onCreate: () -> Unit = {},
)

@Composable
internal expect fun DashboardEmptyState(
    args: DashboardEmptyArgs,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
)

/**
 * #150 — the centered empty-state content shared by both [DashboardEmptyState] actuals.
 * #672 — the copy names the viewed day (never "today" for history); the filtered
 * state offers a working reset instead of a dead end.
 */
@Composable
internal fun EmptyStateContent(
    args: DashboardEmptyArgs,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (args.kind) {
            DashboardEmptyKind.NO_SESSIONS_FOR_DAY -> {
                Text(
                    text =
                        if (args.dateLabel != null) {
                            "No sessions on ${args.dateLabel}"
                        } else {
                            "No sessions for this day"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "Sessions will appear here as practitioners log them." +
                            (args.branchName?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                )
                if (args.canCreate) {
                    // #672 — the empty surface carries the entry point as the secondary
                    // action: the workspace header already holds the one filled primary.
                    SecondaryActionButton(
                        label = "New session",
                        onClick = args.onCreate,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            DashboardEmptyKind.NO_FILTER_MATCHES -> {
                Text(
                    text = "No sessions match these filters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Try a different filter, or clear them to see the whole day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                )
                SecondaryActionButton(
                    label = "Clear filters",
                    onClick = args.onClearFilters,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

/**
 * The dashboard's selection inputs (#97/#348/#351): the branch label rendered in the header
 * and the optional session id the list highlights — bundled to keep the signature lean.
 * #672 — the viewed operational date names the day in the header and the empty copy.
 */
data class DashboardSelection(
    val branchName: String?,
    val sessionId: String?,
    val operationalDate: String? = null,
)

/**
 * #97 session dashboard, #672 stable branch-day workspace.
 *
 * Header (branch/day + filled New session), one quiet summary line (count, gross,
 * labeled personal commission), one stable toolbar (All / Pending / Completed,
 * Hide-voided, Team with actionable count, Refresh) with a reserved freshness slot,
 * then the session list. Attendance/relief live behind the Team sheet — an overlay,
 * never a third permanent column — so the session task keeps the full width.
 *
 * Poll lifecycle: resumed while this screen is composed, paused on leave (mobile detail push;
 * desktop inline pane never leaves). States per Q6: cold spinner / error card with Retry /
 * empty (day-named) / filtered-empty (reset) / stale + errored inline bands that never
 * replace a populated list / forbidden card (attendance gate 403).
 *
 * 7 params: the two feature-content slots ride the #351 slot pattern (relief access,
 * #404 attendance) — grouping them would churn both hosts for no clarity gain; the
 * team count is the toolbar's badge for the Team sheet trigger.
 */
@Suppress("LongParameterList") // #672 7-param slots+badges ride the #351/#594 pattern
@Composable
fun SessionDashboardScreen(
    viewModel: SessionDashboardViewModel,
    selection: DashboardSelection,
    onSessionClick: (DashboardSessionResponse) -> Unit,
    // #348 — the dashboard's entry into the start-a-session flow (both platforms).
    onSessionCreateClick: () -> Unit,
    // #351 — the relief-access surface (requester entry + incoming Grant/Deny), slotted so
    // this screen stays agnostic of the feature's VM/state sources. #672 renders the
    // slot inside the Team sheet.
    reliefAccessContent: @Composable () -> Unit = {},
    // #404 — member-marked attendance (roster + Present/Absent), same slot pattern.
    // #672 renders the slot inside the Team sheet.
    attendanceContent: @Composable () -> Unit = {},
    // #672 — actionable incoming relief-request count for the Team trigger; null hides
    // the badge (requests not yet loaded), never the trigger itself.
    teamRequestCount: Int? = null,
) {
    val state by viewModel.dashboardState.collectAsState()
    val lastData by viewModel.lastData.collectAsState()
    val isForbidden by viewModel.isForbidden.collectAsState()
    val pollStatus by viewModel.pollStatus.collectAsState()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsState()
    val canEdit by viewModel.canEdit.collectAsState()
    val canCorrectStatus by viewModel.canCorrectStatus.collectAsState()
    val edit by viewModel.editState.collectAsState()
    val dayStatus by viewModel.dayStatus.collectAsState()
    val snapshot by AppSessionState.snapshot.collectAsState()
    // Q5 "silent polling": the pull-to-refresh indicator must show ONLY for a user-initiated
    // refresh, never for the 30s poll cycle's Loading frame (pass-1 HARD).
    var isManualRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is UiState.Loading) {
            isManualRefreshing = false
        }
    }
    // #150 — the manual-refresh closure shared by the toolbar Refresh and the cold
    // error card. The explicit `() -> Unit` coerces refresh()'s Job away.
    val onManualRefresh: () -> Unit = {
        isManualRefreshing = true
        viewModel.refresh()
    }
    // #672 — the workspace filter survives section switches per user+branch (the drawer
    // collapses to the Dashboard root on switch, so screen-held state alone would reset
    // on every return). Hide-voided restarts off; the Team sheet is entry-local.
    val userId = snapshot.user?.id
    val branchId = snapshot.clock?.branchId
    var filter by rememberSaveable(userId, branchId) {
        mutableStateOf(
            restoredDashboardFilter(
                NavigationContextStore.retained(userId, branchId, Route.Dashboard())?.tab,
            ),
        )
    }
    var hideVoided by rememberSaveable(userId, branchId) { mutableStateOf(false) }
    // #672 — the sheet is entry-local like the filter: a branch switch rekeys it shut
    // rather than holding another branch's team surface open.
    var showTeam by rememberSaveable(userId, branchId) { mutableStateOf(false) }
    // #672 — the card-list scroll anchor survives detail pushes and section switches
    // per user+branch (saved on dispose, restored below); refreshes and filter
    // changes keep the live position — the list never jumps under the operator.
    val cardListState =
        remember(userId, branchId) {
            val anchor =
                NavigationContextStore
                    .retained(userId, branchId, Route.Dashboard())
                    ?.scrollAnchorId
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0
            LazyListState(firstVisibleItemIndex = anchor)
        }
    DisposableEffect(userId, branchId) {
        onDispose {
            NavigationContextStore.retain(
                userId,
                branchId,
                Route.Dashboard(),
                selectedId = null,
                scrollAnchorId = cardListState.firstVisibleItemIndex.toString(),
            )
        }
    }
    // #672 — closing the Team sheet returns focus to the Team trigger (opened state
    // tracked so the initial composition never steals focus; keyed with the sheet so
    // a branch switch cannot fire a stale return onto the new branch's trigger).
    val teamTriggerFocus = remember { FocusRequester() }
    var teamWasOpen by remember(userId, branchId) { mutableStateOf(false) }
    // #680 — relief users open on the Relief tab (their operational work); members keep
    // the Attendance default for today's operational work.
    val teamDefaultTab = if (snapshot.clock?.isRelief == true) TeamTab.RELIEF else TeamTab.ATTENDANCE
    LaunchedEffect(showTeam) {
        if (showTeam) {
            teamWasOpen = true
        } else if (teamWasOpen) {
            teamTriggerFocus.requestFocus()
        }
    }
    // #348 gate — New session is offered wherever the create route would admit the
    // caller (any-context EDIT_BRANCH_DATA + a clocked-in branch); the backend gate
    // stays authoritative.
    val canCreate =
        snapshot.capabilities.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA) &&
            branchId != null

    DisposableEffect(Unit) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }

    Box {
        if (!DashboardStateGate(viewModel, state, isForbidden, lastData != null)) {
            val data = lastData
            if (data != null) {
                DashboardWorkspace(
                    data = data,
                    selection = selection,
                    filter = filter,
                    onFilterChange = { next ->
                        filter = next
                        NavigationContextStore.retain(
                            userId,
                            branchId,
                            Route.Dashboard(),
                            selectedId = null,
                            tab = next.name,
                        )
                    },
                    hideVoided = hideVoided,
                    onHideVoidedChange = { hideVoided = it },
                    showTeam = showTeam,
                    onShowTeamChange = { showTeam = it },
                    teamTriggerFocus = teamTriggerFocus,
                    teamRequestCount = teamRequestCount,
                    teamDefaultTab = teamDefaultTab,
                    pollStatus = pollStatus,
                    lastUpdatedAt = lastUpdatedAt,
                    isRefreshing = isManualRefreshing && state is UiState.Loading,
                    canCreate = canCreate,
                    canEdit = canEdit,
                    canCorrectStatus = canCorrectStatus,
                    edit = edit,
                    dayStatus = dayStatus,
                    onSessionClick = onSessionClick,
                    onSessionCreateClick = onSessionCreateClick,
                    onManualRefresh = onManualRefresh,
                    onClearFilters = {
                        filter = DashboardFilter.ALL
                        hideVoided = false
                        NavigationContextStore.retain(
                            userId,
                            branchId,
                            Route.Dashboard(),
                            selectedId = null,
                            tab = DashboardFilter.ALL.name,
                        )
                    },
                    cardListState = cardListState,
                    viewModel = viewModel,
                    attendanceContent = attendanceContent,
                    reliefAccessContent = reliefAccessContent,
                )
            }
        }
    }
}

// #462 — BoxScope receiver: the cold-start spinner's align needs the Box scope, and the
// receiver keeps the pre-content gate at 4 params.
@Composable
private fun BoxScope.DashboardStateGate(
    viewModel: SessionDashboardViewModel,
    state: UiState<DashboardResponse>,
    isForbidden: Boolean,
    hasData: Boolean,
): Boolean =
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
            true
        }

        !hasData && (state is UiState.Idle || state is UiState.Loading) -> {
            // Q6a — cold start: full-screen spinner, no skeletons.
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            true
        }

        !hasData && state is UiState.Error -> {
            InPlaceCard(
                title = "Couldn't load the dashboard",
                body = state.message,
                actionLabel = "Retry",
                onAction = viewModel::refresh,
            )
            true
        }

        else -> {
            false
        }
    }

/**
 * #672 — the workspace column: header, summary line, stable toolbar, reserved
 * freshness slot, then the filtered list (populated rows stay mounted across
 * refreshes — background Loading/STALE/ERRORED never replace them). 26 params ride
 * one call site from the screen above (the #535 declarative-UI shape: one leg per
 * workspace-owned input, bundled no further; the slots keep the #351 pattern).
 */
@Suppress("LongParameterList") // #672 workspace legs stay whole per #535; slots ride #351
@Composable
private fun DashboardWorkspace(
    data: DashboardResponse,
    selection: DashboardSelection,
    filter: DashboardFilter,
    onFilterChange: (DashboardFilter) -> Unit,
    hideVoided: Boolean,
    onHideVoidedChange: (Boolean) -> Unit,
    showTeam: Boolean,
    onShowTeamChange: (Boolean) -> Unit,
    teamTriggerFocus: FocusRequester,
    teamRequestCount: Int?,
    teamDefaultTab: TeamTab,
    pollStatus: DashboardPollStatus,
    lastUpdatedAt: Instant?,
    isRefreshing: Boolean,
    canCreate: Boolean,
    canEdit: Boolean,
    canCorrectStatus: Boolean,
    edit: DashboardEditState?,
    dayStatus: DayStatus?,
    onSessionClick: (DashboardSessionResponse) -> Unit,
    onSessionCreateClick: () -> Unit,
    onManualRefresh: () -> Unit,
    onClearFilters: () -> Unit,
    cardListState: LazyListState,
    viewModel: SessionDashboardViewModel,
    attendanceContent: @Composable () -> Unit,
    reliefAccessContent: @Composable () -> Unit,
) {
    val filtered = applyDashboardFilter(data.sessions, filter, hideVoided)
    // #672 — the actively edited row stays mounted even when the filter hides it,
    // so its editor keeps a visible commit/discard exit (no stranded machine).
    val visible = ensureEditedRowVisible(data.sessions, filtered, edit?.sessionId)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = OperationalUiContract.isCompactViewport(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHeader(
                branchName = selection.branchName,
                dateLabel = selection.operationalDate,
                canCreate = canCreate,
                onCreate = onSessionCreateClick,
                isCompact = isCompact,
            )
            DashboardSummaryLine(summary = remember(data) { summarizeDashboard(data) })
            DashboardToolbar(
                filter = filter,
                onFilterChange = onFilterChange,
                hideVoided = hideVoided,
                onHideVoidedChange = onHideVoidedChange,
                teamRequestCount = teamRequestCount,
                onTeamClick = { onShowTeamChange(true) },
                teamTriggerModifier = Modifier.focusRequester(teamTriggerFocus),
                onRefresh = onManualRefresh,
                isRefreshing = isRefreshing,
            )
            DashboardFreshness(
                isRefreshing = isRefreshing,
                pollStatus = pollStatus,
                lastUpdatedAt = lastUpdatedAt,
                onRetry = onManualRefresh,
            )
            if (data.sessions.isEmpty()) {
                DashboardEmptyState(
                    args =
                        DashboardEmptyArgs(
                            branchName = selection.branchName,
                            dateLabel = selection.operationalDate,
                            kind = DashboardEmptyKind.NO_SESSIONS_FOR_DAY,
                            canCreate = canCreate,
                            onCreate = onSessionCreateClick,
                        ),
                    onRefresh = onManualRefresh,
                )
            } else if (visible.isEmpty()) {
                DashboardEmptyState(
                    args =
                        DashboardEmptyArgs(
                            branchName = selection.branchName,
                            dateLabel = selection.operationalDate,
                            kind = dashboardEmptyKind(data.sessions.size),
                            onClearFilters = onClearFilters,
                        ),
                    onRefresh = onManualRefresh,
                )
            } else {
                SessionList(
                    args =
                        SessionListArgs(
                            sessions = visible,
                            selectedSessionId = selection.sessionId,
                            onSessionClick = onSessionClick,
                            onRefresh = onManualRefresh,
                            isRefreshing = isRefreshing,
                            canEdit = canEdit,
                            canCorrectStatus = canCorrectStatus,
                            dayStatus = dayStatus,
                            edit = edit,
                            onEditStart = viewModel::startEdit,
                            onEditDraftChange = viewModel::updateDraft,
                            requiresReason = remittedReasonRequired(dayStatus),
                            onEditReasonChange = viewModel::updateReason,
                            onEditCommit = viewModel::commitEdit,
                            onEditDiscard = viewModel::discardEdit,
                            onEditReload = viewModel::reloadAfterConflict,
                            lazyListState = cardListState,
                        ),
                )
            }
        }
        if (showTeam) {
            TeamSheetOverlay(
                isCompact = isCompact,
                onClose = { onShowTeamChange(false) },
                attendanceContent = attendanceContent,
                reliefAccessContent = reliefAccessContent,
                defaultTab = teamDefaultTab,
            )
        }
    }
}

/** #672 — branch/day heading (page size per #670) + the one filled New session action. */
@Composable
private fun DashboardHeader(
    branchName: String?,
    dateLabel: String?,
    canCreate: Boolean,
    onCreate: () -> Unit,
    isCompact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = branchName?.ifBlank { null } ?: "Sessions",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = OperationalUiContract.pageHeadingSize(isCompact),
                        lineHeight = OperationalUiContract.pageHeadingLineHeight(isCompact),
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (dateLabel != null) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // #348 — the flow's entry point stays in the workspace header.
        if (canCreate) {
            PrimaryActionButton(label = "New session", onClick = onCreate)
        }
    }
}

/** #672 — one quiet summary line (count, gross, labeled personal commission). */
@Composable
private fun DashboardSummaryLine(summary: DashboardSummary) {
    Text(
        text = summary.lineText(),
        style = MaterialTheme.typography.bodySmall,
        color = InkSubtle,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
    )
}

/**
 * #672 — one stable toolbar: All / Pending / Completed, Hide-voided toggle, Team
 * (badged with the actionable request count when present), Refresh. Horizontally
 * scrollable on narrow widths so controls never wrap or shift. 9 legs ride one call
 * site from the workspace above (the #535 declarative-UI shape; the Team trigger
 * modifier carries the focus-return leg).
 */
@Suppress("LongParameterList") // #672 toolbar legs stay whole per #535
@Composable
private fun DashboardToolbar(
    filter: DashboardFilter,
    onFilterChange: (DashboardFilter) -> Unit,
    hideVoided: Boolean,
    onHideVoidedChange: (Boolean) -> Unit,
    teamRequestCount: Int?,
    onTeamClick: () -> Unit,
    teamTriggerModifier: Modifier,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    // #672 — filter chips meet the #670 48dp touch target + 2dp focus ring (raw M3
    // chips default to ~32dp with no contract ring).
    val chipModifier = Modifier.operationalTouchTarget().operationalFocusRing()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter == DashboardFilter.ALL,
            onClick = { onFilterChange(DashboardFilter.ALL) },
            label = { Text("All") },
            modifier = chipModifier,
        )
        FilterChip(
            selected = filter == DashboardFilter.PENDING,
            onClick = { onFilterChange(DashboardFilter.PENDING) },
            label = { Text("Pending") },
            modifier = chipModifier,
        )
        FilterChip(
            selected = filter == DashboardFilter.COMPLETED,
            onClick = { onFilterChange(DashboardFilter.COMPLETED) },
            label = { Text("Completed") },
            modifier = chipModifier,
        )
        FilterChip(
            selected = hideVoided,
            onClick = { onHideVoidedChange(!hideVoided) },
            label = { Text("Hide voided") },
            modifier = chipModifier,
        )
        TertiaryActionButton(
            label =
                if (teamRequestCount != null && teamRequestCount > 0) {
                    "Team ($teamRequestCount)"
                } else {
                    "Team"
                },
            onClick = onTeamClick,
            modifier = teamTriggerModifier,
        )
        TertiaryActionButton(
            label = "Refresh",
            onClick = onRefresh,
            // #672 — duplicate submission is disabled while a manual refresh is in
            // flight (the in-flight poll guard makes a second tap a harmless no-op,
            // but the control still says so).
            enabled = !isRefreshing,
        )
    }
}

/**
 * #672 — reserved freshness slot: Updating… / last-updated / Could-not-update Retry
 * render in a fixed-minimum band, so background refreshes never move New session,
 * the toolbar, or the list. Access revocation stays a protected-data card (the
 * gate above), never a retry state here.
 */
@Composable
private fun DashboardFreshness(
    isRefreshing: Boolean,
    pollStatus: DashboardPollStatus,
    lastUpdatedAt: Instant?,
    onRetry: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FRESHNESS_MIN_HEIGHT.dp)
                .padding(horizontal = Spacing.md)
                // #672 — the #670 polite live region: status changes announce without
                // stealing focus (the fixed-min band above keeps geometry stable).
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isRefreshing -> {
                Text(
                    text = "Updating…",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.weight(1f),
                )
            }

            pollStatus == DashboardPollStatus.ERRORED -> {
                Text(
                    text = "Could not update",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TertiaryActionButton(label = "Retry", onClick = onRetry)
            }

            pollStatus == DashboardPollStatus.STALE -> {
                Text(
                    text =
                        if (lastUpdatedAt == null) {
                            "Updates paused — showing the last loaded data"
                        } else {
                            "Stale — Updated ${formatTimeOfDay(lastUpdatedAt)}"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            lastUpdatedAt != null -> {
                // Q5a — timestamp of the LAST SUCCESSFUL refresh, never the last attempt.
                Text(
                    text = "Updated ${formatTimeOfDay(lastUpdatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                // Cold-loaded without a timestamp yet: reserve the band, render nothing.
                Text(text = "", modifier = Modifier.weight(1f))
            }
        }
    }
}

/** #680 — the Team sheet tabs: Attendance stays default for today's operational work. */
enum class TeamTab {
    ATTENDANCE,
    RELIEF,
}

/**
 * #672 — the Team sheet: attendance + relief requests/invites as an overlay panel
 * (full-width on compact screens), never a third permanent column. The workspace
 * list stays composed underneath, so closing restores row/scroll exactly; focus
 * returns to the Team trigger (screen-owned LaunchedEffect).
 *
 * #680 — Attendance and Relief ride separate tabs (Attendance default for today's
 * operational work); a SaveableStateHolder keeps each tab's drafts across switches,
 * and closing still returns focus to the Team trigger.
 */
@Composable
private fun TeamSheetOverlay(
    isCompact: Boolean,
    onClose: () -> Unit,
    attendanceContent: @Composable () -> Unit,
    reliefAccessContent: @Composable () -> Unit,
    defaultTab: TeamTab = TeamTab.ATTENDANCE,
) {
    var tab by rememberSaveable(defaultTab) { mutableStateOf(defaultTab) }
    val holder = rememberSaveableStateHolder()
    Box(modifier = Modifier.fillMaxSize()) {
        // #672 — dismiss surface carries an accessible label (redundant with Close
        // by design, so pointer and screen-reader dismissal agree).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = TEAM_SCRIM_ALPHA))
                    .clickable(role = Role.Button, onClickLabel = "Close team panel", onClick = onClose),
        ) {}
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .then(
                        if (isCompact) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.width(TEAM_SHEET_WIDTH.dp)
                        },
                    ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Team",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TertiaryActionButton(label = "Close", onClick = onClose)
                }
                // #680 — Attendance / Relief tabs: one row, full-width compact keeps every
                // action (no width-gated hiding here).
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val chipModifier = Modifier.operationalTouchTarget().operationalFocusRing()
                    FilterChip(
                        selected = tab == TeamTab.ATTENDANCE,
                        onClick = { tab = TeamTab.ATTENDANCE },
                        label = { Text("Attendance") },
                        modifier = chipModifier,
                    )
                    FilterChip(
                        selected = tab == TeamTab.RELIEF,
                        onClick = { tab = TeamTab.RELIEF },
                        label = { Text("Relief") },
                        modifier = chipModifier,
                    )
                }
                // #680 — each tab scrolls independently and positions hold across actions
                // within a tab (the holder retains typed drafts across tab switches; plain
                // scroll states reset on switch by design).
                holder.SaveableStateProvider(TEAM_TAB_ATTENDANCE_KEY) {
                    if (tab == TeamTab.ATTENDANCE) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            attendanceContent()
                        }
                    }
                }
                holder.SaveableStateProvider(TEAM_TAB_RELIEF_KEY) {
                    if (tab == TeamTab.RELIEF) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            reliefAccessContent()
                        }
                    }
                }
            }
        }
    }
}

private const val FRESHNESS_MIN_HEIGHT = 32
private const val TEAM_SHEET_WIDTH = 400
private const val TEAM_SCRIM_ALPHA = 0.5f
private const val TEAM_TAB_ATTENDANCE_KEY = "team-tab-attendance"
private const val TEAM_TAB_RELIEF_KEY = "team-tab-relief"
