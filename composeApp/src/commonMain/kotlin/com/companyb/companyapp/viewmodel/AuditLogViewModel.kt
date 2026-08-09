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

    // Synchronous in-flight guard for silent flagged refreshes (the handler's Loading lands on
    // flaggedPage only after launch, so a state-based guard would race a rapid double-tap).
    private var flaggedRefreshInFlight = false

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
    private val appliedFilters = MutableStateFlow(AuditLogFilters())
    private val _nextCursor = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError.asStateFlow()

    // D4 — server-driven table list for the dropdown.
    private val _tables = MutableStateFlow<UiState<List<AuditLogTableResponse>>>(UiState.Idle)
    val tables: StateFlow<UiState<List<AuditLogTableResponse>>> = _tables.asStateFlow()

    // D8 — per-record history (Route.AuditLogHistory gets its own entry-scoped VM instance).
    private val _history = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val history: StateFlow<UiState<List<AuditLogEntryResponse>>> = _history.asStateFlow()

    // D10 — For-review tab load.
    fun loadFlaggedEntries() {
        handler.launch(
            state = _flaggedEntries,
            operation = "loadFlaggedEntries",
            endpoint = "GET /api/audit-log/flagged",
            block = { apiClient.httpClient.get("/api/audit-log/flagged") },
            transform = { it.body() },
        )
    }

    // D10 — silent refresh of the For-review tab (keep-last-list: the list stays rendered while
    // in-flight and on failure); the failure surfaces as the tab's refresh error line. Also the
    // tab re-entry reload (D10: new flags must appear on re-entry without wiping the list).
    fun refreshFlagged() {
        if (flaggedRefreshInFlight) return
        _refreshError.value = null
        flaggedRefreshInFlight = true
        handler.launch(
            state = flaggedPage,
            operation = "refreshFlagged",
            endpoint = "GET /api/audit-log/flagged",
            block = {
                try {
                    apiClient.httpClient.get("/api/audit-log/flagged")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _refreshError.value = e.message ?: "refresh failed"
                    flaggedRefreshInFlight = false
                    throw e
                }
            },
            transform = {
                _flaggedEntries.value = UiState.Success(it.body())
                flaggedRefreshInFlight = false
                Unit
            },
            onNonSuccess = { response ->
                _refreshError.value = "refresh failed: ${response.status.value}"
                flaggedRefreshInFlight = false
                true
            },
        )
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
                    removeFlaggedRow(entry.id)
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

    // D8 — apply filter bar values: fresh page-1 load, previous pages discarded.
    fun applyFilters(filters: AuditLogFilters) {
        appliedFilters.value = filters
        _nextCursor.value = null
        _refreshError.value = null
        _loadMoreError.value = null
        _browseEntries.value = UiState.Loading
        fetchPage(FetchMode.Cold, cursor = null)
    }

    // D10 — manual refresh: silent page-1 re-fetch; the accumulated list stays rendered while
    // in-flight and on failure (keep-last-list, #97 Q5 axis). The cursor is NOT pre-nulled: a
    // failed refresh keeps the old list AND its Load-more availability (D5); the success path
    // replaces both from the fresh page.
    fun refreshBrowse() {
        if (_isRefreshing.value || _isLoadingMore.value) return
        _refreshError.value = null
        fetchPage(FetchMode.Refresh, cursor = null)
    }

    // D10 — error-card retry: re-fires the last applied filters as a cold load (the list state
    // holds no content, so the error card must give way to a fresh Loading + fetch).
    fun retryBrowse() {
        applyFilters(appliedFilters.value)
    }

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
        )
    }

    private fun removeFlaggedRow(entryId: String) {
        val current = (_flaggedEntries.value as? UiState.Success<List<AuditLogEntryResponse>>)?.data ?: return
        _flaggedEntries.value = UiState.Success(current.filterNot { it.id == entryId })
    }

    // Any failure type must clear the in-flight flags before the handler assigns Error.
    @Suppress("TooGenericExceptionCaught")
    private fun fetchPage(
        mode: FetchMode,
        cursor: String?,
    ) {
        val filters = appliedFilters.value
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
                    // error to the mode's error surface and clear the in-flight flags.
                    handlePageFailure(mode, e.message ?: "browse failed")
                    finish(mode)
                    throw e
                }
            },
            transform = {
                try {
                    val page = it.body<AuditLogBrowseResponse>()
                    _nextCursor.value = page.nextCursor
                    applyPage(mode, page)
                    finish(mode)
                    Unit
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Deserialization failure — same error surface + flag cleanup as a network
                    // failure so the list state and buttons never freeze (keep-last-list).
                    handlePageFailure(mode, e.message ?: "browse failed")
                    finish(mode)
                    throw e
                }
            },
            onNonSuccess = { response ->
                handlePageFailure(mode, "browse failed: ${response.status.value}")
                finish(mode)
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
            FetchMode.Refresh -> _refreshError.value = message
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
