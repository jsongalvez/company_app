@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

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
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AuditLogFilters
import com.companyb.companyapp.viewmodel.AuditLogViewModel
import com.companyb.companyapp.viewmodel.UiState
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
private fun AuditLogTabEffects(
    viewModel: AuditLogViewModel,
    selectedTab: Int,
    hasVisitedAllActivity: Boolean,
    onFirstBrowseVisit: () -> Unit,
) {
    // D10 — For-review refreshes on tab re-entry via the SILENT path (refreshFlagged), so the
    // loaded list survives the reload (keep-last-list); a cold first load is the loud path above.
    LaunchedEffect(selectedTab) {
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
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    // #390 — "Open client record" jump; null when the caller lacks GLOBAL EDIT_BRANCH_DATA
    // (the backend's client-read scope — fail-closed, a day-grant holder would 403).
    onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
) {
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

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_FOR_REVIEW) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    // Hoisted filter-bar draft state: the bar lives inside the All-activity tab branch, so its
    // local remember would be disposed on every tab switch — the hoist keeps the typed values
    // across switches (and nav round-trips, via the saver) so the bar can't drift from the
    // applied filters it rendered.
    val filterDraft = rememberSaveable(saver = AuditLogFilterDraftSaver) { AuditLogFilterDraft() }

    val onToggleExpanded: (String) -> Unit = { id ->
        expandedIds =
            if (id in expandedIds) expandedIds - id else expandedIds + id
    }
    // D10 — the All-activity list loads on first visit and keeps its accumulated pages across
    // tab switches; the flag survives nav round-trips (saveable) so a history push → back
    // re-entry stays silent instead of re-colding over the accumulated list.
    var hasVisitedAllActivity by rememberSaveable { mutableStateOf(false) }

    val tableLabels =
        (tables as? UiState.Success<List<AuditLogTableResponse>>)
            ?.data
            ?.associate { it.tableName to it.label }
            .orEmpty()

    AuditLogScreenLoadEffects(viewModel)
    AuditLogTabEffects(
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
                        viewModel.refreshFlagged()
                    } else {
                        viewModel.refreshBrowse()
                    }
                },
                // Per-tab in-flight coupling: the flagged tab's button tracks the flagged load
                // guard; the All-activity tab's tracks the browse flags (the VM guards remain
                // authoritative against double-fires either way).
                enabled =
                    if (selectedTab == TAB_FOR_REVIEW) {
                        !flaggedLoadInFlight
                    } else {
                        !isRefreshing && !isLoadingMore
                    },
            ) {
                Text("Refresh")
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == TAB_FOR_REVIEW,
                onClick = { selectedTab = TAB_FOR_REVIEW },
                text = { Text("For review") },
            )
            Tab(
                selected = selectedTab == TAB_ALL_ACTIVITY,
                onClick = { selectedTab = TAB_ALL_ACTIVITY },
                text = { Text("All activity") },
            )
        }

        if (selectedTab == TAB_FOR_REVIEW) {
            ForReviewTab(
                state = flaggedEntries,
                refreshError = flaggedRefreshError,
                currentUserId = currentUserId,
                hasAnyCapability = hasAnyCapability,
                tableLabels = tableLabels,
                expandedIds = expandedIds,
                onToggleExpanded = onToggleExpanded,
                acknowledgingIds = acknowledgingIds,
                ackErrors = ackErrors,
                onAcknowledge = viewModel::acknowledge,
                onFullHistory = onFullHistory,
                onOpenClientRecord = onOpenClientRecord,
                onRetry = viewModel::loadFlaggedEntries,
            )
        } else {
            AllActivityTab(
                state = browseEntries,
                tables = tables,
                refreshError = browseRefreshError,
                currentUserId = currentUserId,
                hasAnyCapability = hasAnyCapability,
                tableLabels = tableLabels,
                expandedIds = expandedIds,
                onToggleExpanded = onToggleExpanded,
                acknowledgingIds = acknowledgingIds,
                ackErrors = ackErrors,
                onAcknowledge = viewModel::acknowledge,
                onFullHistory = onFullHistory,
                onOpenClientRecord = onOpenClientRecord,
                filterDraft = filterDraft,
                filtersApplied = appliedFilters != AuditLogFilters(),
                onApplyFilters = viewModel::applyFilters,
                onRetryBrowse = viewModel::retryBrowse,
                onRetryTables = viewModel::loadTables,
                onLoadMore = viewModel::loadMore,
                hasMore = nextCursor != null,
                isLoadingMore = isLoadingMore,
                loadMoreError = loadMoreError,
            )
        }
    }
}

