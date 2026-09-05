package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.isStatusCorrection
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasBranchOrDayCapability
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
import com.companyb.companyapp.ui.screen.normalizedReason
import com.companyb.companyapp.ui.screen.remittedReasonRequired
import com.companyb.companyapp.ui.screen.statusEditAllowed
import com.companyb.companyapp.ui.screen.statusOptionsFor
import com.companyb.companyapp.ui.screen.withDraft
import com.companyb.companyapp.ui.screen.withReason
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
    internal val apiClient: ApiClient,
) : ViewModel() {
    internal val handler = ApiCallHandler(viewModelScope, "DashboardVM")

    private val _dashboardState = MutableStateFlow<UiState<DashboardResponse>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardResponse>> = _dashboardState.asStateFlow()

    internal val lastDataCache = MutableStateFlow<DashboardResponse?>(null)
    val lastData: StateFlow<DashboardResponse?> = lastDataCache.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<Instant?>(null)
    val lastUpdatedAt: StateFlow<Instant?> = _lastUpdatedAt.asStateFlow()

    private val _pollStatus = MutableStateFlow(DashboardPollStatus.FRESH)
    val pollStatus: StateFlow<DashboardPollStatus> = _pollStatus.asStateFlow()

    private val _isForbidden = MutableStateFlow(false)
    val isForbidden: StateFlow<Boolean> = _isForbidden.asStateFlow()

    // #149 — inline editing (#97 Q4 + ADR-0022 pessimistic model). canEdit mirrors the
    // per-element capability guard (#92): EDIT_BRANCH_DATA held at the selected branch or
    // clocked-in branch day, matching the status PATCH route's branch-or-day gate; a PATCH 403
    // sets it false (Q4: silent exit + affordance vanishes).
    internal val canEditState =
        MutableStateFlow(
            SessionState.snapshot.value.let { snap ->
                snap.capabilities.hasBranchOrDayCapability(
                    code = CapabilityCodes.EDIT_BRANCH_DATA,
                    branchId = snap.clock?.branchId,
                    dayId = snap.clock?.branchDayId,
                )
            },
        )
    val canEdit: StateFlow<Boolean> = canEditState.asStateFlow()

    // #425 — correction affordance mirrors the backend's Coordinator authority check. A
    // missing branch context fails closed through hasCapability's null-context behavior.
    internal val canCorrectStatusState =
        MutableStateFlow(
            SessionState.snapshot.value.let { snap ->
                snap.capabilities.hasCapability(
                    CapabilityCodes.EDIT_PAST_DAY,
                    CapabilityContextType.BRANCH,
                    snap.clock?.branchId,
                )
            },
        )
    val canCorrectStatus: StateFlow<Boolean> = canCorrectStatusState.asStateFlow()

    internal val currentEditState = MutableStateFlow<DashboardEditState?>(null)
    val editState: StateFlow<DashboardEditState?> = currentEditState.asStateFlow()

    // #403 — today's effective day status at the branch (the same evaluateStatus shape the
    // backend's write gate applies). Null = unknown: mutation affordances fail closed. A failed
    // read clears the value so mutation affordances fail closed until a fresh state lands.
    internal val dayStatusState = MutableStateFlow<DayStatus?>(null)
    val dayStatus: StateFlow<DayStatus?> = dayStatusState.asStateFlow()

    // #403 — only the newest day read commits (a superseded OPEN landing must never
    // overwrite a fresh REMITTED one).
    internal var dayGeneration = 0L
    internal var dayStatusBranchId: String? =
        SessionState.snapshot.value.clock
            ?.branchId
    internal var capabilityContext = SessionState.snapshot.value.let { it.clock?.branchId to it.clock?.branchDayId }
    internal var capabilitySnapshot = SessionState.snapshot.value.capabilities
    internal var locallyRevokedEditContext: Pair<String?, String?>? = null
    internal var locallyRevokedCorrectionBranch: String? = null

    private var consecutiveFailures = 0
    private var pollJob: Job? = null
    internal var capabilityJob: Job? = null
    internal var editGeneration = 0L

    // #382 — set synchronously when refresh() is called while a landing is in flight; drained
    // (once) by that landing's invokeOnCompletion.
    @Volatile
    private var pendingRefresh = false

    init {
        observeCapabilities()
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
        observeCapabilities()
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
        capabilityJob?.cancel()
        capabilityJob = null
    }

    fun refresh(): Job {
        val branchId =
            SessionState.snapshot.value.clock
                ?.branchId
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
        loadDayStatus()
        _dashboardState.value = UiState.Loading
        return launchReload(branchId)
    }

    /**
     * #382 — authoritative reload after a session mutation on the detail pane. Unlike
     * [refresh], a landing already in flight must not swallow it: the committed change has
     * to repaint promptly, so exactly one follow-up reload is queued and drained by the
     * active landing's completion — skipped on terminal legs (Idle = 401/403 paused the
     * surface; a dead session must not be re-fetched by the queue).
     */
    fun refreshAfterMutation(): Job {
        val branchId =
            SessionState.snapshot.value.clock
                ?.branchId ?: return Job().also { it.cancel() }
        if (_dashboardState.value is UiState.Loading) {
            pendingRefresh = true
            return Job().also { it.cancel() }
        }
        loadDayStatus()
        _dashboardState.value = UiState.Loading
        return launchReload(branchId)
    }

    private fun launchReload(branchId: String): Job =
        handler
            .launch(
                LaunchRequest(
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
                            lastDataCache.value =
                                data.copy(
                                    sessions = mergeDashboardRows(lastDataCache.value?.sessions, data.sessions),
                                )
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
                ),
            ).also { job ->
                // #382 — drain exactly one queued post-mutation reload when the active
                // landing completes; skipped on terminal legs (Idle = 401/403 paused the
                // surface — a dead session must not be re-polled by the queue).
                job.invokeOnCompletion {
                    if (pendingRefresh && _dashboardState.value is UiState.Success) {
                        pendingRefresh = false
                        refreshAfterMutation()
                    }
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
    }
}
