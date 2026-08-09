package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
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
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    currentUserId: String?,
    hasAnyCapability: Boolean,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
) {
    val flaggedEntries by viewModel.flaggedEntries.collectAsState()
    val browseEntries by viewModel.browseEntries.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val acknowledgingIds by viewModel.acknowledgingIds.collectAsState()
    val ackErrors by viewModel.ackErrors.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val nextCursor by viewModel.nextCursor.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadMoreError by viewModel.loadMoreError.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_FOR_REVIEW) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    // D10 — the All-activity list loads on first visit and keeps its accumulated pages across
    // tab switches; For-review reloads on every re-entry (new flags must appear).
    var hasVisitedAllActivity by remember { mutableStateOf(false) }

    val tableLabels =
        (tables as? UiState.Success<List<AuditLogTableResponse>>)
            ?.data
            ?.associate { it.tableName to it.label }
            .orEmpty()

    LaunchedEffect(Unit) {
        logInfo("AuditLogScreen", "composable entered (first composition)")
        viewModel.loadFlaggedEntries()
        viewModel.loadTables()
    }

    // D10 — For-review refreshes on tab re-entry via the SILENT path (refreshFlagged), so the
    // loaded list survives the reload (keep-last-list); a cold first load is the loud path above.
    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_FOR_REVIEW) {
            if (hasVisitedAllActivity) {
                // Not the first composition: this is a re-entry (tab switch back).
                viewModel.refreshFlagged()
            }
        } else if (!hasVisitedAllActivity) {
            hasVisitedAllActivity = true
            viewModel.refreshBrowse()
        }
    }

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
                enabled = !isRefreshing && !isLoadingMore,
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
                refreshError = refreshError,
                currentUserId = currentUserId,
                hasAnyCapability = hasAnyCapability,
                tableLabels = tableLabels,
                expandedIds = expandedIds,
                onToggleExpanded = { id ->
                    expandedIds =
                        if (id in expandedIds) expandedIds - id else expandedIds + id
                },
                acknowledgingIds = acknowledgingIds,
                ackErrors = ackErrors,
                onAcknowledge = viewModel::acknowledge,
                onFullHistory = onFullHistory,
                onRetry = viewModel::loadFlaggedEntries,
            )
        } else {
            AllActivityTab(
                state = browseEntries,
                tables = tables,
                refreshError = refreshError,
                currentUserId = currentUserId,
                hasAnyCapability = hasAnyCapability,
                tableLabels = tableLabels,
                expandedIds = expandedIds,
                onToggleExpanded = { id ->
                    expandedIds =
                        if (id in expandedIds) expandedIds - id else expandedIds + id
                },
                acknowledgingIds = acknowledgingIds,
                ackErrors = ackErrors,
                onAcknowledge = viewModel::acknowledge,
                onFullHistory = onFullHistory,
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
                        entries = state.data,
                        tableLabels = tableLabels,
                        expandedIds = expandedIds,
                        onToggleExpanded = onToggleExpanded,
                        currentUserId = currentUserId,
                        onAcknowledge = onAcknowledge,
                        acknowledgingIds = acknowledgingIds,
                        ackErrors = ackErrors,
                        onFullHistory = onFullHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshErrorLine(refreshError: String?) {
    if (refreshError != null) {
        Text(
            text = refreshError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.xs),
        )
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
    onApplyFilters: (AuditLogFilters) -> Unit,
    onRetryBrowse: () -> Unit,
    onRetryTables: () -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: String?,
) {
    var filtersApplied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        RefreshErrorLine(refreshError)
        AuditLogFilterBar(
            tables = tables,
            onRetryTables = onRetryTables,
            onApply = { filters ->
                filtersApplied = filters != AuditLogFilters()
                onApplyFilters(filters)
            },
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
                ErrorCard(
                    message = state.message,
                    onRetry = onRetryBrowse,
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
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
                            entries = state.data,
                            tableLabels = tableLabels,
                            expandedIds = expandedIds,
                            onToggleExpanded = onToggleExpanded,
                            currentUserId = currentUserId,
                            onAcknowledge = onAcknowledge,
                            acknowledgingIds = acknowledgingIds,
                            ackErrors = ackErrors,
                            onFullHistory = onFullHistory,
                        )
                        if (loadMoreError != null) {
                            Text(
                                text = loadMoreError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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
        }
    }
}

// D4/D8 — filter bar: server-driven table dropdown (loading/error states + retry; never
// hardcode labels), action dropdown, date range from/to (inclusive Manila days, yyyy-MM-dd),
// caller-name text. Validation is light (format + ordering); the backend remains authoritative
// (400 → in-place error card).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogFilterBar(
    tables: UiState<List<AuditLogTableResponse>>,
    onRetryTables: () -> Unit,
    onApply: (AuditLogFilters) -> Unit,
) {
    var selectedTableName by remember { mutableStateOf<String?>(null) }
    var selectedAction by remember { mutableStateOf<String?>(null) }
    var callerName by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf<String?>(null) }

    fun apply() {
        val from = dateFrom.trim().takeIf { it.isNotEmpty() }
        val to = dateTo.trim().takeIf { it.isNotEmpty() }
        val malformed = listOfNotNull(from, to).any { !DATE_PATTERN.matches(it) }
        if (malformed) {
            dateError = "Dates must be yyyy-MM-dd"
            return
        }
        if (from != null && to != null && from > to) {
            dateError = "From must be before To"
            return
        }
        dateError = null
        onApply(
            AuditLogFilters(
                tableName = selectedTableName,
                action = selectedAction,
                callerName = callerName.trim().takeIf { it.isNotBlank() },
                dateFrom = from,
                dateTo = to,
            ),
        )
    }

    fun reset() {
        selectedTableName = null
        selectedAction = null
        callerName = ""
        dateFrom = ""
        dateTo = ""
        dateError = null
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
                selectedTableName = selectedTableName,
                onTableSelected = { selectedTableName = it },
                onRetryTables = onRetryTables,
                modifier = Modifier.weight(1f),
            )
            ActionDropdown(
                selectedAction = selectedAction,
                onActionSelected = { selectedAction = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = dateFrom,
                onValueChange = {
                    dateFrom = it
                    dateError = null
                },
                label = { Text("From") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dateTo,
                onValueChange = {
                    dateTo = it
                    dateError = null
                },
                label = { Text("To") },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = callerName,
                onValueChange = { callerName = it },
                label = { Text("Caller") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
        }
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
            if (dateError != null) {
                Text(
                    text = dateError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
    modifier: Modifier = Modifier,
) {
    val canAcknowledge = showAcknowledge && entry.isFlagged && entry.changedBy != currentUserId

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = Spacing.xs),
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

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
            ) {
                val fields =
                    remember(entry.oldValue, entry.newValue) {
                        parseChangedFields(entry.oldValue, entry.newValue)
                    }
                if (fields.isEmpty()) {
                    Text(
                        text = "No field changes recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ChangedFieldsList(fields = fields, action = entry.action)
                }
                entry.reason?.let { reason ->
                    Text(
                        text = "Reason: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
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
                }
                if (ackError != null) {
                    Text(
                        text = ackError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ActionPill(action: String) {
    val (background, content) =
        when (AuditAction.from(action)) {
            AuditAction.INSERT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            AuditAction.DELETE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = background,
    ) {
        Text(
            text = action,
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
    action: String,
) {
    fields.forEach { field ->
        Text(
            text =
                buildString {
                    append(field.field)
                    append(": ")
                    when (AuditAction.from(action)) {
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

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
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
): List<ChangedField> {
    val old = parseFieldMap(oldValue)
    val new = parseFieldMap(newValue)
    // A present-but-unparseable side is server-data corruption — render nothing rather than a
    // partial diff from the healthy side.
    if (old is FieldMap.Malformed || new is FieldMap.Malformed) return emptyList()
    val oldFields = (old as? FieldMap.Valid)?.fields
    val newFields = (new as? FieldMap.Valid)?.fields
    if (oldFields == null && newFields == null) return emptyList()
    val keys = (oldFields?.keys ?: emptySet()) + (newFields?.keys ?: emptySet())
    return keys.sorted().map { key ->
        ChangedField(
            field = key,
            // A side that is absent entirely (INSERT/DELETE shape) stays null; a key missing on
            // one side of an UPDATE renders "—" for that side.
            old = oldFields?.let { it[key]?.toDisplayValue() ?: "—" },
            new = newFields?.let { it[key]?.toDisplayValue() ?: "—" },
        )
    }
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

// D11 — desktop dense rows / mobile cards (#95 smallest-divergent-subtree); the row itself is
// the shared [AuditLogEntryRow].
@Composable
expect fun AuditLogEntryList(
    entries: List<AuditLogEntryResponse>,
    tableLabels: Map<String, String>,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    currentUserId: String?,
    onAcknowledge: (AuditLogEntryResponse) -> Unit,
    acknowledgingIds: Set<String>,
    ackErrors: Map<String, String>,
    onFullHistory: (AuditLogEntryResponse) -> Unit,
    showAcknowledge: Boolean = true,
    showFullHistory: Boolean = true,
)

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

    LaunchedEffect(Unit) {
        logInfo("AuditLogHistoryScreen", "composable entered (first composition)")
        viewModel.loadHistory(tableName, recordId)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
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
        when (val state = history) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
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
private enum class AuditAction(
    val raw: String,
) {
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    ;

    companion object {
        fun from(raw: String): AuditAction? = entries.firstOrNull { it.raw == raw }
    }
}

private val AUDIT_ACTIONS = AuditAction.entries.map { it.raw }

private const val EXPANDED_CHEVRON_ROTATION = 90f
