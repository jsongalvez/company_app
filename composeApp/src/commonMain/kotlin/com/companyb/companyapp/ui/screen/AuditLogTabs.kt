package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AuditLogFilters
import com.companyb.companyapp.viewmodel.AuditLogViewModel
import com.companyb.companyapp.viewmodel.applyFilters
import com.companyb.companyapp.viewmodel.loadBrowse
import com.companyb.companyapp.viewmodel.loadMore
import com.companyb.companyapp.viewmodel.refreshBrowse
import com.companyb.companyapp.viewmodel.retryBrowse

// #479 — the audit-log tab seam (#104 D1/D5/D10), extracted from AuditLogScreen.kt so the
// file-function wall (TMF) stays honest: the tab content switch, the For-review and
// All-activity hosts, and the collected-VM-state holder. Tab-wide inputs ride carriers
// ([AuditLogTabUi]/[AuditLogTabExtras]/[AuditLogBrowseUi]) so every signature stays under the
// LongParameterList threshold (5); the carriers are remembered/derived here (self-collecting,
// the #477 SessionList precedent).

/**
 * Entry-context + acknowledge state shared by both tabs (D2/D7). `tableLabels` derives in
 * [AuditLogTabContent] (it owns the collected tables) and rides the copy.
 */
internal data class AuditLogTabUi(
    val currentUserId: String?,
    val hasAnyCapability: Boolean,
    val expandedIds: Set<String>,
    val onToggleExpanded: (String) -> Unit,
    val acknowledgingIds: Set<String>,
    val ackErrors: Map<String, String>,
    val onAcknowledge: (AuditLogEntryResponse) -> Unit,
    val tableLabels: Map<String, String> = emptyMap(),
)

/**
 * Navigation + retry affordances the VM cannot derive: the history drill-down, the #390
 * client-record jump, and the per-tab cold retries (the cold/loud paths).
 */
internal data class AuditLogTabExtras(
    val onFullHistory: (AuditLogEntryResponse) -> Unit,
    val onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
    val onRetryFlagged: () -> Unit,
    val onRetryBrowse: () -> Unit,
    val onRetryTables: () -> Unit,
)

/**
 * The All-activity tab's browse controls + pinned load-more state (D5/D10), self-collected
 * from the VM by [rememberAuditLogBrowseUi].
 */
internal data class AuditLogBrowseUi(
    val refreshError: String?,
    val filterDraft: AuditLogFilterDraft,
    val filtersApplied: Boolean,
    val onApplyFilters: (AuditLogFilters) -> Unit,
    val onRetryBrowse: () -> Unit,
    val onRetryTables: () -> Unit,
    val onLoadMore: () -> Unit,
    val hasMore: Boolean,
    val isLoadingMore: Boolean,
    val loadMoreError: String?,
)

/** Per-tab refresh affordance state for the top bar (the in-flight couplings, #104 D10). */
internal data class AuditLogRefreshControls(
    val flaggedLoadInFlight: Boolean,
    val isRefreshing: Boolean,
    val isLoadingMore: Boolean,
    val onRefreshFlagged: () -> Unit,
    val onRefreshBrowse: () -> Unit,
)

@Composable
internal fun rememberAuditLogTabUi(
    viewModel: AuditLogViewModel,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
): AuditLogTabUi =
    AuditLogTabUi(
        currentUserId = currentUserId,
        hasAnyCapability = hasAnyCapability,
        expandedIds = expandedIds,
        onToggleExpanded = onToggleExpanded,
        acknowledgingIds = viewModel.acknowledgingIds.collectAsState().value,
        ackErrors = viewModel.ackErrors.collectAsState().value,
        onAcknowledge = viewModel::acknowledge,
    )

internal fun rememberAuditLogBrowseUi(
    viewModel: AuditLogViewModel,
    collected: AuditLogCollected,
    filterDraft: AuditLogFilterDraft,
): AuditLogBrowseUi =
    AuditLogBrowseUi(
        refreshError = collected.browseRefreshError,
        filterDraft = filterDraft,
        filtersApplied = collected.appliedFilters != AuditLogFilters(),
        onApplyFilters = viewModel::applyFilters,
        onRetryBrowse = viewModel::retryBrowse,
        onRetryTables = viewModel::loadTables,
        onLoadMore = viewModel::loadMore,
        hasMore = collected.nextCursor != null,
        isLoadingMore = collected.isLoadingMore,
        loadMoreError = collected.loadMoreError,
    )

