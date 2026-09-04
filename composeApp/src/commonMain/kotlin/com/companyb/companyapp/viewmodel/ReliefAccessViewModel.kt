package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Relief access flow (#357 broadcast model):
 *
 * - **Member** (assigned at the branch): [loadRequests] returns every pending/decided
 *   request on the day; Grant/Deny act on them and reload.
 * - **Requester** (outsider): [requestAccess] raises one branch-day-scoped ask (today or
 *   a future date); [loadMine] carries their outcome view across branches; [cancel]
 *   withdraws while the day has not started.
 *
 * Entry-scoped at each call site (the #112 self-cleaning pattern).
 */
class ReliefAccessViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReliefAccessVM")

    // keep-last (the #143 shape): the freshest request list survives Loading/Error so both
    // sections keep rendering across reloads triggered by the actions below.
    private val keptRequests = KeepLast<List<ReliefAccessResponse>>(viewModelScope)
    val requests: StateFlow<UiState<List<ReliefAccessResponse>>> = keptRequests.state
    val freshestRequests: StateFlow<List<ReliefAccessResponse>?> = keptRequests.freshest

    private val keptMine = KeepLast<List<ReliefAccessResponse>>(viewModelScope)
    val mine: StateFlow<UiState<List<ReliefAccessResponse>>> = keptMine.state
    val freshestMine: StateFlow<List<ReliefAccessResponse>?> = keptMine.freshest

    private val _branchOptions = MutableStateFlow<UiState<List<ReliefBranchOptionResponse>>>(UiState.Idle)
    val branchOptions: StateFlow<UiState<List<ReliefBranchOptionResponse>>> = _branchOptions.asStateFlow()

    private val _requestResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val requestResult: StateFlow<UiState<Unit>> = _requestResult.asStateFlow()

    private val _grantResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val grantResult: StateFlow<UiState<Unit>> = _grantResult.asStateFlow()

    private val _denyResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val denyResult: StateFlow<UiState<Unit>> = _denyResult.asStateFlow()

    private val _cancelResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val cancelResult: StateFlow<UiState<Unit>> = _cancelResult.asStateFlow()

    // Bumped on every successful action: a load landing with a mismatched stamp predates the
    // action and must not commit its pre-action snapshot (the #141/#165 stamp pattern).
    private var actionStamp = 0L

    fun loadRequests(branchDayId: String): Job {
        if (keptRequests.stateFlow.value is UiState.Loading) return Job()
        return refreshRequests(branchDayId)
    }

    /**
     * Unconditional reload for action follow-ups (and the #165 fallback): an action landing
     * while a load was in flight must converge server truth even though the surface is
     * Loading — the public [loadRequests] guard would swallow it (the loadSent precedent).
     */
    private fun refreshRequests(branchDayId: String): Job =
        handler.launch(
            LaunchRequest(
                state = keptRequests.stateFlow,
                operation = "loadRequests",
                endpoint = "GET /api/relief-access?branchDayId=$branchDayId",
                block = { apiClient.httpClient.get(ApiRoutes.reliefAccessList(branchDayId)) },
                transform = { it.body<List<ReliefAccessResponse>>() },
                // #165 stale-substitution guard: an action landing while a load was in flight
                // must not commit its pre-action snapshot — substitute the freshest mirror and
                // re-issue so server truth converges.
                stamp = { actionStamp },
                fallback = {
                    refreshRequests(branchDayId)
                    keptRequests.freshestValue() ?: emptyList()
                },
            ),
        )

    /** The caller's own asks across branches/days — the pre-clock-in outcome view (#357). */
    fun loadMine(): Job =
        handler.launch(
            LaunchRequest(
                state = keptMine.stateFlow,
                operation = "loadMine",
                endpoint = "GET ${ApiRoutes.RELIEF_ACCESS_MINE}",
                block = { apiClient.httpClient.get(ApiRoutes.RELIEF_ACCESS_MINE) },
                transform = { it.body<List<ReliefAccessResponse>>() },
                stamp = { actionStamp },
                fallback = {
                    loadMine()
                    keptMine.freshestValue() ?: emptyList()
                },
            ),
        )

    fun loadBranchOptions(): Job =
        handler.launch(
            state = _branchOptions,
            operation = "loadBranchOptions",
            endpoint = "GET ${ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS}",
            block = { apiClient.httpClient.get(ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS) },
            transform = { it.body() },
        )

    fun requestAccess(
        branchId: String,
        date: String?,
    ): Job =
        handler.launch(
            state = _requestResult,
            operation = "requestAccess",
            endpoint = "POST ${ApiRoutes.RELIEF_ACCESS_REQUEST}",
            block = {
                apiClient.httpClient.post(ApiRoutes.RELIEF_ACCESS_REQUEST) {
                    setBody(ReliefAccessRequest(requestId = newRequestId(), branchId = branchId, date = date))
                }
            },
            transform = {
                actionStamp++
                loadMine()
                Unit
            },
        )

    fun grantAccess(
        requestId: String,
        branchDayId: String,
    ): Job =
        handler.launch(
            state = _grantResult,
            operation = "grantAccess",
            endpoint = "PATCH ${ApiRoutes.reliefAccessGrant(requestId)}",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessGrant(requestId)) },
            transform = {
                actionStamp++
                refreshRequests(branchDayId)
                Unit
            },
        )

    fun denyAccess(
        requestId: String,
        branchDayId: String,
    ): Job =
        handler.launch(
            state = _denyResult,
            operation = "denyAccess",
            endpoint = "PATCH ${ApiRoutes.reliefAccessDeny(requestId)}",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessDeny(requestId)) },
            transform = {
                actionStamp++
                refreshRequests(branchDayId)
                Unit
            },
        )

    /** Withdraw (requester) or cancel (branch member) a pending ask (#357). */
    fun cancel(
        requestId: String,
        branchDayId: String?,
    ): Job =
        handler.launch(
            state = _cancelResult,
            operation = "cancelRequest",
            endpoint = "PATCH ${ApiRoutes.reliefAccessCancel(requestId)}",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessCancel(requestId)) },
            transform = {
                actionStamp++
                loadMine()
                branchDayId?.let(::refreshRequests)
                Unit
            },
        )

    fun resetActionStates() {
        _requestResult.value = UiState.Idle
        _grantResult.value = UiState.Idle
        _denyResult.value = UiState.Idle
        _cancelResult.value = UiState.Idle
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newRequestId(): String = Uuid.random().toString()
}
