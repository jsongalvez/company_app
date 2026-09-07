@file:Suppress("DEPRECATION")

package com.companyb.companyapp.audit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.audit.AuditLogEntryResponse
import com.companyb.companyapp.contracts.audit.AuditLogTableResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// D1/D10 — two tabs (For review default, All activity filtered browse), load-on-entry + manual
// refresh, no polling, cold-start spinner, in-place error card + retry, keep-last-list on
// refresh failure. D9 — no route gate; a zero-grant caller sees the "No branch access" empty
// state (`hasAnyCapability` computed from SessionState at the NavHost call site, #108/#99 D7
// code-only pattern; the backend is authoritative either way — 200 + empty list).
@Composable
private fun AuditLogScreenLoadEffects(viewModel: AuditLogViewModel) {
    LaunchedEffect(Unit) {
        logInfo("AuditLogScreen", "composable entered")
        // D4 server-driven registry: load once per VM lifetime (Idle), re-fire from an error
        // state (auto-retry — same policy as the lists); a nav round-trip with a loaded
        // registry must not reset it to "Loading tables…" (pass-5 SOFT). Loading skips: an
        // in-flight fetch owns the slot.
        if (viewModel.tables.value is UiState.Idle || viewModel.tables.value is UiState.Error) {
            viewModel.loadTables()
        }
        // Cold loud load only when this VM has nothing loaded (first composition, or a fresh VM
        // after process death — saveable flags don't survive into a fresh VM's list state). A
        // surviving VM (rotation, history round-trip) with a loaded list takes the silent refresh
        // path so the list never wipes (D10 keep-last-list).
        if (viewModel.flaggedEntries.value is UiState.Success) {
            viewModel.refreshFlagged()
        } else {
            viewModel.loadFlaggedEntries()
        }
    }
}

@Composable
private fun AuditLogTopBar(
    selectedTab: Int,
    refresh: AuditLogRefreshControls,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Audit Log",
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(
            onClick = {
                if (selectedTab == TAB_FOR_REVIEW) {
                    refresh.onRefreshFlagged()
                } else {
                    refresh.onRefreshBrowse()
                }
            },
            // Per-tab in-flight coupling: the flagged tab's button tracks the flagged load
            // guard; the All-activity tab's tracks the browse flags (the VM guards remain
            // authoritative against double-fires either way).
            enabled =
                if (selectedTab == TAB_FOR_REVIEW) {
                    !refresh.flaggedLoadInFlight
                } else {
                    !refresh.isRefreshing && !refresh.isLoadingMore
                },
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun AuditLogTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == TAB_FOR_REVIEW,
            onClick = { onSelectTab(TAB_FOR_REVIEW) },
            text = { Text("For review") },
        )
        Tab(
            selected = selectedTab == TAB_ALL_ACTIVITY,
            onClick = { onSelectTab(TAB_ALL_ACTIVITY) },
            text = { Text("All activity") },
        )
    }
}

@Composable
private fun rememberAuditLogFilterDraft(): AuditLogFilterDraft =
    // Hoisted filter-bar draft state: the bar lives inside the All-activity tab branch, so its
    // local remember would be disposed on every tab switch — the hoist keeps the typed values
    // across switches (and nav round-trips, via the saver) so the bar can't drift from the
    // applied filters it rendered.
    rememberSaveable(saver = AuditLogFilterDraftSaver) { AuditLogFilterDraft() }

@Composable
private fun AuditLogScreenEffects(
    viewModel: AuditLogViewModel,
    selectedTab: Int,
    hasVisitedAllActivity: Boolean,
    onFirstBrowseVisit: () -> Unit,
) {
    AuditLogScreenLoadEffects(viewModel)
    AuditLogTabEffects(
        viewModel = viewModel,
        selectedTab = selectedTab,
        hasVisitedAllActivity = hasVisitedAllActivity,
        onFirstBrowseVisit = onFirstBrowseVisit,
    )
}

@Composable
private fun rememberAuditLogExpanded(): Pair<Set<String>, (String) -> Unit> {
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    val onToggleExpanded: (String) -> Unit = { id ->
        expandedIds =
            if (id in expandedIds) expandedIds - id else expandedIds + id
    }
    return expandedIds to onToggleExpanded
}

@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    // #390 — "Open client record" jump; null when the caller lacks GLOBAL EDIT_BRANCH_DATA
    // (the backend's client-read scope — fail-closed, a day-grant holder would 403).
    onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
) {
    val collected = rememberAuditLogCollected(viewModel)

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_FOR_REVIEW) }
    val (expandedIds, onToggleExpanded) = rememberAuditLogExpanded()
    val filterDraft = rememberAuditLogFilterDraft()
    // D10 — the All-activity list loads on first visit and keeps its accumulated pages across
    // tab switches; the flag survives nav round-trips (saveable) so a history push → back
    // re-entry stays silent instead of re-colding over the accumulated list.
    var hasVisitedAllActivity by rememberSaveable { mutableStateOf(false) }

    AuditLogScreenEffects(
        viewModel = viewModel,
        selectedTab = selectedTab,
        hasVisitedAllActivity = hasVisitedAllActivity,
        onFirstBrowseVisit = { hasVisitedAllActivity = true },
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        AuditLogTopBar(selectedTab, rememberAuditLogRefreshControls(viewModel, collected))

        AuditLogTabRow(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
        )

        AuditLogTabContent(
            selectedTab = selectedTab,
            collected = collected,
            tabUi = rememberAuditLogTabUi(viewModel, currentUserId, hasAnyCapability, expandedIds, onToggleExpanded),
            extras =
                AuditLogTabExtras(
                    onFullHistory = onFullHistory,
                    onOpenClientRecord = onOpenClientRecord,
                    onRetryFlagged = viewModel::loadFlaggedEntries,
                    onRetryBrowse = viewModel::retryBrowse,
                    onRetryTables = viewModel::loadTables,
                ),
            browse = rememberAuditLogBrowseUi(viewModel, collected, filterDraft),
        )
    }
}

internal const val TAB_FOR_REVIEW = 0
internal const val TAB_ALL_ACTIVITY = 1

internal const val EXPANDED_CHEVRON_ROTATION = 90f