internal fun rememberAuditLogRefreshControls(
    viewModel: AuditLogViewModel,
    collected: AuditLogCollected,
): AuditLogRefreshControls =
    AuditLogRefreshControls(
        flaggedLoadInFlight = collected.flaggedLoadInFlight,
        isRefreshing = collected.isRefreshing,
        isLoadingMore = collected.isLoadingMore,
        onRefreshFlagged = viewModel::refreshFlagged,
        onRefreshBrowse = viewModel::refreshBrowse,
    )

@Composable
internal fun rememberAuditLogCollected(viewModel: AuditLogViewModel): AuditLogCollected {
    val flaggedEntries by viewModel.flaggedEntries.collectAsState()
    val browseEntries by viewModel.browseEntries.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val acknowledgingIds by viewModel.acknowledgingIds.collectAsState()
    val ackErrors by viewModel.ackErrors.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val flaggedLoadInFlight by viewModel.flaggedLoadInFlight.collectAsState()
    val flaggedRefreshError by viewModel.flaggedRefreshError.collectAsState()
    val browseRefreshError by viewModel.browseRefreshError.collectAsState()
    val appliedFilters by viewModel.appliedFilters.collectAsState()
    val nextCursor by viewModel.nextCursor.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadMoreError by viewModel.loadMoreError.collectAsState()
    return AuditLogCollected(
        flaggedEntries = flaggedEntries,
        browseEntries = browseEntries,
        tables = tables,
        acknowledgingIds = acknowledgingIds,
        ackErrors = ackErrors,
        isRefreshing = isRefreshing,
        flaggedLoadInFlight = flaggedLoadInFlight,
        flaggedRefreshError = flaggedRefreshError,
        browseRefreshError = browseRefreshError,
        appliedFilters = appliedFilters,
        nextCursor = nextCursor,
        isLoadingMore = isLoadingMore,
        loadMoreError = loadMoreError,
    )
}

internal data class AuditLogCollected(
    val flaggedEntries: UiState<List<AuditLogEntryResponse>>,
    val browseEntries: UiState<List<AuditLogEntryResponse>>,
    val tables: UiState<List<AuditLogTableResponse>>,
    val acknowledgingIds: Set<String>,
    val ackErrors: Map<String, String>,
    val isRefreshing: Boolean,
    val flaggedLoadInFlight: Boolean,
    val flaggedRefreshError: String?,
    val browseRefreshError: String?,
    val appliedFilters: AuditLogFilters,
    val nextCursor: String?,
    val isLoadingMore: Boolean,
    val loadMoreError: String?,
)

@Composable
internal fun AuditLogTabEffects(
    viewModel: AuditLogViewModel,
    selectedTab: Int,
    hasVisitedAllActivity: Boolean,
    onFirstBrowseVisit: () -> Unit,
) {
    // D10 — For-review refreshes on tab re-entry via the SILENT path (refreshFlagged), so the
    // loaded list survives the reload (keep-last-list); a cold first load is the loud path above.
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_FOR_REVIEW) {
            // Idle-check: on a fresh VM after process death the flag restores true while the
            // list is Idle — the Unit effect cold-loads it; the tab effect must not fire a
            // silent refresh over an empty list (a failed one would leave Idle + error line,
            // no ErrorCard). Declaration order makes this safe today; the check removes the
            // coupling.
            if (hasVisitedAllActivity && viewModel.flaggedEntries.value !is UiState.Idle) {
                // Not the first composition: this is a re-entry (tab switch back).
                viewModel.refreshFlagged()
            }
        } else if (
            !hasVisitedAllActivity ||
            viewModel.browseEntries.value is UiState.Idle ||
            viewModel.browseEntries.value is UiState.Error
        ) {
            // First visit, OR a fresh VM after process death whose saveable flag restored true
            // (a VM that never loaded has no list to keep), OR a failed load with only an error
            // card to show — all take the cold loud path (D10 load-on-entry + auto-retry).
            onFirstBrowseVisit()
            // D10 — the first visit is a load-on-entry: the cold loud path (Loading → error
            // card + retry), unlike later re-entries which refresh silently (keep-last-list).
            viewModel.loadBrowse()
        }
    }
}

