package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * BranchSelect surface (#94-grad build): the caller's branches with per-branch clock-in
 * status (GET /api/me/branches, #98) + the clock-in flow (Phase 3 of the #94 outline).
 *
 * Clock-in chains the ADR-0021 second trigger: POST /api/attendance/clock-in (via
 * success → SessionState.
 * setSelectedBranch (branch-scoped capability resolution becomes meaningful — #156:
 * the full row list is stored; the clock-in refetch keeps it fresh) → GET
 * /api/me/capabilities refresh. The screen holds on BranchSelect while EITHER is in flight
 * and navigates to Dashboard only when the refresh succeeds (#94 Q2 principle: never
 * navigate to a screen whose backing state isn't ready).
 *
 * Retry semantics per phase: a failed clock-in re-runs clock-in; a failed refresh re-runs
 * refresh (the clock-in itself already succeeded — re-POSTing would conflict with
 * ShiftGuard's single active clock-in).
 */
@OptIn(ExperimentalUuidApi::class)
class BranchSelectViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "BranchSelectVM")

    private val _branches = MutableStateFlow<UiState<List<MeBranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<MeBranchResponse>>> = _branches.asStateFlow()

    private val _clockInState = MutableStateFlow<UiState<ClockInResponse>>(UiState.Idle)
    val clockInState: StateFlow<UiState<ClockInResponse>> = _clockInState.asStateFlow()
    private var clockInFlowActive = false

    private val _refreshState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val refreshState: StateFlow<UiState<Unit>> = _refreshState.asStateFlow()

    fun loadBranches() {
        handler.launch(
            state = _branches,
            operation = "loadBranches",
            endpoint = "GET /api/me/branches",
            block = { apiClient.httpClient.get(ApiRoutes.ME_BRANCHES) },
            transform = { it.body() },
        )
    }

    fun clockIn(branch: MeBranchResponse): Job {
        if (clockInFlowActive) return Job().apply { complete() }
        clockInFlowActive = true
        val request =
            ClockInRequest(
                attendanceId = Uuid.random().toString(),
                branchId = branch.branchId,
            )
        _clockInState.value = UiState.Loading
        val clockInJob =
            handler.launch(
                state = _clockInState,
                operation = "clockIn",
                endpoint = "POST /api/attendance/clock-in",
                block = {
                    apiClient.httpClient.post(ApiRoutes.ATTENDANCE_CLOCK_IN) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
            )
        // Chain the second trigger (ADR-0021): only after the clock-in succeeded.
        return viewModelScope.launch {
            try {
                clockInJob.join()
                val clockInState = _clockInState.value
                if (clockInState is UiState.Success) {
                    SessionState.setSelectedBranch(branch.branchId, branch.branchName)
                    // #147 — persist the clock-state slots (attendance id + branchDayId) at
                    // clock-in: the drawer's clock-out request sources the attendance id here.
                    SessionState.setClockState(clockInState.data.id, clockInState.data.branchDayId)
                    refreshCapabilities().join()
                }
            } finally {
                clockInFlowActive = false
            }
        }
    }

    fun refreshCapabilities(): Job {
        if (_refreshState.value is UiState.Loading) return Job().apply { complete() }
        // Synchronous pre-set: the guard must hold from the caller's frame (a double-tap
        // before any dispatch would otherwise launch two refreshes).
        _refreshState.value = UiState.Loading
        return handler.launch(
            state = _refreshState,
            operation = "refreshCapabilities",
            endpoint = "GET /api/me/capabilities",
            block = { apiClient.httpClient.get(ApiRoutes.ME_CAPABILITIES) },
            transform = {
                // #156 — the full row list is stored (the client-side branch slice filter
                // is gone; resolution happens at consumption sites).
                SessionState.setCapabilities(it.body())
                Unit
            },
        )
    }
}
