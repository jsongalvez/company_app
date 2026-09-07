package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ActionTracker
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.StatelessHooks
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.http.HttpStatusCode
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
    internal val apiClient: ApiClient,
) : ViewModel() {
    internal val handler = ApiCallHandler(viewModelScope, "AuditLogVM")

    private val _flaggedEntries = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val flaggedEntries: StateFlow<UiState<List<AuditLogEntryResponse>>> = _flaggedEntries.asStateFlow()

    // Synchronous in-flight guard shared by the cold load and the silent refresh (a state-based
    // guard would race a rapid double-tap — the state-less launch writes no Loading at all).
    // One slot for both: cold and refresh write the same list, so they must be
    // mutually exclusive (two overlapping snapshots would last-writer-win). StateFlow so the
    // Refresh button can disable per tab.
    private val _flaggedLoadInFlight = MutableStateFlow(false)
    val flaggedLoadInFlight: StateFlow<Boolean> = _flaggedLoadInFlight.asStateFlow()

    // Entries acknowledged in this VM's lifetime (D2). The server removes them from /flagged, but
    // a refresh GET whose snapshot was taken pre-ack-commit could resurrect a just-acked row — the
    // transform drops them so the locally-acknowledged state is the authority mid-mutation (#143
    // guard shape). Acknowledged rows never legitimately re-appear in the flagged list (re-flagging
    // creates a new entry).
    internal val acknowledgedIds = mutableSetOf<String>()

    // D2 — ack in-flight set + per-row inline errors (ADR-0022: pessimistic, failure keeps row).
    // The acknowledge response routes through ackTracker + the list writes; no state flow.
    private val ackTracker = ActionTracker<String>()
    val acknowledgingIds: StateFlow<Set<String>> = ackTracker.inFlight
    val ackErrors: StateFlow<Map<String, String>> = ackTracker.errors

    // D5/D8/D10 — browse: accumulated pages + cursor; page-1 loads drive [browseEntries], while
    // refresh/load-more calls are state-less (the #168 launch) so the accumulated list is never
    // clobbered by a failed or in-flight page fetch.
    internal val browseEntriesState = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val browseEntries: StateFlow<UiState<List<AuditLogEntryResponse>>> = browseEntriesState.asStateFlow()
    internal val appliedFiltersState = MutableStateFlow(AuditLogFilters())
    val appliedFilters: StateFlow<AuditLogFilters> = appliedFiltersState.asStateFlow()
    internal val nextCursorState = MutableStateFlow<String?>(null)
    val nextCursor: StateFlow<String?> = nextCursorState.asStateFlow()
    internal val isRefreshingState = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = isRefreshingState.asStateFlow()

    // Tab-scoped refresh error lines: a failed For-review refresh must not surface atop All
    // activity and vice versa (each tab renders only its own).
    private val _flaggedRefreshError = MutableStateFlow<String?>(null)
    val flaggedRefreshError: StateFlow<String?> = _flaggedRefreshError.asStateFlow()
    internal val browseRefreshErrorState = MutableStateFlow<String?>(null)
    val browseRefreshError: StateFlow<String?> = browseRefreshErrorState.asStateFlow()
    internal val isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = isLoadingMoreState.asStateFlow()
    internal val loadMoreErrorState = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = loadMoreErrorState.asStateFlow()

    // Generation guard for the browse list: applyFilters bumps it, and every in-flight page
    // fetch (cold/refresh/load-more) checks it before committing — a response that belongs to a
    // superseded filter generation must not append to or replace the new list, nor write its
    // cursor or error surface. The stale fetch is deliberately NOT cancelled: cancellation would
    // make the guard untestable, and an inert single GET is cheaper than a second mechanism.
    internal var browseGeneration = 0

    // D4 — server-driven table list for the dropdown.
    private val _tables = MutableStateFlow<UiState<List<AuditLogTableResponse>>>(UiState.Idle)
    val tables: StateFlow<UiState<List<AuditLogTableResponse>>> = _tables.asStateFlow()

    // D8 — per-record history (Route.AuditLogHistory gets its own entry-scoped VM instance).
    private val _history = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val history: StateFlow<UiState<List<AuditLogEntryResponse>>> = _history.asStateFlow()

    // D10 — For-review tab load: cold loud path (Loading → error card + retry).
    fun loadFlaggedEntries() {
        if (_flaggedLoadInFlight.value) return
        _flaggedRefreshError.value = null
        _flaggedEntries.value = UiState.Loading
        fetchFlagged(cold = true)
    }

    // D10 — silent refresh of the For-review tab (keep-last-list: the list stays rendered while
    // in-flight and on failure); the failure surfaces as the tab's refresh error line. Also the
    // tab re-entry reload (D10: new flags must appear on re-entry without wiping the list). No
    // ack-in-flight skip: the transform's acknowledgedIds filter already kills the pre-commit-
    // snapshot resurrection deterministically, and skipping would silently drop the re-entry
    // reload's new flags.
    fun refreshFlagged() {
        if (_flaggedLoadInFlight.value) return
        _flaggedRefreshError.value = null
        fetchFlagged(cold = false)
    }

    // Cold and refresh share one slot (they write the same list) and every failure type —
    // network exception, non-success status, deserialization exception — clears the guard, so
    // neither path can wedge the other (the ack and page transforms were already guarded; this
    // closes the flagged side).
    private fun fetchFlagged(cold: Boolean) {
        _flaggedLoadInFlight.value = true
        handler.launchStateless(
            operation = if (cold) "loadFlaggedEntries" else "refreshFlagged",
            endpoint = "GET /api/audit-log/flagged",
            block = { apiClient.httpClient.get(ApiRoutes.AUDIT_LOG_FLAGGED_PATH) },
            transform = {
                _flaggedEntries.value =
                    UiState.Success(
                        it.body<List<AuditLogEntryResponse>>().filterNot { entry ->
                            entry.id in acknowledgedIds
                        },
                    )
                _flaggedLoadInFlight.value = false
            },
            hooks =
                StatelessHooks(
                    onNonSuccess = { response ->
                        flaggedLoadFailure(cold, "flagged load failed: ${response.status.value}")
                        _flaggedLoadInFlight.value = false
                    },
                    onError = { e ->
                        // Transport or deserialization failure: run the same surface + in-flight-flag
                        // clear the pre-port block/transform catches ran — onError is the single
                        // failure surface, the handler keeps its logging (#169).
                        flaggedLoadFailure(cold, "flagged load failed: ${e.message ?: "network error"}")
                        _flaggedLoadInFlight.value = false
                    },
                ),
        )
    }

    private fun flaggedLoadFailure(
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
        if (!ackTracker.tryBegin(entry.id)) return
        handler.launchStateless(
            operation = "acknowledgeEntry",
            endpoint = "PATCH /api/audit-log/${entry.id}/acknowledge",
            block = { apiClient.httpClient.patch(ApiRoutes.auditLogAcknowledge(entry.id)) },
            transform = {
                it.body<AuditLogEntryResponse>()
                acknowledgedIds += entry.id
                removeFlaggedRow(entry.id)
                // D2/D10 in-place semantics on both lists: the All-activity row keeps its
                // badge + Acknowledge button until a refresh otherwise (a re-tap would 404
                // on an already-acked row — stale state masking success).
                markAcknowledgedInBrowse(entry.id)
                ackTracker.finish(entry.id)
            },
            hooks =
                StatelessHooks(
                    onNonSuccess = { response ->
                        ackTracker.fail(
                            entry.id,
                            if (response.status == HttpStatusCode.Conflict) {
                                // Self-acknowledge (D2: the editor can't clear their own flag).
                                "Only another reviewer can acknowledge this entry"
                            } else {
                                "Acknowledge failed: ${response.status.value}"
                            },
                        )
                    },
                    onError = { e ->
                        // Every failure path (transport or deserialization) clears the in-flight guard
                        // so the row's button re-enables, and surfaces an inline error (ADR-0022
                        // pessimistic axis; #123 decision 2) — onError is that surface (#169).
                        ackTracker.fail(entry.id, "Acknowledge failed: ${e.message ?: "network error"}")
                    },
                ),
        )
    }

    // D4 — server-driven table dropdown source; retryable from the dropdown's error state.
    fun loadTables() {
        handler.launch(
            state = _tables,
            operation = "loadTables",
            endpoint = "GET /api/audit-log/tables",
            block = { apiClient.httpClient.get(ApiRoutes.AUDIT_LOG_TABLES_PATH) },
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
                apiClient.httpClient.get(ApiRoutes.AUDIT_LOG) {
                    parameter("tableName", tableName)
                    parameter("recordId", recordId)
                }
            },
            transform = { it.body() },
            // No onNonSuccess: the per-record endpoint returns 200 + empty for absent/out-of-
            // window records ("the rows are invisible, not an error" — backend contract), so the
            // empty state renders; a 4xx/5xx here is a genuine outage → generic ErrorCard + retry.
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
            (browseEntriesState.value as? UiState.Success<List<AuditLogEntryResponse>>)
                ?.data
                ?: return
        browseEntriesState.value =
            UiState.Success(
                current.map { entry ->
                    if (entry.id == entryId) entry.copy(isFlagged = false) else entry
                },
            )
    }
}
