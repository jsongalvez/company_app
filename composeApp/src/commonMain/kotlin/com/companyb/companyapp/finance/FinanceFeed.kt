package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import com.companyb.companyapp.contracts.reporting.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn

@Composable
internal fun FeedSection(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    modifier: Modifier = Modifier,
) {
    // #678 — the list state + Rows/Cards choice hoist above the status branch, so Back
    // from a detail and Cold reloads restore the period list, scroll position and view.
    // (Rotation still resets them — no rememberSaveable anywhere on this surface.)
    var showCards by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        FeedSectionHeader(viewModel = viewModel, collected = collected)
        FeedSectionStatus(feed = collected.feed, onRetry = viewModel::retryFeed)
        if (collected.feed is UiState.Success) {
            FeedSuccessContent(
                viewModel = viewModel,
                days = collected.feed.data,
                collected = collected,
                chrome =
                    FeedListChrome(
                        showCards = showCards,
                        onShowCards = { showCards = it },
                        listState = listState,
                    ),
            )
        }
    }
}

@Composable
private fun FeedSectionHeader(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
) {
    if (collected.mode == ReportMode.DATE_RANGE && collected.appliedRange == null) {
        // #105 D4 — the DATE_RANGE feed is window-scoped; before a window exists there is
        // no feed (the unbounded all-time view would mislead — pass-4).
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Apply a From/To window to browse these days",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }
    }
    if (collected.mode == ReportMode.MONTHLY &&
        collected.monthlyRollup is UiState.Success &&
        collected.monthlyRollup.data != null
    ) {
        MonthlyRollupCard(rollup = collected.monthlyRollup.data)
    }
    if (collected.mode == ReportMode.MONTHLY && collected.monthlyRollup is UiState.Error) {
        // #678 — an independently failed breakdown stays visibly unavailable with a retry
        // while the day feed below stays usable.
        logWarn("FinanceReportsScreen", "monthlyRollup=Error: ${collected.monthlyRollup.message}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Month rollup unavailable: ${collected.monthlyRollup.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = viewModel::loadMonthlyRollup) { Text("Retry") }
        }
    }
}

@Composable
private fun FeedSectionStatus(
    feed: UiState<List<DailySalesSummaryResponse>>,
    onRetry: () -> Unit,
) {
    when (feed) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "feed=Error: ${feed.message}")
            ErrorCard(message = feed.message, onRetry = onRetry)
        }

        is UiState.Success -> {}
    }
}

@Composable
private fun ColumnScope.FeedSuccessContent(
    viewModel: FinanceReportsViewModel,
    days: List<DailySalesSummaryResponse>,
    collected: FinanceReportsCollected,
    chrome: FeedListChrome,
) {
    if (days.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text =
                    "No data for this branch" +
                        if (collected.mode == ReportMode.DATE_RANGE) " and date range" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }
    } else {
        // #105 D5 — compact day rows by default; Cards sits behind the View menu (#678),
        // never as a competing main task.
        FeedSuccessToolbar(
            viewModel = viewModel,
            showCards = chrome.showCards,
            onShowCards = chrome.onShowCards,
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // #678 — at >= 1000dp the report list keeps >= 480dp beside a >= 480dp
            // selected-day region (even weight split clears both floors past the
            // breakpoint); below it a selection opens the existing full-width inline /
            // dialog detail and collapsing it restores the list.
            val sideBySide = FinanceLayoutPolicy.showSideDetail(maxWidth)
            val selected = collected.selectedDay
            if (sideBySide && selected != null) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        FeedDayList(
                            viewModel = viewModel,
                            days = days,
                            collected = collected,
                            display = FeedListDisplay(showCards = chrome.showCards, sideBySide = true),
                            listState = chrome.listState,
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        SelectedDayRegion(
                            viewModel = viewModel,
                            collected = collected,
                            selected = selected,
                        )
                    }
                }
            } else {
                FeedDayList(
                    viewModel = viewModel,
                    days = days,
                    collected = collected,
                    display = FeedListDisplay(showCards = chrome.showCards, sideBySide = false),
                    listState = chrome.listState,
                )
            }
        }
    }
}

@Composable
private fun FeedSuccessToolbar(
    viewModel: FinanceReportsViewModel,
    showCards: Boolean,
    onShowCards: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // #678 — Cards sits behind the View menu; Rows stays the default overview.
        FeedViewMenu(showCards = showCards, onShowCards = onShowCards)
        Spacer(Modifier.weight(1f))
    }
    // The header owns Refresh (#678 stable header); its failure surfaces here above the
    // retained list with an explicit retry — the report underneath is never replaced.
    val refreshError by viewModel.refreshError.collectAsState()
    val refreshErrorValue = refreshError
    if (refreshErrorValue != null) {
        logWarn("FinanceReportsScreen", "refresh=Error: $refreshErrorValue")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = refreshErrorValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = viewModel::refreshFeed) { Text("Retry") }
        }
    }
}

@Composable
private fun FeedViewMenu(
    showCards: Boolean,
    onShowCards: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(if (showCards) "View: Cards" else "View: Rows")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rows") },
                onClick = {
                    open = false
                    onShowCards(false)
                },
            )
            DropdownMenuItem(
                text = { Text("Cards") },
                onClick = {
                    open = false
                    onShowCards(true)
                },
            )
        }
    }
}

/** #678 LPL burn — the day-list display flags as one object (Rows/Cards + adaptive). */
internal data class FeedListDisplay(
    val showCards: Boolean,
    val sideBySide: Boolean,
)

