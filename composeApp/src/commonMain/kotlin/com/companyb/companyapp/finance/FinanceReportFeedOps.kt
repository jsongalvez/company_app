package com.companyb.companyapp.finance

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

// #479 — extracted from FinanceReportsViewModel so the file-function wall (TMF) stays
// honest. Extension functions on the ViewModel; FinanceReportsScreen method references and
// FinanceReportsViewModelTest (same package) resolve them unchanged.

internal fun FinanceReportsViewModel.refreshFeed() {
    if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) return
    val branchId = selectedBranchIdState.value ?: return
    if (isRefreshingState.value || isLoadingMoreState.value) return
    // A refresh replaces the day rows — a stale selection would keep showing the
    // pre-refresh figures (pass-9 SOFT).
    selectedDayState.value = null
    editModeState.value = false
    refreshErrorState.value = null
    loadMoreErrorState.value = null
    fetchPage(FeedFetchMode.Refresh, cursor = null, branchId = branchId)
}

internal fun FinanceReportsViewModel.loadMore() {
    val branchId = selectedBranchIdState.value ?: return
    val cursor = nextCursorState.value ?: return
    if (isLoadingMoreState.value || isRefreshingState.value) return
    loadMoreErrorState.value = null
    fetchPage(FeedFetchMode.LoadMore, cursor = cursor, branchId = branchId)
}

internal fun FinanceReportsViewModel.retryFeed() {
    if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) return
    val branchId = selectedBranchIdState.value ?: return
    feedEntriesState.value = UiState.Loading
    fetchPage(FeedFetchMode.Cold, cursor = null, branchId = branchId)
}

internal fun FinanceReportsViewModel.refreshWindowAndFeed() {
    feedGeneration++
    isRefreshingState.value = false
    isLoadingMoreState.value = false
    nextCursorState.value = null
    refreshErrorState.value = null
    loadMoreErrorState.value = null
    selectedDayState.value = null
    editModeState.value = false
    // Pass-3 HARD — a mode/window switch while editing must not leave the OLD day's
    // section data armed: re-entering edit on another day would render the old rows
    // during the load window and Edit/Delete would mutate the wrong day's records.
    clearEditData()
    val branchId = selectedBranchIdState.value ?: return
    // Pass-5 SOFT — DATE_RANGE has no window before Apply: no unbounded all-time fetch
    // fires behind the hint (the hint branch renders instead of the feed).
    if (modeState.value == ReportMode.DATE_RANGE && appliedRangeState.value == null) {
        feedEntriesState.value = UiState.Idle
        return
    }
    feedEntriesState.value = UiState.Loading
    fetchPage(FeedFetchMode.Cold, cursor = null, branchId = branchId)
}

internal fun FinanceReportsViewModel.currentWindow(): FeedWindow =
    feedWindowFor(
        FeedWindowRequest(
            mode = modeState.value,
            today = today,
            month = appliedMonthState.value,
            rangeFrom = appliedRangeState.value?.first,
            rangeTo = appliedRangeState.value?.second,
            jumpMonth = jumpMonthState.value,
        ),
    )

internal fun FinanceReportsViewModel.fetchPage(
    mode: FeedFetchMode,
    cursor: String?,
    branchId: String,
) {
    val generation = feedGeneration
    if (mode == FeedFetchMode.Refresh) isRefreshingState.value = true
    if (mode == FeedFetchMode.LoadMore) isLoadingMoreState.value = true
    handler.launchStatelessGuarded(
        operation = mode.operationName,
        endpoint = "GET /api/branches/$branchId/daily-summaries",
        block = {
            apiClient.httpClient.get(ApiRoutes.branchDailySummaries(branchId)) {
                cursor?.let { parameter("cursor", it) }
                parameter("limit", FinanceReportsViewModel.FEED_PAGE_SIZE)
                currentWindow().from?.let { parameter("from", it) }
                currentWindow().to?.let { parameter("to", it) }
            }
        },
        guarded =
            GuardedStateless(
                // #528 — suspend decode owns the parse; the non-suspending commit below owns
                // every list/cursor/flag write. A generation bump mid-decode drops the body.
                decode = { it.body<DailySalesSummaryBrowseResponse>() },
                commit = { page ->
                    val listAlreadyCommitted =
                        mode == FeedFetchMode.Cold && feedEntriesState.value is UiState.Success
                    if (listAlreadyCommitted) {
                        logWarn("FinanceVM", "cold feed success suppressed — feed superseded")
                    } else {
                        refreshErrorState.value = null
                        nextCursorState.value = page.nextCursor
                        applyPage(mode, page.entries)
                    }
                    finish(mode)
                },
// #173 — the hand-rolled generation guard folds into the guarded stale gate: a
                // superseded landing (window/branch/mode switched while this page was in flight)
                // is inert — no list/cursor/error/flags writes, no finish.
                stale = { generation != feedGeneration },
                onNonSuccess = { response ->
                    handlePageFailure(mode, "feed failed: ${response.status.value}")
                    finish(mode)
                },
                onError = { e ->
                    // Network or deserialization failure — same error surface + flag cleanup so the
                    // list state and buttons never freeze (keep-last-list); gated by stale — a
                    // superseded fetch's failure must not surface on the new list. onError is that
                    // single surface (#169).
                    handlePageFailure(mode, "feed failed: ${e.message ?: "network error"}")
                    finish(mode)
                },
            ),
    )
}