@Composable
internal fun AuditLogTabContent(
    selectedTab: Int,
    collected: AuditLogCollected,
    tabUi: AuditLogTabUi,
    extras: AuditLogTabExtras,
    browse: AuditLogBrowseUi,
) {
    val tableLabels =
        (collected.tables as? UiState.Success<List<AuditLogTableResponse>>)
            ?.data
            ?.associate { it.tableName to it.label }
            .orEmpty()
    val tabUi = tabUi.copy(tableLabels = tableLabels)
    if (selectedTab == TAB_FOR_REVIEW) {
        ForReviewTab(
            state = collected.flaggedEntries,
            refreshError = collected.flaggedRefreshError,
            tabUi = tabUi,
            extras = extras,
        )
    } else {
        AllActivityTab(
            state = collected.browseEntries,
            tables = collected.tables,
            tabUi = tabUi,
            extras = extras,
            browse = browse,
        )
    }
}

@Composable
private fun ForReviewTab(
    state: UiState<List<AuditLogEntryResponse>>,
    refreshError: String?,
    tabUi: AuditLogTabUi,
    extras: AuditLogTabExtras,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RefreshErrorLine(refreshError)
        when (state) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                logWarn("AuditLogScreen", "flaggedState=Error: ${state.message}")
                ErrorCard(
                    message = state.message,
                    onRetry = extras.onRetryFlagged,
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        message =
                            if (tabUi.hasAnyCapability) {
                                "All flagged edits reviewed"
                            } else {
                                "No branch access"
                            },
                    )
                } else {
                    AuditLogEntryList(
                        args =
                            AuditLogEntryListArgs(
                                entries = state.data,
                                tableLabels = tabUi.tableLabels,
                                expandedIds = tabUi.expandedIds,
                                onToggleExpanded = tabUi.onToggleExpanded,
                                currentUserId = tabUi.currentUserId,
                                onAcknowledge = tabUi.onAcknowledge,
                                acknowledgingIds = tabUi.acknowledgingIds,
                                ackErrors = tabUi.ackErrors,
                                onFullHistory = extras.onFullHistory,
                                onOpenClientRecord = extras.onOpenClientRecord,
                                showAcknowledge = true,
                                showFullHistory = true,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AllActivitySuccessContent(
    entries: List<AuditLogEntryResponse>,
    tabUi: AuditLogTabUi,
    extras: AuditLogTabExtras,
    browse: AuditLogBrowseUi,
) {
    if (entries.isEmpty()) {
        EmptyState(
            message =
                when {
                    !tabUi.hasAnyCapability -> "No branch access"
                    browse.filtersApplied -> "No activity matches the filters"
                    else -> "No activity yet"
                },
        )
    } else {
        // The list actual handles its own scrolling; the load-more affordances stay
        // pinned below it (D5 "Load more" on both platforms).
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            AuditLogEntryList(
                args =
                    AuditLogEntryListArgs(
                        entries = entries,
                        tableLabels = tabUi.tableLabels,
                        expandedIds = tabUi.expandedIds,
                        onToggleExpanded = tabUi.onToggleExpanded,
                        currentUserId = tabUi.currentUserId,
                        onAcknowledge = tabUi.onAcknowledge,
                        acknowledgingIds = tabUi.acknowledgingIds,
                        ackErrors = tabUi.ackErrors,
                        onFullHistory = extras.onFullHistory,
                        onOpenClientRecord = extras.onOpenClientRecord,
                        showAcknowledge = true,
                        showFullHistory = true,
                    ),
                // weight(1f), not fillMaxSize: the pinned Load-more affordances below
                // must keep their height (D5; pass-7 HARD).
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
            if (browse.loadMoreError != null) {
                InlineErrorText(
                    text = browse.loadMoreError,
                    modifier = Modifier.padding(Spacing.xs),
                )
            }
            if (browse.hasMore) {
                TextButton(
                    onClick = browse.onLoadMore,
                    enabled = !browse.isLoadingMore,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (browse.isLoadingMore) "Loading…" else "Load more")
                }
            }
        }
    }
}

@Composable
private fun AllActivityTab(
    state: UiState<List<AuditLogEntryResponse>>,
    tables: UiState<List<AuditLogTableResponse>>,
    tabUi: AuditLogTabUi,
    extras: AuditLogTabExtras,
    browse: AuditLogBrowseUi,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RefreshErrorLine(browse.refreshError)
        AuditLogFilterBar(
            tables = tables,
            onRetryTables = browse.onRetryTables,
            draft = browse.filterDraft,
            onApply = browse.onApplyFilters,
        )

        when (state) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                logWarn("AuditLogScreen", "browseState=Error: ${state.message}")
                ErrorCard(
                    message = state.message,
                    onRetry = browse.onRetryBrowse,
                )
            }

            is UiState.Success -> {
                AllActivitySuccessContent(
                    entries = state.data,
                    tabUi = tabUi,
                    extras = extras,
                    browse = browse,
                )
            }
        }
    }
}
