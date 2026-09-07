package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

// #479 — the All-activity browse seam (#104 D5/D8/D10), extracted from AuditLogViewModel so
// the file-function wall (TMF) stays honest: cursor pagination with the #173 generation gate,
// keep-last-list across silent refresh/load-more, and the pass-5/pass-6 same-generation
// commit/failure guards. Extension functions on the ViewModel (plus the internal FetchMode
// they dispatch on) — AuditLogScreen call sites resolve identically with imports added;
// AuditLogViewModelTest shares the package and needs none.

// D8 — apply filter bar values: fresh page-1 load, previous pages discarded. Unguarded by
// design — an in-flight page fetch from an older filter generation is made inert by the
// generation guard (it can't append to, replace, or error the new list).
internal fun AuditLogViewModel.applyFilters(filters: AuditLogFilters) {
    browseGeneration++
    isRefreshingState.value = false
    isLoadingMoreState.value = false
    appliedFiltersState.value = filters
    nextCursorState.value = null
    browseRefreshErrorState.value = null
    loadMoreErrorState.value = null
    browseEntriesState.value = UiState.Loading
    fetchPage(FetchMode.Cold, cursor = null)
}

// D10 — All-activity first-visit load (cold loud path: Loading → error card + retry).
internal fun AuditLogViewModel.loadBrowse() {
    applyFilters(appliedFiltersState.value)
}

// D10 — manual refresh: silent page-1 re-fetch; the accumulated list stays rendered while
// in-flight and on failure (keep-last-list, #97 Q5 axis). The cursor is NOT pre-nulled: a
// failed refresh keeps the old list AND its Load-more availability (D5); the success path
// replaces both from the fresh page.
internal fun AuditLogViewModel.refreshBrowse() {
    if (isRefreshingState.value || isLoadingMoreState.value) return
    browseRefreshErrorState.value = null
    loadMoreErrorState.value = null
    fetchPage(FetchMode.Refresh, cursor = null)
}

// D10 — error-card retry: re-fires the last applied filters as a cold load (the list state
// holds no content, so the error card must give way to a fresh Loading + fetch).
internal fun AuditLogViewModel.retryBrowse() = loadBrowse()

// D5 — cursor-based pagination: appends the next page; `nextCursor` null = last page.
internal fun AuditLogViewModel.loadMore() {
    val cursor = nextCursorState.value ?: return
    if (isLoadingMoreState.value || isRefreshingState.value) return
    loadMoreErrorState.value = null
    fetchPage(FetchMode.LoadMore, cursor = cursor)
}

// Any failure type must clear the in-flight flags before the failure is surfaced.
internal fun AuditLogViewModel.fetchPage(
    mode: FetchMode,
    cursor: String?,
) {
    val filters = appliedFiltersState.value
    val generation = browseGeneration
    if (mode == FetchMode.Refresh) isRefreshingState.value = true
    if (mode == FetchMode.LoadMore) isLoadingMoreState.value = true
    handler.launchStatelessGuarded(
        // State-less (#168): the list state is mutated in commit, so a failed or
        // in-flight page fetch can never clobber the accumulated list (D10).
        operation = mode.operationName,
        endpoint = "GET /api/audit-log/entries",
        block = { browseRequest(filters, cursor) },
        guarded =
            GuardedStateless(
                // #528 — suspend decode owns the parse; every list/cursor/flag write below is
                // the non-suspending commit. A filter switch mid-decode drops the body.
                decode = { it.body<AuditLogBrowseResponse>() },
                commit = { page ->
                    // A cold response that lands after a concurrent same-generation fetch
                    // already wrote Success is the older snapshot (older rows + older
                    // cursor — accumulated load-more pages would truncate): skip the whole
                    // commit (pass-6 HARD, the success-side mirror of the pass-5 failure
                    // guard). Cold only launches from Loading/Error/Idle, so Success at
                    // landing ⟺ a concurrent refresh already committed.
                    val listAlreadyCommitted =
                        mode == FetchMode.Cold && browseEntriesState.value is UiState.Success
                    if (listAlreadyCommitted) {
                        logWarn("AuditLogVM", "cold browse success suppressed — list superseded")
                    } else {
                        if (browseRefreshErrorState.value != null) {
                            // Any successful commit supersedes a failed refresh's error line:
                            // the list below is fresh, so the line would be stale (pass-6
                            // SOFT); log rather than vanish silently.
                            logWarn(
                                "AuditLogVM",
                                "browse commit cleared stale refresh error: ${browseRefreshErrorState.value}",
                            )
                        }
                        browseRefreshErrorState.value = null
                        nextCursorState.value = page.nextCursor
                        // A page snapshot taken before an ack commit may still carry the
                        // now-acked row's flag — clear it for locally-acknowledged ids (the
                        // flagged-transform mirror; the badge must not resurrect on browse).
                        applyPage(
                            mode,
                            page.entries.map { entry ->
                                if (entry.id in acknowledgedIds) {
                                    entry.copy(isFlagged = false)
                                } else {
                                    entry
                                }
                            },
                        )
                    }
                    finish(mode)
                },
// #173 — the hand-rolled browseGeneration guard folds into the guarded stale
                // gate: a stale response (applyFilters bumped the generation while this fetch was
                // in flight) is inert — no list write, no cursor write, no error line, no flag
                // cleanup (applyFilters already reset the flags).
                stale = { generation != browseGeneration },
                onNonSuccess = { response ->
                    handlePageFailure(mode, "browse failed: ${response.status.value}")
                    finish(mode)
                },
                onError = { e ->
                    // Network or deserialization failure — same error surface + flag cleanup so the
                    // list state and buttons never freeze (keep-last-list); gated by the stale flag
                    // so a superseded fetch's failure can't surface on the new list. onError is
                    // that single surface (#169).
                    handlePageFailure(mode, "browse failed: ${e.message ?: "network error"}")
                    finish(mode)
                },
            ),
    )
}

