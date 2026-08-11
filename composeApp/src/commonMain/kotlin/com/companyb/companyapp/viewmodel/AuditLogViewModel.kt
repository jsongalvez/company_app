package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browse filters for the All-activity tab (#104 D8). Date params are inclusive Manila calendar
 * days (`yyyy-MM-dd`); empty/null = no filter. Applied server-side by `GET /api/audit-log/entries`.
 */
data class AuditLogFilters(
    val tableName: String? = null,
    val action: String? = null,
    val callerName: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

/**
 * State model for the Audit Log screen (#104 D1-D10, built in #123).
 *
 * - For-review tab: [flaggedEntries] (D1) + pessimistic acknowledge (D2) — success removes the
 *   row in place; failure keeps the row with an inline per-row error ([ackErrors]).
 * - All-activity tab: filtered browse with keyset cursor pagination (D5) — [applyFilters] starts
 *   a fresh page-1 load, [loadMore] appends, [refreshBrowse] re-fetches page 1 silently while
 *   keeping the accumulated list (D10 keep-last-list).
 * - Server-driven table dropdown source (D4) + per-record history for the pushed
 *   Route.AuditLogHistory (D8).
 */
class AuditLogViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AuditLogVM")

    private val _flaggedEntries = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val flaggedEntries: StateFlow<UiState<List<AuditLogEntryResponse>>> = _flaggedEntries.asStateFlow()
    private val flaggedPage = MutableStateFlow<UiState<Unit>>(UiState.Idle)

    // Synchronous in-flight guard shared by the cold load and the silent refresh (the handler's
    // Loading lands on flaggedPage only after launch, so a state-based guard would race a rapid
    // double-tap). One slot for both: cold and refresh write the same list, so they must be
    // mutually exclusive (two overlapping snapshots would last-writer-win).
    private var flaggedLoadInFlight = false

    // Entries acknowledged in this VM's lifetime (D2). The server removes them from /flagged, but
    // a refresh GET whose snapshot was taken pre-ack-commit could resurrect a just-acked row — the
    // transform drops them so the locally-acknowledged state is the authority mid-mutation (#143
    // guard shape). Acknowledged rows never legitimately re-appear in the flagged list (re-flagging
    // creates a new entry).
    private val acknowledgedIds = mutableSetOf<String>()

    // D2 — ack in-flight set + per-row inline errors (ADR-0022: pessimistic, failure keeps row).
    private val acknowledgeResult = MutableStateFlow<UiState<AuditLogEntryResponse>>(UiState.Idle)
    private val _acknowledgingIds = MutableStateFlow<Set<String>>(emptySet())
    val acknowledgingIds: StateFlow<Set<String>> = _acknowledgingIds.asStateFlow()
    private val _ackErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val ackErrors: StateFlow<Map<String, String>> = _ackErrors.asStateFlow()

    // D5/D8/D10 — browse: accumulated pages + cursor; page-1 loads drive [browseEntries], while
    // refresh/load-more calls land on a throwaway flow so the accumulated list is never clobbered
    // by a failed or in-flight page fetch.
    private val _browseEntries = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val browseEntries: StateFlow<UiState<List<AuditLogEntryResponse>>> = _browseEntries.asStateFlow()
    private val pageFetch = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    private val _appliedFilters = MutableStateFlow(AuditLogFilters())
    val appliedFilters: StateFlow<AuditLogFilters> = _appliedFilters.asStateFlow()
    private val _nextCursor = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Tab-scoped refresh error lines: a failed For-review refresh must not surface atop All
    // activity and vice versa (each tab renders only its own).
    private val _flaggedRefreshError = MutableStateFlow<String?>(null)
    val flaggedRefreshError: StateFlow<String?> = _flaggedRefreshError.asStateFlow()
    private val _browseRefreshError = MutableStateFlow<String?>(null)
    val browseRefreshError: StateFlow<String?> = _browseRefreshError.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError.asStateFlow()

    // Generation guard for the browse list: applyFilters bumps it, and every in-flight page
    // fetch (cold/refresh/load-more) checks it before committing — a response that belongs to a
    // superseded filter generation must not append to or replace the new list, nor write its
    // cursor or error surface. The stale fetch is deliberately NOT cancelled: cancellation would
    // make the guard untestable, and an inert single GET is cheaper than a second mechanism.
    private var browseGeneration = 0

    // D4 — server-driven table list for the dropdown.
    private val _tables = MutableStateFlow<UiState<List<AuditLogTableResponse>>>(UiState.Idle)
    val tables: StateFlow<UiState<List<AuditLogTableResponse>>> = _tables.asStateFlow()

    // D8 — per-record history (Route.AuditLogHistory gets its own entry-scoped VM instance).
    private val _history = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val history: StateFlow<UiState<List<AuditLogEntryResponse>>> = _history.asStateFlow()

    // D10 — For-review tab load: cold loud path (Loading → error card + retry).
    fun loadFlaggedEntries() {
        if (flaggedLoadInFlight) return
        _flaggedRefreshError.value = null
        _flaggedEntries.value = UiState.Loading
        fetchFlagged(cold = true)
    }

    // D10 — silent refresh of the For-review tab (keep-last-list: the list stays rendered while
    // in-flight and on failure); the failure surfaces as the tab's refresh error line. Also the
    // tab re-entry reload (D10: new flags must appear on re-entry without wiping the list).
    // Skips while an acknowledge is in flight: a refresh GET could snapshot the pre-ack-commit
    // flagged list and resurrect the just-acked row (the ack transform is the authority).
    fun refreshFlagged() {
        if (flaggedLoadInFlight || _acknowledgingIds.value.isNotEmpty()) return
        _flaggedRefreshError.value = null
        fetchFlagged(cold = false)
    }

    // Cold and refresh share one slot (they write the same list) and every failure type —
    // network exception, non-success status, deserialization exception — clears the guard, so
    // neither path can wedge the other (the ack and page transforms were already guarded; this
    // closes the flagged side).
    private fun fetchFlagged(cold: Boolean) {
        flaggedLoadInFlight = true
        handler.launch(
            state = flaggedPage,
            operation = if (cold) "loadFlaggedEntries" else "refreshFlagged",
            endpoint = "GET /api/audit-log/flagged",
            block = {
                try {
                    apiClient.httpClient.get("/api/audit-log/flagged")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    flagLoadFailure(cold, e.message ?: "flagged load failed")
                    flaggedLoadInFlight = false
                    throw e
                }
            },
            transform = {
                try {
                    _flaggedEntries.value =
                        UiState.Success(
                            it.body<List<AuditLogEntryResponse>>().filterNot { entry ->
                                entry.id in acknowledgedIds
                            },
                        )
                    flaggedLoadInFlight = false
                    Unit
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    flagLoadFailure(cold, e.message ?: "flagged load failed")
                    flaggedLoadInFlight = false
                    throw e
                }
            },
            onNonSuccess = { response ->
                flagLoadFailure(cold, "flagged load failed: ${response.status.value}")
                flaggedLoadInFlight = false
                true
            },
        )
    }

    private fun flagLoadFailure(
        cold: Boolean,
        message: String,
    ) {
        if (cold) {
            _flaggedEntries.value = UiState.Error(message)
        } else {
            _flaggedRefreshError.value = message
        }
    }

    // D2 — one-tap acknowledge, pessimistic: row leaves the flagged list only on 2xx; a failure
    // (incl. the server-enforced self-ack 409) keeps the row and surfaces an inline per-row error.
    fun acknowledge(entry: AuditLogEntryResponse) {
        if (entry.id in _acknowledgingIds.value) return
        _acknowledgingIds.value = _acknowledgingIds.value + entry.id
        _ackErrors.value = _ackErrors.value - entry.id
        handler.launch(
            state = acknowledgeResult,
            operation = "acknowledgeEntry",
            endpoint = "PATCH /api/audit-log/${entry.id}/acknowledge",
            block = { apiClient.httpClient.patch("/api/audit-log/${entry.id}/acknowledge") },
            transform = {
                try {
                    val acknowledged = it.body<AuditLogEntryResponse>()
                    acknowledgedIds += entry.id
                    removeFlaggedRow(entry.id)
                    // D2/D10 in-place semantics on both lists: the All-activity row keeps its
                    // badge + Acknowledge button until a refresh otherwise (a re-tap would 404
                    // on an already-acked row — stale state masking success).
                    markAcknowledgedInBrowse(entry.id)
                    _acknowledgingIds.value = _acknowledgingIds.value - entry.id
                    acknowledged
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Deserialization failure — clear the in-flight guard so the row's button
                    // re-enables, and surface an inline error (ADR-0022 pessimistic axis).
                    _acknowledgingIds.value = _acknowledgingIds.value - entry.id
                    _ackErrors.value =
                        _ackErrors.value +
                        (entry.id to (e.message ?: "Acknowledge failed"))
                    throw e
                }
            },
            onNonSuccess = { response ->
                _acknowledgingIds.value = _acknowledgingIds.value - entry.id
                _ackErrors.value =
                    _ackErrors.value +
                    (
                        entry.id to
                            if (response.status == HttpStatusCode.Conflict) {
                                // Self-acknowledge (D2: the editor can't clear their own flag).
                                "Only another reviewer can acknowledge this entry"
                            } else {
                                "Acknowledge failed: ${response.status.value}"
                            }
                    )
                true
            },
        )
    }

    // D8 — apply filter bar values: fresh page-1 load, previous pages discarded. Unguarded by
    // design — an in-flight page fetch from an older filter generation is made inert by the
    // generation guard (it can't append to, replace, or error the new list).
    fun applyFilters(filters: AuditLogFilters) {
        browseGeneration++
        _isRefreshing.value = false
        _isLoadingMore.value = false
        _appliedFilters.value = filters
        _nextCursor.value = null
        _browseRefreshError.value = null
        _loadMoreError.value = null
        _browseEntries.value = UiState.Loading
        fetchPage(FetchMode.Cold, cursor = null)
    }

    // D10 — All-activity first-visit load (cold loud path: Loading → error card + retry).
    fun loadBrowse() {
        applyFilters(_appliedFilters.value)
    }

    // D10 — manual refresh: silent page-1 re-fetch; the accumulated list stays rendered while
    // in-flight and on failure (keep-last-list, #97 Q5 axis). The cursor is NOT pre-nulled: a
    // failed refresh keeps the old list AND its Load-more availability (D5); the success path
    // replaces both from the fresh page.
    fun refreshBrowse() {
        if (_isRefreshing.value || _isLoadingMore.value) return
        _browseRefreshError.value = null
        _loadMoreError.value = null
        fetchPage(FetchMode.Refresh, cursor = null)
    }

    // D10 — error-card retry: re-fires the last applied filters as a cold load (the list state
    // holds no content, so the error card must give way to a fresh Loading + fetch).
    fun retryBrowse() = loadBrowse()

    // D5 — cursor-based pagination: appends the next page; `nextCursor` null = last page.
    fun loadMore() {
        val cursor = _nextCursor.value ?: return
        if (_isLoadingMore.value || _isRefreshing.value) return
        _loadMoreError.value = null
        fetchPage(FetchMode.LoadMore, cursor = cursor)
    }

    // D4 — server-driven table dropdown source; retryable from the dropdown's error state.
    fun loadTables() {
        handler.launch(
            state = _tables,
            operation = "loadTables",
            endpoint = "GET /api/audit-log/tables",
            block = { apiClient.httpClient.get("/api/audit-log/tables") },
            transform = { it.body() },
        )
    }

    // D8 — full history for one record (pushed Route.AuditLogHistory).
    fun loadHistory(
        tableName: String,
        recordId: String,
    ) {
        handler.launch(
            state = _history,
            operation = "loadHistory",
            endpoint = "GET /api/audit-log?tableName=$tableName&recordId=$recordId",
            block = {
                apiClient.httpClient.get("/api/audit-log") {
                    parameter("tableName", tableName)
                    parameter("recordId", recordId)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                if (response.status == HttpStatusCode.NotFound) {
                    // The record has no audit trail (never audited / scrubbed): a truthful
                    // message, not the generic "history failed: 404" (an absent record is not
                    // an outage — D8 trail semantics).
                    _history.value = UiState.Error("No audit trail found for this record")
                    true
                } else {
                    false
                }
            },
        )
    }

    private fun removeFlaggedRow(entryId: String) {
        val current = (_flaggedEntries.value as? UiState.Success<List<AuditLogEntryResponse>>)?.data ?: return
        _flaggedEntries.value = UiState.Success(current.filterNot { it.id == entryId })
    }

    // D2/D10 in-place ack semantics on the All-activity list: the row keeps its position but the
    // flag clears, so the badge + Acknowledge affordance disappear without a refresh.
    private fun markAcknowledgedInBrowse(entryId: String) {
        val current =
            (_browseEntries.value as? UiState.Success<List<AuditLogEntryResponse>>)
                ?.data
                ?: return
        _browseEntries.value =
            UiState.Success(
                current.map { entry ->
                    if (entry.id == entryId) entry.copy(isFlagged = false) else entry
                },
            )
    }

    // Any failure type must clear the in-flight flags before the handler assigns Error.
    @Suppress("TooGenericExceptionCaught")
    private fun fetchPage(
        mode: FetchMode,
        cursor: String?,
    ) {
        val filters = _appliedFilters.value
        val generation = browseGeneration
        if (mode == FetchMode.Refresh) _isRefreshing.value = true
        if (mode == FetchMode.LoadMore) _isLoadingMore.value = true
        handler.launch(
            // Every mode lands on the throwaway flow; the list state is mutated in transform, so
            // a failed or in-flight page fetch can never clobber the accumulated list (D10).
            state = pageFetch,
            operation = mode.operationName,
            endpoint = "GET /api/audit-log/entries",
            block = {
                try {
                    browseRequest(filters, cursor)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Network failure: the handler assigns Error to the throwaway flow; route the
                    // error to the mode's error surface and clear the in-flight flags — but only
                    // if this fetch belongs to the current filter generation (a superseded
                    // fetch's failure must not surface as an error on the new list).
                    if (generation == browseGeneration) {
                        handlePageFailure(mode, e.message ?: "browse failed")
                        finish(mode)
                    }
                    throw e
                }
            },
            transform = {
                try {
                    val page = it.body<AuditLogBrowseResponse>()
                    // A stale response (applyFilters bumped the generation while this fetch was
                    // in flight) is inert: no list write, no cursor write, no flag cleanup
                    // (applyFilters already reset the flags).
                    if (generation == browseGeneration) {
                        _nextCursor.value = page.nextCursor
                        applyPage(mode, page)
                        finish(mode)
                    }
                    Unit
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Deserialization failure — same error surface + flag cleanup as a network
                    // failure so the list state and buttons never freeze (keep-last-list).
                    if (generation == browseGeneration) {
                        handlePageFailure(mode, e.message ?: "browse failed")
                        finish(mode)
                    }
                    throw e
                }
            },
            onNonSuccess = { response ->
                if (generation == browseGeneration) {
                    handlePageFailure(mode, "browse failed: ${response.status.value}")
                    finish(mode)
                }
                true
            },
        )
    }

    private suspend fun browseRequest(
        filters: AuditLogFilters,
        cursor: String?,
    ): HttpResponse =
        apiClient.httpClient.get("/api/audit-log/entries") {
            filters.tableName?.takeIf { it.isNotBlank() }?.let { parameter("tableName", it) }
            filters.action?.let { parameter("action", it) }
            filters.callerName?.takeIf { it.isNotBlank() }?.let { parameter("callerName", it) }
            filters.dateFrom?.takeIf { it.isNotBlank() }?.let { parameter("dateFrom", it) }
            filters.dateTo?.takeIf { it.isNotBlank() }?.let { parameter("dateTo", it) }
            cursor?.let { parameter("cursor", it) }
        }

    private fun applyPage(
        mode: FetchMode,
        page: AuditLogBrowseResponse,
    ) {
        when (mode) {
            FetchMode.Cold -> {
                _browseEntries.value = UiState.Success(page.entries)
            }

            FetchMode.Refresh -> {
                _browseEntries.value = UiState.Success(page.entries)
            }

            FetchMode.LoadMore -> {
                val current =
                    (_browseEntries.value as? UiState.Success<List<AuditLogEntryResponse>>)
                        ?.data
                        .orEmpty()
                _browseEntries.value = UiState.Success(current + page.entries)
            }
        }
    }

    private fun handlePageFailure(
        mode: FetchMode,
        message: String,
    ) {
        when (mode) {
            FetchMode.Cold -> _browseEntries.value = UiState.Error(message)
            FetchMode.Refresh -> _browseRefreshError.value = message
            FetchMode.LoadMore -> _loadMoreError.value = message
        }
    }

    private fun finish(mode: FetchMode) {
        if (mode == FetchMode.Refresh) _isRefreshing.value = false
        if (mode == FetchMode.LoadMore) _isLoadingMore.value = false
    }

    private enum class FetchMode(
        val operationName: String,
    ) {
        Cold("browse"),
        Refresh("refreshBrowse"),
        LoadMore("loadMore"),
    }
}
