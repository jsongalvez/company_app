package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
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

    private var consecutiveFailures = 0
    private var pollJob: Job? = null

    init {
        resume()
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
        // poll loop's join() from hanging — a bare `Job()` never completes.
        if (_dashboardState.value is UiState.Loading) {
            return Job().also { it.cancel() }
        }
        return handler.launch(
            state = _dashboardState,
            operation = "loadDashboard",
            endpoint = "GET /api/branches/$branchId/dashboard/today",
            block = { apiClient.httpClient.get("/api/branches/$branchId/dashboard/today") },
            transform = {
                it.body<DashboardResponse>().also { data ->
                    // Q5a — the failure counter and the timestamp reset only on SUCCESS.
                    consecutiveFailures = 0
                    _pollStatus.value = DashboardPollStatus.FRESH
                    _lastData.value = data
                    _lastUpdatedAt.value = Clock.System.now()
                }
            },
            onNonSuccess = { response ->
                when (response.status.value) {
                    // 401 — session termination: the global ApiClient.onUnauthorized path
                    // handles the redirect; never counts as poll degradation (Q5b). The
                    // handler pre-set Loading, so reset the state or the in-flight guard
                    // wedges every future poll.
                    401 -> {
                        _dashboardState.value = UiState.Idle
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

                    // Generic failure: counted against the stale/error thresholds. NOTE the
                    // counting cannot live in a _dashboardState collector — StateFlow
                    // conflates equal Error values, so consecutive identical errors would
                    // never re-emit (probe-proven).
                    else -> {
                        consecutiveFailures++
                        _pollStatus.value =
                            if (consecutiveFailures >= ERROR_THRESHOLD) {
                                DashboardPollStatus.ERRORED
                            } else if (consecutiveFailures >= STALE_THRESHOLD) {
                                DashboardPollStatus.STALE
                            } else {
                                DashboardPollStatus.FRESH
                            }
                        false
                    }
                }
            },
        )
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
