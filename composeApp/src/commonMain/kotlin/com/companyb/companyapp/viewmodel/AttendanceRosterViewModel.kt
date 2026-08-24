package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.MarkAttendanceRequest
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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

    // #416 — the self-service slot legs (own row only at the surface; each keeps its own
    // UiState so a slot-edit failure never touches the mark or swap flows).
    private val _slotUpdate = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val slotUpdate: StateFlow<UiState<Unit>> = _slotUpdate.asStateFlow()

    private val _swapUpdate = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val swapUpdate: StateFlow<UiState<Unit>> = _swapUpdate.asStateFlow()

    // Bumped on every successful mark: a roster reload landing with a mismatched stamp
    // predates the mark and must not commit its pre-action snapshot (the #165 stamp shape).
    private var actionStamp = 0L

    // #416 — one mutation at a time across all three legs (mark/slot/swap): a second dispatch
    // while one runs could serialize two swaps of the same pair and undo the first. Tracked by
    // the launched Job itself — isActive self-releases on every terminal, including the
    // CancellationException path where handler hooks never run. The follow-up roster reload is
    // covered separately at the surface (its Loading rides [roster]).
    private var mutationJob: Job? = null

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
    ): Job {
        if (mutationJob?.isActive == true) return Job()
        return handler
            .launch(
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
            ).also { mutationJob = it }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newAttendanceId(): String = Uuid.random().toString()

    /**
     * #416 — self slot edit from the dashboard roster (own row only; the backend self-leg
     * authorizes own assignments). Mirrors ProfileViewModel.updateSlot: the server 4xx
     * body's message surfaces inline ([extractApiErrorMessage]), and a 2xx refreshes the
     * roster so the reordered rows show immediately.
     */
    fun updateSlot(
        branchId: String,
        userId: String,
        slot: Short,
    ) {
        if (mutationJob?.isActive == true) return
        handler
            .launch(
                state = _slotUpdate,
                operation = "updateSlot",
                endpoint = "PATCH ${ApiRoutes.branchAssignmentSlot(branchId, userId)}",
                block = {
                    apiClient.httpClient.patch(ApiRoutes.branchAssignmentSlot(branchId, userId)) {
                        setBody(UpdateSlotRequest(slot))
                    }
                },
                transform = {
                    actionStamp++
                    refreshRoster(branchId)
                    Unit
                },
                onNonSuccess = { response ->
                    val message =
                        extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                            ?: "Slot update failed: ${response.status.value}"
                    _slotUpdate.value = UiState.Error(message)
                    true
                },
            ).also { mutationJob = it }
    }

    /**
     * #416 — swap my slot with another member's (the backend's participant rule authorizes
     * caller-involving swaps without MANAGE_USERS). Success refreshes the roster.
     */
    fun swapSlots(
        branchId: String,
        userIdA: String,
        userIdB: String,
    ) {
        if (mutationJob?.isActive == true) return
        handler
            .launch(
                state = _swapUpdate,
                operation = "swapSlots",
                endpoint = "POST ${ApiRoutes.branchSlotsSwap(branchId)}",
                block = {
                    apiClient.httpClient.post(ApiRoutes.branchSlotsSwap(branchId)) {
                        setBody(SwapSlotsRequest(userIdA, userIdB))
                    }
                },
                transform = {
                    actionStamp++
                    refreshRoster(branchId)
                    Unit
                },
                onNonSuccess = { response ->
                    val message =
                        extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                            ?: "Swap failed: ${response.status.value}"
                    _swapUpdate.value = UiState.Error(message)
                    true
                },
            ).also { mutationJob = it }
    }
}
