package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.companyb.companyapp.ui.screen.peso
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
    Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        FeedSectionHeader(collected = collected)
        FeedSectionStatus(feed = collected.feed, onRetry = viewModel::retryFeed)
        if (collected.feed is UiState.Success) {
            FeedSuccessContent(
                viewModel = viewModel,
                days = collected.feed.data,
                collected = collected,
            )
        }
    }
}

@Composable
private fun FeedSectionHeader(collected: FinanceReportsCollected) {
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
        logWarn("FinanceReportsScreen", "monthlyRollup=Error: ${collected.monthlyRollup.message}")
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
        // #105 D5 — compact day rows by default, toggle to full-day cards.
        var showCards by remember { mutableStateOf(false) }
        FeedSuccessToolbar(viewModel = viewModel, showCards = showCards, onShowCards = { showCards = it })
        val refreshError by viewModel.refreshError.collectAsState()
        val refreshErrorValue = refreshError
        if (refreshErrorValue != null) {
            logWarn("FinanceReportsScreen", "refresh=Error: $refreshErrorValue")
            Text(
                text = refreshErrorValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        FeedSuccessList(
            viewModel = viewModel,
            days = days,
            collected = collected,
            showCards = showCards,
        )
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
        FilterChip(selected = !showCards, onClick = { onShowCards(false) }, label = { Text("Rows") })
        Spacer(Modifier.width(Spacing.xs))
        FilterChip(selected = showCards, onClick = { onShowCards(true) }, label = { Text("Cards") })
        Spacer(Modifier.weight(1f))
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val isLoadingMore by viewModel.isLoadingMore.collectAsState()
        TextButton(onClick = viewModel::refreshFeed, enabled = !isRefreshing && !isLoadingMore) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.width(Spacing.sm).height(Spacing.sm),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ColumnScope.FeedSuccessList(
    viewModel: FinanceReportsViewModel,
    days: List<DailySalesSummaryResponse>,
    collected: FinanceReportsCollected,
    showCards: Boolean,
) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(days, key = { it.branchDayId }) { day ->
            FeedDayItem(
                day = day,
                viewModel = viewModel,
                collected = collected,
                showCards = showCards,
            )
        }
        item(key = "load-more") {
            FeedLoadMoreItem(viewModel = viewModel)
        }
    }
}

@Composable
private fun FeedDayItem(
    day: DailySalesSummaryResponse,
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    showCards: Boolean,
) {
    val isSelected = day.branchDayId == collected.selectedDay?.branchDayId
    val onExportDay: (String) -> Unit = { format ->
        collected.selectedBranchId?.let { branch -> viewModel.exportDay(day, branch, format) }
    }
    val export =
        DayExport(
            onExportDay = onExportDay,
            downloadStates = collected.downloads,
            exportErrors = collected.exportErrors,
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
            selected = isSelected,
            onSelect = onSelect,
            export = export,
        )
    }
}

@Composable
private fun ColumnScope.FeedLoadMoreItem(viewModel: FinanceReportsViewModel) {
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
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(),
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
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Month rollup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Gross ${peso(rollup.grossIncome)}", style = MaterialTheme.typography.bodySmall)
            Text("Comp ${peso(rollup.totalCompensation)}", style = MaterialTheme.typography.bodySmall)
            Text("Exp ${peso(rollup.totalExpenses)}", style = MaterialTheme.typography.bodySmall)
            Text("Net ${peso(rollup.netIncome)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
