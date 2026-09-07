package com.companyb.companyapp.session.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.PromoteConcernRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #557 — selection-scoped session-detail mutation owner (one VM per selection, keyed by
 * session.id per #486; a revisit reuses the cached keyed VM per #489).
 *
 * Distinct lifetime from entry-scoped [SessionDetailViewModel] (the mobile pushed route's
 * one-shot bearer fetch): this owner holds the roster (`GET .../practitioners`), the
 * concern collection, and every pane mutation; the authoritative row arrives via the pane
 * param and reloads pessimistically after each landing (ADR-0022).
 *
 * #479's [SessionConcernOps]/[SessionRosterOps] extension files fold back here as private
 * state + member functions per #535: no `internal` members exist only to serve extensions.
 * `createSession`/`updateStatus` stay until #530's semantic dead-code pass retires them —
 * grep alone never proves death.
 */
class SessionViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionVM")

    private val _sessionResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val sessionResult: StateFlow<UiState<SessionResponse>> = _sessionResult.asStateFlow()

    private val _statusUpdateResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val statusUpdateResult: StateFlow<UiState<SessionResponse>> = _statusUpdateResult.asStateFlow()

    private val _voidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val voidResult: StateFlow<UiState<SessionVoidResponse>> = _voidResult.asStateFlow()

    private val _unvoidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val unvoidResult: StateFlow<UiState<SessionVoidResponse>> = _unvoidResult.asStateFlow()

    private val practitionersState = MutableStateFlow<UiState<List<SessionPractitionerResponse>>>(UiState.Idle)
    val practitioners: StateFlow<UiState<List<SessionPractitionerResponse>>> = practitionersState.asStateFlow()

    private val practitionerResultState = MutableStateFlow<UiState<SessionPractitionerResponse>>(UiState.Idle)
    val practitionerResult: StateFlow<UiState<SessionPractitionerResponse>> = practitionerResultState.asStateFlow()

    private val concernsState = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val concerns: StateFlow<UiState<List<ConcernResponse>>> = concernsState.asStateFlow()

    private val sessionConcernsState = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val sessionConcerns: StateFlow<UiState<List<ConcernResponse>>> = sessionConcernsState.asStateFlow()

    private val concernResultState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val concernResult: StateFlow<UiState<Unit>> = concernResultState.asStateFlow()

    // #382 — post-create practitioner adds need the branch member directory (the same read
    // SessionCreate uses); loaded lazily when the picker dialog opens.
    // #382 — generation counters for the superseded-landing guards in the roster/members
    // loaders (one VM serves one selection since #486; the guards cover same-scope races).
    private var rosterGeneration = 0L
    private var membersGeneration = 0L

    private val branchMembersState = MutableStateFlow<UiState<List<BranchMemberResponse>>>(UiState.Idle)
    val branchMembers: StateFlow<UiState<List<BranchMemberResponse>>> = branchMembersState.asStateFlow()

    fun createSession(request: CreateSessionRequest) {
        handler.launch(
            state = _sessionResult,
            operation = "createSession",
            endpoint = "POST /api/sessions",
            block = {
                apiClient.httpClient.post(ApiRoutes.SESSIONS) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun updateStatus(
        sessionId: String,
        request: UpdateSessionStatusRequest,
    ) {
        handler.launch(
            state = _statusUpdateResult,
            operation = "updateStatus",
            endpoint = "PATCH /api/sessions/$sessionId/status",
            block = {
                apiClient.httpClient.patch(ApiRoutes.sessionStatus(sessionId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun voidSession(
        sessionId: String,
        request: VoidSessionRequest,
    ) {
        handler.launch(
            state = _voidResult,
            operation = "voidSession",
            endpoint = "POST /api/sessions/$sessionId/void",
            block = {
                apiClient.httpClient.post(ApiRoutes.sessionVoid(sessionId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun unvoidSession(
        sessionId: String,
        request: UnvoidSessionRequest,
    ) {
        handler.launch(
            state = _unvoidResult,
            operation = "unvoidSession",
            endpoint = "POST /api/sessions/$sessionId/unvoid",
            block = {
                apiClient.httpClient.post(ApiRoutes.sessionUnvoid(sessionId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    /** #406 — one-shot drains for the void/unvoid command landings. */
    fun consumeVoidResult() {
        _voidResult.value = UiState.Idle
    }

    fun consumeUnvoidResult() {
        _unvoidResult.value = UiState.Idle
    }

    // #557 — folded from SessionConcernOps (#479): the session-concern collection lives with
    // its state owner; no extension file exists only to satisfy a function budget.
    fun loadAllConcerns() {
        handler.launch(
            state = concernsState,
            operation = "loadAllConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    fun loadSessionConcerns(sessionId: String) {
        handler.launch(
            state = sessionConcernsState,
            operation = "loadSessionConcerns",
            endpoint = "GET /api/sessions/$sessionId/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.sessionConcerns(sessionId)) },
            transform = { it.body() },
        )
    }

    fun addSessionConcern(
        sessionId: String,
        request: AddSessionConcernRequest,
    ) {
        handler.launchUnit(
            state = concernResultState,
            operation = "addSessionConcern",
            endpoint = "POST /api/sessions/$sessionId/concerns",
            block = {
                apiClient.httpClient.post(ApiRoutes.sessionConcerns(sessionId)) {
                    setBody(request)
                }
            },
        )
    }

    fun removeSessionConcern(
        sessionId: String,
        concernId: String,
    ) {
        handler.launchUnit(
            state = concernResultState,
            operation = "removeSessionConcern",
            endpoint = "DELETE /api/sessions/$sessionId/concerns/$concernId",
            block = {
                apiClient.httpClient.delete(
                    ApiRoutes.sessionConcern(sessionId, concernId),
                )
            },
        )
    }

    fun promoteConcern(
        sessionId: String,
        request: PromoteConcernRequest,
    ) {
        handler.launchUnit(
            state = concernResultState,
            operation = "promoteConcern",
            endpoint = "POST /api/sessions/$sessionId/promote-concern",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.sessionPromoteConcern(sessionId),
                ) {
                    setBody(request)
                }
            },
        )
    }

    /** #382 — one-shot drain for concern command landings (add/remove/promote). */
    fun consumeConcernResult() {
        concernResultState.value = UiState.Idle
    }

    // #557 — folded from SessionRosterOps (#479): the #382 generation discipline travels with
    // the owner. #486 keys one VM per selection, so a switch starts a fresh scope (a revisit
    // reuses the cached keyed VM); within a scope, rapid successive loads still race, and a
    // superseded roster or member GET must not commit over the newer request (stale body never
    // deserialized — the committed value stands).
    fun loadSessionPractitioners(sessionId: String) {
        // #382 — generation guard: the desktop pane reuses one VM per selection, so a
        // superseded roster GET (a newer load dispatched mid-flight) must not commit over the
        // newer request — a stale body is never deserialized; the committed value stands.
        ++rosterGeneration
        handler.launch(
            LaunchRequest(
                state = practitionersState,
                operation = "loadSessionPractitioners",
                endpoint = "GET /api/sessions/$sessionId/practitioners",
                block = { apiClient.httpClient.get(ApiRoutes.sessionPractitioners(sessionId)) },
                transform = { it.body() },
                stamp = { rosterGeneration },
                fallback = { (practitionersState.value as? UiState.Success)?.data ?: emptyList() },
            ),
        )
    }

    fun addPractitioner(
        sessionId: String,
        request: AddPractitionerRequest,
    ) {
        handler.launch(
            state = practitionerResultState,
            operation = "addPractitioner",
            endpoint = "POST /api/sessions/$sessionId/practitioners",
            block = {
                apiClient.httpClient.post(ApiRoutes.sessionPractitioners(sessionId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun updatePractitionerRemarks(
        sessionId: String,
        practitionerId: String,
        request: UpdatePractitionerRemarksRequest,
    ) {
        handler.launch(
            state = practitionerResultState,
            operation = "updatePractitionerRemarks",
            endpoint = "PATCH /api/sessions/$sessionId/practitioners/$practitionerId",
            block = {
                apiClient.httpClient.patch(
                    ApiRoutes.sessionPractitioner(sessionId, practitionerId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun removePractitioner(
        sessionId: String,
        practitionerId: String,
    ) {
        handler.launch(
            state = practitionerResultState,
            operation = "removePractitioner",
            endpoint = "DELETE /api/sessions/$sessionId/practitioners/$practitionerId",
            block = {
                apiClient.httpClient.delete(
                    ApiRoutes.sessionPractitioner(sessionId, practitionerId),
                )
            },
            transform = {
                SessionPractitionerResponse(
                    id = "",
                    sessionId = sessionId,
                    practitionerId = practitionerId,
                    remarks = null,
                    slotAtTime = 0,
                )
            },
        )
    }

    /** #382 — member directory for the add-practitioner picker at the session's branch. */
    fun loadBranchMembers(branchId: String) {
        if (branchMembersState.value is UiState.Loading) return
        branchMembersState.value = UiState.Loading
        // Same generation discipline as the roster: only the newest branch request commits.
        ++membersGeneration
        handler.launch(
            LaunchRequest(
                state = branchMembersState,
                operation = "loadBranchMembers",
                endpoint = "GET /api/branches/$branchId/members",
                block = { apiClient.httpClient.get(ApiRoutes.branchMembers(branchId)) },
                transform = { it.body() },
                stamp = { membersGeneration },
                fallback = { (branchMembersState.value as? UiState.Success)?.data ?: emptyList() },
            ),
        )
    }

    /** #382 — one-shot drain: the pane handles a terminal practitioner landing exactly once. */
    fun consumePractitionerResult() {
        practitionerResultState.value = UiState.Idle
    }
}
