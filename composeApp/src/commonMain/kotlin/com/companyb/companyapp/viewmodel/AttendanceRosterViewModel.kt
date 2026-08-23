package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.MarkAttendanceRequest
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #404 — member-marked attendance on the dashboard: the branch's home-member roster with
 * live presence flags, and Present/Absent marks that mirror the backend's clock-window
 * semantics (present = clock-in on behalf, absent = close the open window).
 *
 * Entry-scoped at each call site (the #112 self-cleaning pattern). The roster read is
 * membership-gated server-side: a 403 lands as [roster] Error and the section renders
 * nothing (the #149/#113 silent-exit shape — no affordance for unauthorized callers).
 */
class AttendanceRosterViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AttendanceVM")

    // keep-last (#143): the freshest roster survives Loading/Error frames across mark reloads.
    private val keptRoster = KeepLast<List<MemberAttendanceResponse>>(viewModelScope)
    val roster: StateFlow<UiState<List<MemberAttendanceResponse>>> = keptRoster.state
    val freshestRoster: StateFlow<List<MemberAttendanceResponse>?> = keptRoster.freshest

    private val _markResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val markResult: StateFlow<UiState<Unit>> = _markResult.asStateFlow()

    // Bumped on every successful mark: a roster reload landing with a mismatched stamp
    // predates the mark and must not commit its pre-action snapshot (the #165 stamp shape).
    private var actionStamp = 0L

    fun load(branchId: String): Job {
        if (keptRoster.stateFlow.value is UiState.Loading) return Job()
        return refreshRoster(branchId)
    }

    /**
     * Unconditional reload for mark follow-ups: a mark landing while a load was in flight must
     * converge server truth even though the public [load] guard would swallow it.
     */
    private fun refreshRoster(branchId: String): Job =
        handler.launch(
            state = keptRoster.stateFlow,
            operation = "loadRoster",
            endpoint = "GET /api/branches/{branchId}/attendance/today",
            block = { apiClient.httpClient.get(ApiRoutes.branchAttendanceToday(branchId)) },
            transform = { it.body() },
            stamp = { actionStamp },
            fallback = {
                refreshRoster(branchId)
                keptRoster.freshestValue() ?: emptyList()
            },
        )

    fun mark(
        branchId: String,
        targetUserId: String,
        present: Boolean,
    ): Job =
        handler.launch(
            state = _markResult,
            operation = "markAttendance",
            endpoint = "POST /api/branches/{branchId}/attendance/marks",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchAttendanceMarks(branchId)) {
                    setBody(
                        MarkAttendanceRequest(
                            userId = targetUserId,
                            present = present,
                            // The idempotency key rides present-marks only; absent-marks close
                            // the open window and need no id.
                            attendanceId = if (present) newAttendanceId() else null,
                        ),
                    )
                }
            },
            transform = {
                actionStamp++
                refreshRoster(branchId)
                Unit
            },
        )

    @OptIn(ExperimentalUuidApi::class)
    private fun newAttendanceId(): String = Uuid.random().toString()
}
