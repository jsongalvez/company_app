package com.companyb.companyapp.session.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LatestLoad
import com.companyb.companyapp.async.LoadGeneration
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.session.AddPractitionerRequest
import com.companyb.companyapp.contracts.session.PromoteConcernRequest
import com.companyb.companyapp.contracts.session.SessionPractitionerResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionVoidResponse
import com.companyb.companyapp.contracts.session.UnvoidSessionRequest
import com.companyb.companyapp.contracts.session.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.contracts.session.VoidSessionRequest
import com.companyb.companyapp.contracts.workforce.BranchMemberResponse
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
 * concern mutations, and every pane mutation; the authoritative row arrives via the pane
 * param and reloads pessimistically after each landing (ADR-0022).
 *
 * #479's [SessionConcernOps]/[SessionRosterOps] extension files fold back here as private
 * state + member functions per #535: no `internal` members exist only to serve extensions.
 */
class SessionViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionVM")

    private val _voidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val voidResult: StateFlow<UiState<SessionVoidResponse>> = _voidResult.asStateFlow()

    private val _unvoidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val unvoidResult: StateFlow<UiState<SessionVoidResponse>> = _unvoidResult.asStateFlow()

    private val practitionersState = MutableStateFlow<UiState<List<SessionPractitionerResponse>>>(UiState.Idle)
    val practitioners: StateFlow<UiState<List<SessionPractitionerResponse>>> = practitionersState.asStateFlow()

    private val practitionerResultState = MutableStateFlow<UiState<SessionPractitionerResponse>>(UiState.Idle)
    val practitionerResult: StateFlow<UiState<SessionPractitionerResponse>> = practitionerResultState.asStateFlow()

    private val concernResultState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val concernResult: StateFlow<UiState<Unit>> = concernResultState.asStateFlow()

    // #382 — post-create practitioner adds need the branch member directory (the same read
    // SessionCreate uses); loaded lazily when the picker dialog opens.
    // #382 — latest-wins guards for the roster/members loaders (#611 LoadGeneration owners:
    // one VM serves one selection since #486; the guards cover same-scope races).
    private val rosterGuard = LoadGeneration()
    private val membersGuard = LoadGeneration()

    private val branchMembersState = MutableStateFlow<UiState<List<BranchMemberResponse>>>(UiState.Idle)
    val branchMembers: StateFlow<UiState<List<BranchMemberResponse>>> = branchMembersState.asStateFlow()

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

    // #557 — folded from SessionConcernOps (#479): the session-concern mutations live
    // with their state owner; no extension file exists only to satisfy a function budget.
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
        // #382 — latest-wins (#611): a superseded roster GET commits nothing — a stale body is
        // never deserialized and the committed value stands (no fallback data is invented).
        handler.launchLatest(
            LatestLoad(
                state = practitionersState,
                operation = "loadSessionPractitioners",
                endpoint = "GET /api/sessions/$sessionId/practitioners",
                block = { apiClient.httpClient.get(ApiRoutes.sessionPractitioners(sessionId)) },
                decode = { it.body() },
                guard = rosterGuard,
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
        // Same latest-wins discipline as the roster: only the newest branch request commits.
        handler.launchLatest(
            LatestLoad(
                state = branchMembersState,
                operation = "loadBranchMembers",
                endpoint = "GET /api/branches/$branchId/members",
                block = { apiClient.httpClient.get(ApiRoutes.branchMembers(branchId)) },
                decode = { it.body() },
                guard = membersGuard,
            ),
        )
    }

    /** #382 — one-shot drain: the pane handles a terminal practitioner landing exactly once. */
    fun consumePractitionerResult() {
        practitionerResultState.value = UiState.Idle
    }
}