@Composable
private fun ForReviewTab(
    state: UiState<List<AuditLogEntryResponse>>,
    refreshError: String?,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    tableLabels: Map<String, String>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    acknowledgingIds: Set<String>,
    ackErrors: Map<String, String>,
    onAcknowledge: (AuditLogEntryResponse) -> Unit,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
    onRetry: () -> Unit,
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
                    onRetry = onRetry,
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        message =
                            if (hasAnyCapability) {
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
                                tableLabels = tableLabels,
                                expandedIds = expandedIds,
                                onToggleExpanded = onToggleExpanded,
                                currentUserId = currentUserId,
                                onAcknowledge = onAcknowledge,
                                acknowledgingIds = acknowledgingIds,
                                ackErrors = ackErrors,
                                onFullHistory = onFullHistory,
                                onOpenClientRecord = onOpenClientRecord,
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
private fun RefreshErrorLine(refreshError: String?) {
    if (refreshError != null) {
        InlineErrorText(text = refreshError, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun InlineErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.AllActivitySuccessContent(
    entries: List<AuditLogEntryResponse>,
    hasAnyCapability: Boolean,
    filtersApplied: Boolean,
    tableLabels: Map<String, String>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    currentUserId: String?,
    onAcknowledge: (AuditLogEntryResponse) -> Unit,
    acknowledgingIds: Set<String>,
    ackErrors: Map<String, String>,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: String?,
) {
    if (entries.isEmpty()) {
        EmptyState(
            message =
                when {
                    !hasAnyCapability -> "No branch access"
                    filtersApplied -> "No activity matches the filters"
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
                        tableLabels = tableLabels,
                        expandedIds = expandedIds,
                        onToggleExpanded = onToggleExpanded,
                        currentUserId = currentUserId,
                        onAcknowledge = onAcknowledge,
                        acknowledgingIds = acknowledgingIds,
                        ackErrors = ackErrors,
                        onFullHistory = onFullHistory,
                        onOpenClientRecord = onOpenClientRecord,
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
            if (loadMoreError != null) {
                InlineErrorText(
                    text = loadMoreError,
                    modifier = Modifier.padding(Spacing.xs),
                )
            }
            if (hasMore) {
                TextButton(
                    onClick = onLoadMore,
                    enabled = !isLoadingMore,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (isLoadingMore) "Loading…" else "Load more")
                }
            }
        }
    }
}

@Composable
private fun AllActivityTab(
    state: UiState<List<AuditLogEntryResponse>>,
    tables: UiState<List<AuditLogTableResponse>>,
    refreshError: String?,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    tableLabels: Map<String, String>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    acknowledgingIds: Set<String>,
    ackErrors: Map<String, String>,
    onAcknowledge: (AuditLogEntryResponse) -> Unit,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)?,
    filterDraft: AuditLogFilterDraft,
    filtersApplied: Boolean,
    onApplyFilters: (AuditLogFilters) -> Unit,
    onRetryBrowse: () -> Unit,
    onRetryTables: () -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RefreshErrorLine(refreshError)
        AuditLogFilterBar(
            tables = tables,
            onRetryTables = onRetryTables,
            draft = filterDraft,
            onApply = onApplyFilters,
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
                    onRetry = onRetryBrowse,
                )
            }

            is UiState.Success -> {
                AllActivitySuccessContent(
                    entries = state.data,
                    hasAnyCapability = hasAnyCapability,
                    filtersApplied = filtersApplied,
                    tableLabels = tableLabels,
                    expandedIds = expandedIds,
                    onToggleExpanded = onToggleExpanded,
                    currentUserId = currentUserId,
                    onAcknowledge = onAcknowledge,
                    acknowledgingIds = acknowledgingIds,
                    ackErrors = ackErrors,
                    onFullHistory = onFullHistory,
                    onOpenClientRecord = onOpenClientRecord,
                    onLoadMore = onLoadMore,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    loadMoreError = loadMoreError,
                )
            }
        }
    }
}

// D4/D8 — filter bar: server-driven table dropdown (loading/error states + retry; never
// hardcode labels), action dropdown, date range from/to (inclusive Manila days, yyyy-MM-dd),
// caller-name text. Validation is light (format + ordering); the backend remains authoritative
// (400 → in-place error card). The draft state is hoisted to the screen so tab switches don't
// dispose the typed values.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogDateCallerFields(draft: AuditLogFilterDraft) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = draft.dateFrom,
            onValueChange = {
                draft.dateFrom = it
                // Both errors clear: format is per-field, but the ordering violation spans
                // the pair — a stale To-side error must not outlive its cause.
                draft.dateFromError = null
                draft.dateToError = null
            },
            label = { Text("From") },
            placeholder = { Text("yyyy-MM-dd") },
            isError = draft.dateFromError != null,
            supportingText = { draft.dateFromError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.dateTo,
            onValueChange = {
                draft.dateTo = it
                draft.dateToError = null
            },
            label = { Text("To") },
            placeholder = { Text("yyyy-MM-dd") },
            isError = draft.dateToError != null,
            supportingText = { draft.dateToError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.callerName,
            onValueChange = { draft.callerName = it },
            label = { Text("Caller") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogFilterBar(
    tables: UiState<List<AuditLogTableResponse>>,
    onRetryTables: () -> Unit,
    draft: AuditLogFilterDraft,
    onApply: (AuditLogFilters) -> Unit,
) {
    fun apply() {
        val (fromError, toError) = auditDateRangeErrors(draft.dateFrom, draft.dateTo)
        if (fromError != null || toError != null) {
            draft.dateFromError = fromError
            draft.dateToError = toError
            return
        }
        draft.dateFromError = null
        draft.dateToError = null
        onApply(
            AuditLogFilters(
                tableName = draft.selectedTableName,
                action = draft.selectedAction,
                callerName = draft.callerName.trim().takeIf { it.isNotBlank() },
                dateFrom = draft.dateFrom.trim().takeIf { it.isNotEmpty() },
                dateTo = draft.dateTo.trim().takeIf { it.isNotEmpty() },
            ),
        )
    }

    fun reset() {
        draft.reset()
        onApply(AuditLogFilters())
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TableDropdown(
                tables = tables,
                selectedTableName = draft.selectedTableName,
                onTableSelected = { draft.selectedTableName = it },
                onRetryTables = onRetryTables,
                modifier = Modifier.weight(1f),
            )
            ActionDropdown(
                selectedAction = draft.selectedAction,
                onActionSelected = { draft.selectedAction = it },
                modifier = Modifier.weight(1f),
            )
        }
        AuditLogDateCallerFields(draft)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                TextButton(onClick = { apply() }) {
                    Text("Apply")
                }
                TextButton(onClick = { reset() }) {
                    Text("Reset")
                }
            }
        }
    }
}

// Hoisted filter-bar draft (see AuditLogScreen) — plain state holder, remembered at screen scope.
// Mutation lives on the holder (#142 BpDraftState precedent); the composable only reads + applies.
private class AuditLogFilterDraft {
    var selectedTableName by mutableStateOf<String?>(null)
    var selectedAction by mutableStateOf<String?>(null)
    var callerName by mutableStateOf("")
    var dateFrom by mutableStateOf("")
    var dateTo by mutableStateOf("")
    var dateFromError by mutableStateOf<String?>(null)
    var dateToError by mutableStateOf<String?>(null)

    fun reset() {
        selectedTableName = null
        selectedAction = null
        callerName = ""
        dateFrom = ""
        dateTo = ""
        dateFromError = null
        dateToError = null
    }
}

private val AuditLogFilterDraftSaver =
    Saver<AuditLogFilterDraft, List<String?>>(
        save = { draft ->
            listOf(
                draft.selectedTableName,
                draft.selectedAction,
                draft.callerName,
                draft.dateFrom,
                draft.dateTo,
                // Field-level date errors deliberately excluded: a stale validation message
                // must not resurrect across a save/restore cycle.
            )
        },
        restore = { values ->
            if (values.size == 5) {
                AuditLogFilterDraft().apply {
                    selectedTableName = values[0]
                    selectedAction = values[1]
                    callerName = values[2] ?: ""
                    dateFrom = values[3] ?: ""
                    dateTo = values[4] ?: ""
                }
            } else {
                // Shape drift (code updated between save and restore) — degrade to defaults
                // rather than crash the composition (TimestampFormat precedent).
                logWarn("AuditLogScreen", "filter draft saver shape drift: ${values.size} values")
                AuditLogFilterDraft()
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableDropdownErrorEffect(tables: UiState<List<AuditLogTableResponse>>) {
    LaunchedEffect(tables) {
        if (tables is UiState.Error) {
            logWarn("AuditLogScreen", "tablesState=Error: ${tables.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableDropdown(
    tables: UiState<List<AuditLogTableResponse>>,
    selectedTableName: String?,
    onTableSelected: (String?) -> Unit,
    onRetryTables: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isError = tables is UiState.Error
    val isIdle = tables is UiState.Idle
    val isLoading = tables is UiState.Loading
    TableDropdownErrorEffect(tables)
    val tableOptions = (tables as? UiState.Success<List<AuditLogTableResponse>>)?.data.orEmpty()
    val selectedLabel =
        tableOptions.find { it.tableName == selectedTableName }?.label ?: "All tables"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (isError || isIdle) {
                onRetryTables()
            } else if (!isLoading) {
                expanded = !expanded
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value =
                when {
                    isError -> "Tables unavailable — tap to retry"
                    isLoading || isIdle -> "Loading tables…"
                    else -> selectedLabel
                },
            onValueChange = {},
            readOnly = true,
            label = { Text("Table") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = !isLoading && !isIdle,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All tables") },
                onClick = {
                    onTableSelected(null)
                    expanded = false
                },
            )
            tableOptions.forEach { table ->
                DropdownMenuItem(
                    text = { Text("${table.label} (${table.tableName})") },
                    onClick = {
                        onTableSelected(table.tableName)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(
    selectedAction: String?,
    onActionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedAction ?: "All actions",
            onValueChange = {},
            readOnly = true,
            label = { Text("Action") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All actions") },
                onClick = {
                    onActionSelected(null)
                    expanded = false
                },
            )
            AUDIT_ACTIONS.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action) },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AuditLogExpandedDetails(
    entry: AuditLogEntryResponse,
    canAcknowledge: Boolean,
    acknowledging: Boolean,
    onAcknowledge: () -> Unit,
    showFullHistory: Boolean,
    onFullHistory: () -> Unit,
    onOpenClientRecord: (() -> Unit)?,
    ackError: String?,
) {
    Column(
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
    ) {
        val (fields, malformedDiff) =
            remember(entry.oldValue, entry.newValue) {
                parseDiff(entry.oldValue, entry.newValue)
            }
        when {
            // D3 — a present-but-unparseable diff side is server-data corruption: render
            // nothing rather than a misleading "no changes" line (corruption ≠ absence).
            malformedDiff -> {
                Unit
            }

            fields.isEmpty() -> {
                Text(
                    text = "No field changes recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                ChangedFieldsList(fields = fields, action = entry.action)
            }
        }
        entry.reason?.let { reason ->
            Text(
                text = "Reason: $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        AuditLogEntryActions(
            entry = entry,
            canAcknowledge = canAcknowledge,
            acknowledging = acknowledging,
            onAcknowledge = onAcknowledge,
            showFullHistory = showFullHistory,
            onFullHistory = onFullHistory,
            onOpenClientRecord = onOpenClientRecord,
        )
        if (ackError != null) {
            InlineErrorText(text = ackError)
        }
    }
}

@Composable
private fun AuditLogEntryHeader(
    entry: AuditLogEntryResponse,
    tableLabel: String,
    expanded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ActionPill(action = entry.action)
        Text(
            text = tableLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.isFlagged) {
            FlagBadge()
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = entry.changedByName ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = formatRelativeTimestamp(entry.changedAt, logTag = "AuditLogScreen"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
        )
    }
}

// D11 — shared row in commonMain; the platform list actuals diverge only in row chrome (desktop
// dense rows / mobile cards, #95 smallest-divergent-subtree). Renders: action pill + table label
// + flag badge + changedByName + timestamp (D7); expanded → changed-fields diff (D3), reason
// line when present, Acknowledge (D2 — hidden on the caller's own flagged row; the server's 409
// stays as the authoritative backstop with an inline error) + "Full history for this record"
// (D8).
@Composable
internal fun AuditLogEntryRow(
    entry: AuditLogEntryResponse,
    tableLabel: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    currentUserId: String?,
    onAcknowledge: () -> Unit,
    acknowledging: Boolean,
    ackError: String?,
    onFullHistory: () -> Unit,
    showAcknowledge: Boolean = true,
    showFullHistory: Boolean = true,
    // #390 — "Open client record" jump; non-null only when the caller holds the backend's
    // client-read scope (GLOBAL EDIT_BRANCH_DATA) AND this row targets a client record.
    onOpenClientRecord: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val canAcknowledge = canAcknowledgeEntry(showAcknowledge, entry, currentUserId)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .rowHover()
                .padding(vertical = Spacing.xs),
    ) {
        AuditLogEntryHeader(entry = entry, tableLabel = tableLabel, expanded = expanded)

        // #383 — branch context is always visible on the collapsed row: the human branch name
        // when the row has one, an explicit marker otherwise (never a raw UUID or blank).
        Text(
            text = auditBranchDisplayName(entry.branchName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )

        if (expanded) {
            AuditLogExpandedDetails(
                entry = entry,
                canAcknowledge = canAcknowledge,
                acknowledging = acknowledging,
                onAcknowledge = onAcknowledge,
                showFullHistory = showFullHistory,
                onFullHistory = onFullHistory,
                onOpenClientRecord = onOpenClientRecord,
                ackError = ackError,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun AuditLogEntryActions(
    entry: AuditLogEntryResponse,
    canAcknowledge: Boolean,
    acknowledging: Boolean,
    onAcknowledge: () -> Unit,
    showFullHistory: Boolean,
    onFullHistory: () -> Unit,
    onOpenClientRecord: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (canAcknowledge) {
            TextButton(
                onClick = onAcknowledge,
                enabled = !acknowledging,
            ) {
                Text(if (acknowledging) "Acknowledging…" else "Acknowledge")
            }
        }
        if (showFullHistory) {
            TextButton(onClick = onFullHistory) {
                Text("Full history for this record")
            }
        }
        if (onOpenClientRecord != null && canOpenClientRecord(entry)) {
            TextButton(onClick = onOpenClientRecord) {
                Text("Open client record")
            }
        }
    }
}

// D2 — the Acknowledge affordance: hidden on the caller's own flagged rows (self-ack is
// server-409'd), and on already-acknowledged rows (the backend leaves `isFlagged` true and sets
// `acknowledgedAt` — offering Acknowledge there would 404 on tap for other reviewers / fresh
// VMs). Extracted pure so the rule is test-pinned.
internal fun canAcknowledgeEntry(
    showAcknowledge: Boolean,
    entry: AuditLogEntryResponse,
    currentUserId: String?,
): Boolean = showAcknowledge && entry.isFlagged && entry.acknowledgedAt == null && entry.changedBy != currentUserId

// #390 — the client-record jump target: client audit rows carry tableName = "client" (backend
// ClientTable.tableName) with recordId = client.id (ClientAudit.inserted/updated), so
// Route.ClientDetail resolves directly. Extracted pure so the rule is test-pinned like
// [canAcknowledgeEntry]. The caller-side GLOBAL EDIT_BRANCH_DATA gate lives at the NavHost
// call sites; this predicate only answers "is this row a client record?".
internal const val AUDIT_TABLE_CLIENT = "client"

internal fun canOpenClientRecord(entry: AuditLogEntryResponse): Boolean = entry.tableName == AUDIT_TABLE_CLIENT

@Composable
private fun ActionPill(action: AuditAction) {
    val (background, content) =
        when (action) {
            AuditAction.INSERT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            AuditAction.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = background,
    ) {
        Text(
            text = action.name,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun FlagBadge() {
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "flagged",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

// D3 — the backend stores only changed fields (`old_value`/`new_value` JSONB), so the diff
// renders exactly that: UPDATE = `field: old → new`; INSERT = added fields only; DELETE =
// removed fields (+ the reason line rendered by the row from `entry.reason`).
@Composable
private fun ChangedFieldsList(
    fields: List<ChangedField>,
    action: AuditAction,
) {
    fields.forEach { field ->
        Text(
            text =
                buildString {
                    append(field.field)
                    append(": ")
                    when (action) {
                        AuditAction.INSERT -> {
                            append(field.new ?: "—")
                        }

                        AuditAction.DELETE -> {
                            append(field.old ?: "—")
                        }

                        else -> {
                            append(field.old ?: "—")
                            append(" → ")
                            append(field.new ?: "—")
                        }
                    }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// D3 — a changed-field line. `old`/`new` are display-ready (the "null" sentinel the backend
// writes for absent values renders as "—"); either side may be null (INSERT/DELETE shapes).
internal data class ChangedField(
    val field: String,
    val old: String?,
    val new: String?,
)

@Suppress("ReturnCount") // guard-clause style: malformed-data and empty-diff exits are distinct answers
internal fun parseChangedFields(
    oldValue: String?,
    newValue: String?,
): List<ChangedField> = parseDiff(oldValue, newValue).first

// D3 — distinguishes a genuinely empty diff (both sides absent — the "No field changes recorded"
// line) from a present-but-unparseable side (server-data corruption — the row renders nothing).
internal fun diffHasMalformedSide(
    oldValue: String?,
    newValue: String?,
): Boolean = parseDiff(oldValue, newValue).second

// Parses both diff sides ONCE (single JSON parse + single corruption logWarn per side) and
// returns the renderable fields + whether either side was malformed (the row renders nothing on
// the latter; "No field changes recorded" only when both sides are genuinely absent/empty).
internal fun parseDiff(
    oldValue: String?,
    newValue: String?,
): Pair<List<ChangedField>, Boolean> {
    val old = parseFieldMap(oldValue)
    val new = parseFieldMap(newValue)
    // A present-but-unparseable side is server-data corruption — render nothing rather than a
    // partial diff from the healthy side.
    if (old is FieldMap.Malformed || new is FieldMap.Malformed) return emptyList<ChangedField>() to true
    val oldFields = (old as? FieldMap.Valid)?.fields
    val newFields = (new as? FieldMap.Valid)?.fields
    if (oldFields == null && newFields == null) return emptyList<ChangedField>() to false
    val keys = (oldFields?.keys ?: emptySet()) + (newFields?.keys ?: emptySet())
    return keys.sorted().map { key ->
        ChangedField(
            field = key,
            // A side that is absent entirely (INSERT/DELETE shape) stays null; a key missing on
            // one side of an UPDATE renders "—" for that side.
            old = oldFields?.let { it[key]?.toDisplayValue() ?: "—" },
            new = newFields?.let { it[key]?.toDisplayValue() ?: "—" },
        )
    } to false
}

private sealed interface FieldMap {
    data object Absent : FieldMap

    data object Malformed : FieldMap

    data class Valid(
        val fields: Map<String, String>,
    ) : FieldMap
}

@Suppress("ReturnCount") // guard-clause style: absent/malformed/valid are three terminal answers
private fun parseFieldMap(raw: String?): FieldMap {
    if (raw == null) return FieldMap.Absent
    val element =
        runCatching { Json.parseToJsonElement(raw) }
            .getOrElse {
                logWarn("AuditLogScreen", "unparseable audit diff JSON: $raw")
                return FieldMap.Malformed
            }
    if (element !is JsonObject) return FieldMap.Malformed
    return FieldMap.Valid(
        element.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() },
    )
}

private fun String.toDisplayValue(): String = if (this == "null") "—" else this

// #383 — rows without branch context (global rows: products, clients, users, credentials)
// render an explicit marker — never a raw UUID or blank.
internal const val AUDIT_NO_BRANCH_MARKER = "No branch"

internal fun auditBranchDisplayName(branchName: String?): String =
    branchName?.takeIf { it.isNotBlank() } ?: AUDIT_NO_BRANCH_MARKER

internal const val AUDIT_DATE_FORMAT_ERROR = "Dates must be yyyy-MM-dd"

internal const val AUDIT_DATE_ORDER_ERROR = "From must be before To"

// #383 — validate-on-apply, surfaced per field on the inputs themselves. Blank = no filter
// (absent); the backend stays authoritative for real calendar validity (a lexically valid
// non-existent date 400s into the error card). The ordering violation lands on To — From is
// where the range starts.
internal fun auditDateRangeErrors(
    fromRaw: String,
    toRaw: String,
): Pair<String?, String?> {
    val from = fromRaw.trim().takeIf { it.isNotEmpty() }
    val to = toRaw.trim().takeIf { it.isNotEmpty() }
    val fromError = if (from != null && !DATE_PATTERN.matches(from)) AUDIT_DATE_FORMAT_ERROR else null
    val toError =
        when {
            to != null && !DATE_PATTERN.matches(to) -> AUDIT_DATE_FORMAT_ERROR
            fromError == null && from != null && to != null && from > to -> AUDIT_DATE_ORDER_ERROR
            else -> null
        }
    return fromError to toError
}

// D11 — the shared row-affordance bundle across the list actuals and all three list call sites
// (For-review, All-activity, history). A holder keeps the expect/actual signatures under
// detekt's LongParameterList threshold (6) and dissolves the repeated 10-param clump (pass-6
// HARD — the bare 11-param actuals failed :composeApp:detekt*).
internal data class AuditLogEntryListArgs(
    val entries: List<AuditLogEntryResponse>,
    val tableLabels: Map<String, String>,
    val expandedIds: Set<String>,
    val onToggleExpanded: (String) -> Unit,
    val currentUserId: String?,
    val onAcknowledge: (AuditLogEntryResponse) -> Unit,
    val acknowledgingIds: Set<String>,
    val ackErrors: Map<String, String>,
    val onFullHistory: (AuditLogEntryResponse) -> Unit,
    val showAcknowledge: Boolean,
    val showFullHistory: Boolean,
    // #390 — non-null only when the caller holds GLOBAL EDIT_BRANCH_DATA; the list actuals
    // additionally filter per-row via canOpenClientRecord. Default null keeps the D8 history
    // screen drill-down-free without touching its call site.
    val onOpenClientRecord: ((AuditLogEntryResponse) -> Unit)? = null,
)

// D11 — desktop dense rows / mobile cards (#95 smallest-divergent-subtree); the row itself is
// the shared [AuditLogEntryRow]. The modifier lets the All-activity tab weight the list so the
// pinned Load-more affordances below it stay visible (D5; pass-7 HARD — the pre-fix
// fillMaxSize consumed the weighted column's full height and clipped the button).
@Composable
internal fun MobileAuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(args.entries, key = { it.id }) { entry ->
            // #390 — resolve per-row: affordance renders only for client-table rows when the
            // caller holds the backend's client-read scope.
            val onOpenRecord = args.onOpenClientRecord
            val openClientRecord =
                if (onOpenRecord != null && canOpenClientRecord(entry)) {
                    { onOpenRecord(entry) }
                } else {
                    null
                }
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuditLogEntryRow(
                    entry = entry,
                    tableLabel = args.tableLabels[entry.tableName] ?: entry.tableName,
                    expanded = entry.id in args.expandedIds,
                    onToggleExpanded = { args.onToggleExpanded(entry.id) },
                    currentUserId = args.currentUserId,
                    onAcknowledge = { args.onAcknowledge(entry) },
                    acknowledging = entry.id in args.acknowledgingIds,
                    ackError = args.ackErrors[entry.id],
                    onFullHistory = { args.onFullHistory(entry) },
                    onOpenClientRecord = openClientRecord,
                    showAcknowledge = args.showAcknowledge,
                    showFullHistory = args.showFullHistory,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}

@Composable
internal expect fun AuditLogEntryList(
    args: AuditLogEntryListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
)

@Composable
private fun AuditLogHistoryLoadEffect(
    viewModel: AuditLogViewModel,
    tableName: String,
    recordId: String,
) {
    LaunchedEffect(Unit) {
        logInfo("AuditLogHistoryScreen", "composable entered (first composition)")
        // Load once per VM lifetime (Idle), re-fire from an error state (auto-retry — the
        // same policy as the main screen's loads); a rotation/re-entry refire must not wipe
        // the loaded history back to a spinner (pass-5 SOFT).
        if (viewModel.history.value is UiState.Idle || viewModel.history.value is UiState.Error) {
            viewModel.loadHistory(tableName, recordId)
        }
    }
}

@Composable
private fun AuditLogHistoryHeader(
    onBack: () -> Unit,
    tableName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }
        Text(
            text = "History — $tableName",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

// D8 — "Full history for this record": pushed on both platforms (#91 push-route lock; ClientDetail
// precedent — content-level Back TextButton, the pushed-route topbar pattern stays fog). The route
// gets its own entry-scoped VM (fresh entry self-cleans, #112 pattern).
@Composable
fun AuditLogHistoryScreen(
    viewModel: AuditLogViewModel,
    tableName: String,
    recordId: String,
    currentUserId: String?,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsState()
    val acknowledgingIds by viewModel.acknowledgingIds.collectAsState()
    val ackErrors by viewModel.ackErrors.collectAsState()
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    AuditLogHistoryLoadEffect(viewModel, tableName, recordId)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        AuditLogHistoryHeader(onBack = onBack, tableName = tableName)
        when (val state = history) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                logWarn("AuditLogHistoryScreen", "historyState=Error: ${state.message}")
                ErrorCard(
                    message = state.message,
                    onRetry = { viewModel.loadHistory(tableName, recordId) },
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState("No history recorded for this record")
                } else {
                    AuditLogEntryList(
                        args =
                            AuditLogEntryListArgs(
                                entries = state.data,
                                tableLabels = emptyMap(),
                                expandedIds = expandedIds,
                                onToggleExpanded = { id ->
                                    expandedIds =
                                        if (id in expandedIds) expandedIds - id else expandedIds + id
                                },
                                currentUserId = currentUserId,
                                onAcknowledge = viewModel::acknowledge,
                                acknowledgingIds = acknowledgingIds,
                                ackErrors = ackErrors,
                                onFullHistory = {},
                                // D8 — the per-record screen is the record's trail + breadcrumb back
                                // only: no acknowledge affordance, no further drill-down (prototype D8).
                                showAcknowledge = false,
                                showFullHistory = false,
                            ),
                    )
                }
            }
        }
    }
}

private const val TAB_FOR_REVIEW = 0
private const val TAB_ALL_ACTIVITY = 1

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

// #116 precedent: backend enums serialize as name strings; the frontend mirrors the vocabulary
// in one place so the dropdown, pill colors, and diff rendering share it (D4's "never hardcode
// labels" targets table labels; the action list is the backend enum contract).
private val AUDIT_ACTIONS = AuditAction.entries.map { it.name }

private const val EXPANDED_CHEVRON_ROTATION = 90f
