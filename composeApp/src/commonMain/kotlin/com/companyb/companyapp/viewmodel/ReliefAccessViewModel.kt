package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
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

/**
 * Relief access flow (#351) — both BR surfaces in one VM:
 *
 * - **Target** (a checked-in user others asked for): [loadRequests] returns PENDING rows
 *   targeting the caller; Grant/Deny act on those and reload.
 * - **Requester** (a relief user): the same list carries their outgoing requests' outcome
 *   (PENDING/GRANTED/DENIED); [requestAccess] creates one, [loadCandidates] feeds the
 *   checked-in-user picker.
 *
 * The server scopes every row to caller involvement (target or requester), so one GET
 * serves both surfaces. The VM is entry-scoped at its call site (the #112 self-cleaning
 * pattern).
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

    private val _candidates = MutableStateFlow<UiState<List<BranchDayUserResponse>>>(UiState.Idle)
    val candidates: StateFlow<UiState<List<BranchDayUserResponse>>> = _candidates.asStateFlow()

    private val _requestResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val requestResult: StateFlow<UiState<Unit>> = _requestResult.asStateFlow()

    private val _grantResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val grantResult: StateFlow<UiState<Unit>> = _grantResult.asStateFlow()

    private val _denyResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val denyResult: StateFlow<UiState<Unit>> = _denyResult.asStateFlow()

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
                currentRequests() ?: emptyList()
            },
        )

    fun loadCandidates(branchDayId: String): Job =
        handler.launch(
            state = _candidates,
            operation = "loadCandidates",
            endpoint = "GET /api/relief-access/candidates?branchDayId=$branchDayId",
            block = { apiClient.httpClient.get(ApiRoutes.reliefAccessCandidates(branchDayId)) },
            transform = { it.body() },
        )

    fun requestAccess(
        request: ReliefAccessRequest,
        branchDayId: String,
    ): Job =
        handler.launch(
            state = _requestResult,
            operation = "requestAccess",
            endpoint = "POST ${ApiRoutes.RELIEF_ACCESS_REQUEST}",
            block = {
                apiClient.httpClient.post(ApiRoutes.RELIEF_ACCESS_REQUEST) {
                    setBody(request)
                }
            },
            transform = {
                actionStamp++
                refreshRequests(branchDayId)
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
            endpoint = "PATCH ${ApiRoutes.reliefAccessRequest(requestId)}/grant",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessRequest(requestId) + "/grant") },
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
            endpoint = "PATCH ${ApiRoutes.reliefAccessRequest(requestId)}/deny",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessRequest(requestId) + "/deny") },
            transform = {
                actionStamp++
                refreshRequests(branchDayId)
                Unit
            },
        )

    fun resetActionStates() {
        _requestResult.value = UiState.Idle
        _grantResult.value = UiState.Idle
        _denyResult.value = UiState.Idle
    }

    private fun currentRequests(): List<ReliefAccessResponse>? = keptRequests.freshestValue()
}