/** #678 LPL burn — the hoisted day-list chrome: view choice, its writer, and scroll state. */
internal data class FeedListChrome(
    val showCards: Boolean,
    val onShowCards: (Boolean) -> Unit,
    val listState: LazyListState,
)

@Composable
private fun FeedDayList(
    viewModel: FinanceReportsViewModel,
    days: List<DailySalesSummaryResponse>,
    collected: FinanceReportsCollected,
    display: FeedListDisplay,
    listState: LazyListState,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(days, key = { it.branchDayId }) { day ->
            FeedDayItem(
                day = day,
                viewModel = viewModel,
                collected = collected,
                showCards = display.showCards,
                sideBySide = display.sideBySide,
            )
        }
        item(key = "load-more") {
            FeedLoadMoreItem(viewModel = viewModel)
        }
    }
}

/** #678 — the wide selected-day region beside the report list (kept whole so the feed body stays short). */
@Composable
private fun SelectedDayRegion(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    selected: DailySalesSummaryResponse,
) {
    val canEditSelected =
        dayEditAllowed(
            collected.capabilities,
            collected.selectedBranchId,
            selected,
            collected.today,
        )
    SelectedDayPane(
        ui =
            SelectedDayPaneUi(
                day = selected,
                branchName = selectedBranchName(collected),
                today = collected.today,
                edit =
                    if (canEditSelected) {
                        DayEditAction(true) { viewModel.setEditMode(true) }
                    } else {
                        null
                    },
                export = selectedDayExport(viewModel, collected, selected),
                // Wide selections collapse back to the full report list.
                onBack = { viewModel.selectDay(null) },
            ),
    )
}

private fun selectedBranchName(collected: FinanceReportsCollected): String =
    (collected.branches as? UiState.Success)?.data.orEmpty().branchName(collected.selectedBranchId)

private fun selectedDayExport(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    selected: DailySalesSummaryResponse,
): DayExport {
    val branchId = collected.selectedBranchId
    return DayExport(
        onExportDay = { format ->
            if (branchId != null) viewModel.exportDay(selected, branchId, format)
        },
        downloadStates = collected.downloads,
        exportErrors = collected.exportErrors,
    )
}

@Composable
private fun FeedDayItem(
    day: DailySalesSummaryResponse,
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    showCards: Boolean,
    sideBySide: Boolean,
) {
    val isSelected = day.branchDayId == collected.selectedDay?.branchDayId
    val onExportDay: (String) -> Unit = { format ->
        collected.selectedBranchId?.let { branch -> viewModel.exportDay(day, branch, format) }
    }
    // #678 — the Edit-day entry names this day's branch/date/state beside the action (in
    // the pane header wide, in the inline/dialog detail compact); entering edit keeps the
    // selection so the editor opens on this day. The closure arms only when allowed —
    // no read-only row carries a hidden edit entry.
    val canEditDay = dayEditAllowed(collected.capabilities, collected.selectedBranchId, day, collected.today)
    val export =
        DayExport(
            onExportDay = onExportDay,
            downloadStates = collected.downloads,
            exportErrors = collected.exportErrors,
            branchName = selectedBranchName(collected),
            onEditDay =
                if (canEditDay) {
                    {
                        viewModel.selectDay(day)
                        viewModel.setEditMode(true)
                    }
                } else {
                    null
                },
            canEditDay = canEditDay,
        )
    // Pass-2 HARD — re-tapping the selected day deselects (selectDay with the SAME
    // instance never re-emits — the mobile dialog was unclosable). selectDay(null)
    // emits on every call.
    val onSelect = { viewModel.selectDay(if (isSelected) null else day) }
    if (showCards) {
        FinanceDayCard(
            day = day,
            today = collected.today,
            onSelect = onSelect,
            export = export,
        )
    } else {
        DayRow(
            day = day,
            today = collected.today,
            // #678 — wide selections render once, in the side pane; the row stays collapsed.
            selected = isSelected && !sideBySide,
            onSelect = onSelect,
            export = export,
        )
    }
}

@Composable
private fun FeedLoadMoreItem(viewModel: FinanceReportsViewModel) {
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadMoreError by viewModel.loadMoreError.collectAsState()
    val loadMoreErrorValue = loadMoreError
    if (loadMoreErrorValue != null) {
        logWarn("FinanceReportsScreen", "loadMore=Error: $loadMoreErrorValue")
        Text(
            text = loadMoreErrorValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
    val nextCursor by viewModel.nextCursor.collectAsState()
    if (nextCursor != null || isLoadingMore) {
        TextButton(
            onClick = viewModel::loadMore,
            enabled = !isLoadingMore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier.width(Spacing.md).height(Spacing.md),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Load more")
            }
        }
    }
}

@Composable
private fun MonthlyRollupCard(rollup: MonthlyRemittanceSummaryResponse) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = "Month rollup",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Gross ${financeAmount(rollup.grossIncome)}", style = MaterialTheme.typography.bodySmall)
                Text("Comp ${financeAmount(rollup.totalCompensation)}", style = MaterialTheme.typography.bodySmall)
                Text("Exp ${financeAmount(rollup.totalExpenses)}", style = MaterialTheme.typography.bodySmall)
                Text("Net ${financeAmount(rollup.netIncome)}", style = MaterialTheme.typography.bodySmall)
            }
            // #678 — month/all-time aggregates never pretend to be editable days.
            Text(
                text = "Aggregate totals — select a day below to edit",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}