internal fun FinanceReportsViewModel.applyPage(
    mode: FeedFetchMode,
    entries: List<DailySalesSummaryResponse>,
) {
    when (mode) {
        FeedFetchMode.Cold, FeedFetchMode.Refresh -> {
            feedEntriesState.value = UiState.Success(entries)
        }

        FeedFetchMode.LoadMore -> {
            val current =
                (feedEntriesState.value as? UiState.Success<List<DailySalesSummaryResponse>>)
                    ?.data
                    .orEmpty()
            feedEntriesState.value = UiState.Success(current + entries)
        }
    }
}

internal fun FinanceReportsViewModel.handlePageFailure(
    mode: FeedFetchMode,
    message: String,
) {
    when (mode) {
        FeedFetchMode.Cold -> {
            if (feedEntriesState.value !is UiState.Success) {
                feedEntriesState.value = UiState.Error(message)
            } else {
                logWarn("FinanceVM", "cold feed failure suppressed — feed superseded: $message")
            }
        }

        FeedFetchMode.Refresh -> {
            refreshErrorState.value = message
        }

        FeedFetchMode.LoadMore -> {
            loadMoreErrorState.value = message
        }
    }
}

internal fun FinanceReportsViewModel.finish(mode: FeedFetchMode) {
    if (mode == FeedFetchMode.Refresh) isRefreshingState.value = false
    if (mode == FeedFetchMode.LoadMore) isLoadingMoreState.value = false
}

// ─────────────────────────── monthly rollup ───────────────────────────

internal fun FinanceReportsViewModel.loadMonthlyRollup() {
    val branchId = selectedBranchIdState.value ?: return
    val month = appliedMonthState.value
    rollupGeneration++
    val generation = rollupGeneration
    monthlyRollupState.value = UiState.Loading
    handler.launchStatelessGuarded(
        operation = "loadMonthlyRollup",
        endpoint = "GET /api/branches/$branchId/monthly-summary",
        block = {
            apiClient.httpClient.get(ApiRoutes.branchMonthlySummary(branchId)) {
                parameter("year", month.year)
                parameter("month", month.month.ordinal + 1)
            }
        },
        guarded =
            GuardedStateless(
                // #528 — decode/commit split: a branch/month switch mid-decode drops the body.
                decode = { it.body<MonthlyRemittanceSummaryResponse>() },
                commit = {
                    // 404 (no remittance submitted that month — the #105 F5 shape) is NOT an
                    // error: the rollup card simply doesn't render. Transform only sees 2xx —
                    // the 404 branch lives in onNonSuccess below.
                    monthlyRollupState.value = UiState.Success(it)
                },
// #173 — the generation guard folds into the stale gate (a superseded rollup —
                // branch/month switched — must not write any state; a DAILY-mode switch doesn't
                // bump the generation — the pre-existing #170 accepted SOFT, benign while DAILY).
                stale = { generation != rollupGeneration },
                onNonSuccess = { response ->
                    if (response.status == HttpStatusCode.NotFound) {
                        monthlyRollupState.value = UiState.Success(null)
                    } else {
                        monthlyRollupState.value = UiState.Error("monthly rollup failed: ${response.status.value}")
                    }
                },
// #170 — the loadReliefDay shape: a transport failure moves Loading → Error
                // (terminal) instead of parking on Loading forever (the #168/#169 P5 sibling —
                // pre-fix the sole onError-less stateless site parking a UiState on Loading;
                // loadSent is onError-less by keep-last design — no state to park).
                onError = { e ->
                    monthlyRollupState.value = UiState.Error(e.message ?: "Unknown error")
                },
            ),
    )
}

// ─────────────────────────── selection + edit mode ───────────────────────────

internal enum class FeedFetchMode(
    val operationName: String,
) {
    Cold("loadFeed"),
    Refresh("refreshFeed"),
    LoadMore("loadMore"),
}