internal suspend fun AuditLogViewModel.browseRequest(
    filters: AuditLogFilters,
    cursor: String?,
): HttpResponse =
    apiClient.httpClient.get(ApiRoutes.AUDIT_LOG_ENTRIES_PATH) {
        filters.tableName?.takeIf { it.isNotBlank() }?.let { parameter("tableName", it) }
        filters.action?.let { parameter("action", it) }
        filters.callerName?.takeIf { it.isNotBlank() }?.let { parameter("callerName", it) }
        filters.dateFrom?.takeIf { it.isNotBlank() }?.let { parameter("dateFrom", it) }
        filters.dateTo?.takeIf { it.isNotBlank() }?.let { parameter("dateTo", it) }
        cursor?.let { parameter("cursor", it) }
    }

internal fun AuditLogViewModel.applyPage(
    mode: FetchMode,
    entries: List<AuditLogEntryResponse>,
) {
    when (mode) {
        FetchMode.Cold -> {
            browseEntriesState.value = UiState.Success(entries)
        }

        FetchMode.Refresh -> {
            browseEntriesState.value = UiState.Success(entries)
        }

        FetchMode.LoadMore -> {
            val current =
                (browseEntriesState.value as? UiState.Success<List<AuditLogEntryResponse>>)
                    ?.data
                    .orEmpty()
            browseEntriesState.value = UiState.Success(current + entries)
        }
    }
}

internal fun AuditLogViewModel.handlePageFailure(
    mode: FetchMode,
    message: String,
) {
    when (mode) {
        FetchMode.Cold -> {
            // D10 keep-last: a cold failure only surfaces as the error card when the list
            // holds nothing current. A concurrent same-generation fetch (e.g. a Refresh
            // tapped while the first-visit cold was in flight) may have already written a
            // Success list — a stale cold failure must not clobber it (pass-5 HARD).
            if (browseEntriesState.value !is UiState.Success) {
                browseEntriesState.value = UiState.Error(message)
            } else {
                // The suppressed failure has no user-visible loss (the list is newer), but
                // it must not vanish silently (pass-6 SOFT).
                logWarn("AuditLogVM", "cold browse failure suppressed — list superseded: $message")
            }
        }

        FetchMode.Refresh -> {
            browseRefreshErrorState.value = message
        }

        FetchMode.LoadMore -> {
            loadMoreErrorState.value = message
        }
    }
}

internal fun AuditLogViewModel.finish(mode: FetchMode) {
    if (mode == FetchMode.Refresh) isRefreshingState.value = false
    if (mode == FetchMode.LoadMore) isLoadingMoreState.value = false
}

internal enum class FetchMode(
    val operationName: String,
) {
    Cold("browse"),
    Refresh("refreshBrowse"),
    LoadMore("loadMore"),
}
