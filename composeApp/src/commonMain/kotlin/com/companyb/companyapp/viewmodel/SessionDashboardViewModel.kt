package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.ui.screen.DashboardEditField
import com.companyb.companyapp.ui.screen.DashboardEditState
import com.companyb.companyapp.ui.screen.afterReload
import com.companyb.companyapp.ui.screen.asConflict
import com.companyb.companyapp.ui.screen.asFailed
import com.companyb.companyapp.ui.screen.asInFlight
import com.companyb.companyapp.ui.screen.beginEdit
import com.companyb.companyapp.ui.screen.draftChanged
import com.companyb.companyapp.ui.screen.finalPriceInputValid
import com.companyb.companyapp.ui.screen.mergeDashboardRows
import com.companyb.companyapp.ui.screen.withDraft
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

enum class DashboardPollStatus {
    FRESH,
    STALE,
    ERRORED,
}

/**
 * Session dashboard (#147, graduated from #97). One fetch backs both the summary cards and the
 * session list (the Q6c "share an endpoint" escape hatch — the regions cannot diverge because
 * they consume the same response), polled every 30s.
 *
 * #97 Q5 locked semantics:
 * - **Completion-then-wait**: the poll loop joins the fetch job before its 30s delay — a slow
 *   poll never overlaps the next one (no wall-clock stacking).
 * - **Silent + last-successful timestamp**: [lastUpdatedAt] is written only on success (a
 *   "last attempt" timestamp that reads fresh while failing is the misleading case).
 * - **Failure thresholds**: 2 consecutive failures → STALE (data preserved, banner);
 *   5 → ERRORED (error card with Retry). Success resets the counter.
 * - **401 is session termination, not degradation**: swallowed here (ApiClient.onUnauthorized
 *   drives the global redirect) and never counted against the thresholds.
 * - **403 (attendance ended server-side)**: forbidden flag + poll stops; retryAfterForbidden
 *   re-enables. The drawer clock-out remains the clean exit.
 * - **Keep-last-results** (Clients #113 D2 / Notifications #141): [lastData] survives the
 *   Loading frame of a retry/poll — the screen renders it whenever non-null.
 *
 * Lifecycle (Q5c): [pause] stops polling when the dashboard route leaves composition (mobile
 * SessionDetail push — desktop's inline pane never leaves), [resume] restarts it.
 */
class SessionDashboardViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "DashboardVM")

    private val _dashboardState = MutableStateFlow<UiState<DashboardResponse>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardResponse>> = _dashboardState.asStateFlow()

    private val _lastData = MutableStateFlow<DashboardResponse?>(null)
    val lastData: StateFlow<DashboardResponse?> = _lastData.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<Instant?>(null)
    val lastUpdatedAt: StateFlow<Instant?> = _lastUpdatedAt.asStateFlow()

    private val _pollStatus = MutableStateFlow(DashboardPollStatus.FRESH)
    val pollStatus: StateFlow<DashboardPollStatus> = _pollStatus.asStateFlow()

    private val _isForbidden = MutableStateFlow(false)
    val isForbidden: StateFlow<Boolean> = _isForbidden.asStateFlow()

    // #149 — inline editing (#97 Q4 + ADR-0022 pessimistic model). canEdit mirrors the
    // per-element capability guard (#92): EDIT_BRANCH_DATA held at the selected branch
    // (strict BRANCH triple, #156 — matching the backend's strict branch gate on the
    // session PATCH endpoints); a PATCH 403 sets it false (Q4: silent exit + affordance
    // vanishes — no capability-refetch machinery exists, Q2 of #155 deferred it).
    private val _canEdit =
        MutableStateFlow(
            SessionState.capabilities.value.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                SessionState.selectedBranchId.value,
            ),
        )
    val canEdit: StateFlow<Boolean> = _canEdit.asStateFlow()

    private val _editState = MutableStateFlow<DashboardEditState?>(null)
    val editState: StateFlow<DashboardEditState?> = _editState.asStateFlow()

    private var consecutiveFailures = 0
    private var pollJob: Job? = null

    init {
        resume()
    }

    private fun countFailure() {
        // NOTE the counting cannot live in a _dashboardState collector — StateFlow conflates
        // equal Error values, so consecutive identical errors would never re-emit
        // (probe-proven in the #147 build). Hooked from both failure paths below.
        consecutiveFailures++
        _pollStatus.value =
            if (consecutiveFailures >= ERROR_THRESHOLD) {
                DashboardPollStatus.ERRORED
            } else if (consecutiveFailures >= STALE_THRESHOLD) {
                DashboardPollStatus.STALE
            } else {
                DashboardPollStatus.FRESH
            }
    }

    fun resume() {
        if (pollJob?.isActive == true) return
        logInfo("DashboardVM", "poll resume")
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    refresh().join()
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    fun pause() {
        if (pollJob?.isActive == true) {
            logInfo("DashboardVM", "poll pause")
        }
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh(): Job {
        val branchId = SessionState.selectedBranchId.value
        if (branchId == null) {
            // Unreachable post-clock-in (the dashboard is only composed with a selected
            // branch); fail closed rather than fetch a malformed path. A CANCELLED job is
            // returned so the poll loop's join() returns immediately.
            return Job().also { it.cancel() }
        }
        // In-flight guard (manual refresh overlapping a poll): a cancelled job keeps the
        // poll loop's join() from hanging — a bare `Job()` never completes. The synchronous
        // Loading pre-set below makes the guard hold from the CALLER's frame (the #135
        // double-tap pattern — handler.launch pre-sets Loading only inside its coroutine,
        // so two back-to-back refresh() calls would both pass the guard otherwise).
        if (_dashboardState.value is UiState.Loading) {
            return Job().also { it.cancel() }
        }
        _dashboardState.value = UiState.Loading
        return handler.launch(
            state = _dashboardState,
            operation = "loadDashboard",
            endpoint = "GET /api/branches/$branchId/dashboard/today",
            block = { apiClient.httpClient.get(ApiRoutes.branchDashboardToday(branchId)) },
            transform = {
                it.body<DashboardResponse>().also { data ->
                    // Q5a — the failure counter and the timestamp reset only on SUCCESS.
                    consecutiveFailures = 0
                    _pollStatus.value = DashboardPollStatus.FRESH
                    // #149 — monotonic per-row version merge: a poll response that started
                    // before a successful inline edit landed carries an older version and
                    // must never regress the committed row (stale-poll-after-commit).
                    _lastData.value =
                        data.copy(sessions = mergeDashboardRows(_lastData.value?.sessions, data.sessions))
                    _lastUpdatedAt.value = Clock.System.now()
                }
            },
            onNonSuccess = { response ->
                when (response.status.value) {
                    // 401 — session termination: the global ApiClient.onUnauthorized path
                    // handles the redirect; never counts as poll degradation (Q5b). The
                    // handler pre-set Loading, so reset the state or the in-flight guard
                    // wedges every future poll. Polling stops too — the session is dead.
                    401 -> {
                        _dashboardState.value = UiState.Idle
                        pause()
                        true
                    }

                    // 403 — the attendance gate: clock-in ended server-side (e.g. clocked
                    // out elsewhere). Stop polling; the forbidden card + drawer clock-out.
                    403 -> {
                        _isForbidden.value = true
                        _dashboardState.value = UiState.Idle
                        pause()
                        true
                    }

                    // Generic HTTP failure: counted against the stale/error thresholds.
                    else -> {
                        countFailure()
                        false
                    }
                }
            },
            // Transport/deserialization failures land here (onNonSuccess only sees HTTP
            // statuses) — without this hook a dead network would silently freeze the last
            // data with no stale banner, no escalation, no retry (pass-1 HARD).
            onError = { countFailure() },
        )
    }

    // --- #149 inline editing (desktop only, #97 Q4) ---

    /**
     * Opens the editor on a cell. While a commit is in flight the click is ignored (the
     * blur-commit of a price editor dispatches first and owns the machine); otherwise an
     * existing editor is replaced — safe because a price editor with an edited draft
     * commits on blur (clicking another cell) before this runs, and a dropdown editor
     * only ever holds an unchanged draft until a selection commits.
     */
    fun startEdit(
        sessionId: String,
        field: DashboardEditField,
    ) {
        val current = _editState.value
        if (current != null && current.inFlight) return
        if (current != null && current.error != null) {
            // A failed edit (Model-A error still showing) is not silently replaced by a cell
            // switch — the user discards (Esc) or retries first, so an attempted draft is
            // never dropped without resolution (the #142 field-switch draft-drop class).
            // EXCEPT when the machine's row has vanished from the list (e.g. the day
            // rollover drops the edited row): the editor is already invisible, and a parked
            // machine would wedge every future edit (pass-3 finding).
            val rowStillPresent = _lastData.value?.sessions?.any { it.id == current.sessionId } == true
            if (rowStillPresent) return
            _editState.value = null
        }
        val row = _lastData.value?.sessions?.firstOrNull { it.id == sessionId } ?: return
        _editState.value = beginEdit(row, field)
    }

    fun updateDraft(draft: String) {
        _editState.value = _editState.value?.withDraft(draft)
    }

    fun discardEdit() {
        val state = _editState.value ?: return
        if (state.inFlight) return
        _editState.value = null
    }

    /**
     * Pessimistic commit (ADR-0022): nothing changes on screen until the PATCH succeeds.
     * An unchanged draft exits without a request; an invalid price draft fails client-side
     * (the #135 parse-mirror pattern — the backend 400 never sees it); a conflict-state
     * commit is blocked (Reload is the sanctioned path). The post-dispatch machine
     * transitions all live inside the handler call (transform / onNonSuccess / onError) so
     * there is no second transition site to drift (the #142/#147 lesson); the pre-dispatch
     * guards above run before the request is sent.
     */
    fun commitEdit() {
        val state = _editState.value ?: return
        if (state.inFlight) return
        // The 409 conflict is resolved by Reload, not by re-dispatching the same stale
        // version (a retry without reload is a guaranteed 409 — pass-1 finding: the
        // blur-commit on the Reload click re-dispatched the doomed PATCH and swallowed
        // the first Reload click).
        if (state.conflict) return
        // A vanished row (no session-deletion path exists, but fail closed rather than
        // park an editor on a row that can no longer be committed).
        val row =
            _lastData.value?.sessions?.firstOrNull { it.id == state.sessionId }
                ?: run {
                    _editState.value = null
                    return
                }
        if (!draftChanged(state, row)) {
            logInfo("DashboardVM", "edit discarded — draft unchanged")
            _editState.value = null
            return
        }
        if (state.field == DashboardEditField.FINAL_PRICE && !finalPriceInputValid(state.draft)) {
            _editState.value = state.asFailed(INVALID_PRICE_MESSAGE)
            return
        }
        _editState.value = state.asInFlight()
        val request = editRequest(state)
        handler.launchStateless(
            operation = "updateSession",
            endpoint = "PATCH ${request.path}",
            block = {
                apiClient.httpClient.patch(request.path) {
                    setBody(request.body)
                }
            },
            transform = {
                // The updated row is committed to the machine ([editState] carries what the UI
                // renders — the #168 state-less launch has no result flow to write).
                val updated = it.body<SessionResponse>()
                commitRow(updated)
                _editState.value = null
            },
            onNonSuccess = { response ->
                when (response.status.value) {
                    // Q4: 403 — capability revoked mid-edit: silent exit + the affordance
                    // vanishes (no optimistic state to reconcile, ADR-0022).
                    403 -> {
                        logWarn("DashboardVM", "edit forbidden (403) — affordance hidden")
                        _editState.value = null
                        _canEdit.value = false
                    }

                    // ADR-0022: 409 — version conflict: keep the draft + inline error +
                    // Reload action; reload re-baselines the version (never a silent
                    // lost update — the expectedVersion is the edit-start snapshot).
                    409 -> {
                        _editState.value = _editState.value?.asConflict(CONFLICT_MESSAGE)
                    }

                    // Model A: any other HTTP failure keeps the draft + inline error.
                    else -> {
                        _editState.value =
                            _editState.value?.asFailed("Update failed (${response.status.value}) — retry or discard")
                    }
                }
            },
            onError = {
                _editState.value = _editState.value?.asFailed("Update failed — check your connection and retry")
            },
        )
    }

    /**
     * The 409 path's Reload action: refresh, then re-baseline the machine from the fresh
     * row. Guards (pass-1 findings): only re-baselines when the fresh row actually carries a
     * newer version (a failed or silently-cancelled refresh must NOT clear the conflict —
     * the user would retry a stale version forever with no visible failure); and only for
     * the machine still being edited (a discard or a new edit during the reload owns the
     * state).
     */
    fun reloadAfterConflict() {
        val state = _editState.value ?: return
        if (state.inFlight || !state.conflict) return
        viewModelScope.launch {
            refresh().join()
            val current =
                _editState.value
                    ?: return@launch
            if (current.sessionId != state.sessionId || current.field != state.field || !current.conflict) {
                return@launch
            }
            val row = _lastData.value?.sessions?.firstOrNull { it.id == current.sessionId } ?: return@launch
            if (row.version <= current.baselineVersion) {
                // No newer data landed (refresh was cancelled by an in-flight poll, or the
                // fetch failed) — the conflict stands; the user clicks Reload again.
                return@launch
            }
            _editState.value = current.afterReload(row)
        }
    }

    private fun commitRow(updated: SessionResponse) {
        val data = _lastData.value ?: return
        val rows =
            data.sessions.map { row ->
                if (row.id == updated.id) {
                    mergeCommittedRow(row, updated)
                } else {
                    row
                }
            }
        _lastData.value = data.copy(sessions = rows)
    }

    private fun mergeCommittedRow(
        row: DashboardSessionResponse,
        updated: SessionResponse,
    ): DashboardSessionResponse =
        row.copy(
            sessionType = updated.sessionType,
            isWalkIn = updated.isWalkIn,
            sessionStatus = updated.sessionStatus,
            basePrice = updated.basePrice,
            finalPrice = updated.finalPrice,
            remarks = updated.remarks,
            otherConcerns = updated.otherConcerns,
            bookedAt = updated.bookedAt,
            nextAppointmentDate = updated.nextAppointmentDate,
            version = updated.version,
            // dashboard-only fields (clientName / isVoided / practitioners / concerns)
            // keep the existing row's — the PATCH response carries none of them.
        )

    private data class EditRequest(
        val path: String,
        val body: Any,
    )

    private fun editRequest(state: DashboardEditState): EditRequest =
        when (state.field) {
            DashboardEditField.STATUS -> {
                EditRequest(
                    ApiRoutes.sessionStatus(state.sessionId),
                    UpdateSessionStatusRequest(
                        com.companyb.companyapp.domain.SessionStatus
                            .valueOf(state.draft),
                        state.baselineVersion,
                    ),
                )
            }

            DashboardEditField.FINAL_PRICE -> {
                EditRequest(
                    ApiRoutes.sessionFinalPrice(state.sessionId),
                    UpdateSessionFinalPriceRequest(state.draft.trim(), state.baselineVersion),
                )
            }
        }

    fun retryAfterForbidden() {
        _isForbidden.value = false
        resume()
        refresh()
    }

    private companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val STALE_THRESHOLD = 2
        private const val ERROR_THRESHOLD = 5
        private const val CONFLICT_MESSAGE =
            "This session was updated by someone else — reload to see the latest changes"
        private const val INVALID_PRICE_MESSAGE = "Enter a valid amount (digits only, e.g. 2500.00)"
    }
}
