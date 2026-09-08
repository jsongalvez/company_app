package com.companyb.companyapp.session.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LatestLoad
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.LoadGeneration
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branchday.BranchDayTodayResponse
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.AddPractitionerRequest
import com.companyb.companyapp.contracts.session.PromoteConcernRequest
import com.companyb.companyapp.contracts.session.SessionPractitionerResponse
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionVoidResponse
import com.companyb.companyapp.contracts.session.UnvoidSessionRequest
import com.companyb.companyapp.contracts.session.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.contracts.session.UpdateSessionStatusRequest
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

    // #675 — the detail status transition (PATCH .../status, same body the dashboard cell
    // commits): one-shot result plus a dedicated 409 flag. A version conflict is handled,
    // not generic-error surfaced — the pane explains "This session changed" with Reload.
    private val statusResultState = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val statusResult: StateFlow<UiState<SessionResponse>> = statusResultState.asStateFlow()

    private val statusConflictState = MutableStateFlow(false)
    val statusConflict: StateFlow<Boolean> = statusConflictState.asStateFlow()

    // #675 — the day status behind the detail action model (same BranchDayTodayResponse
    // read the dashboard uses; failure fails closed to null via the non-Success legs).
    private val dayStatusState = MutableStateFlow<UiState<DayStatus>>(UiState.Idle)
    val dayStatus: StateFlow<UiState<DayStatus>> = dayStatusState.asStateFlow()

    // #382 — post-create practitioner adds need the branch member directory (the same read
    // SessionCreate uses); loaded lazily when the picker dialog opens.
    // #382 — latest-wins guards for the roster/members loaders (#611 LoadGeneration owners:
    // one VM serves one selection since #486; the guards cover same-scope races).
    private val rosterGuard = LoadGeneration()
    private val membersGuard = LoadGeneration()
    private val dayGuard = LoadGeneration()

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
        // #675 — double-click guard held from the caller's frame (the #135 pattern): the
        // handler pre-sets Loading only inside its coroutine, so two back-to-back taps
        // would both dispatch. Membership adds are server-idempotent, but the second tap
        // must not even send.
        if (practitionerResultState.value is UiState.Loading) return
        practitionerResultState.value = UiState.Loading
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

    /**
     * #675 — the detail status transition (completion, no-show/cancel, corrections): the
     * same PATCH + optimistic-version body the desktop status cell commits, so both paths
     * share the backend's transition/correction/day rules. Ordinary completion gets no
     * extra confirmation at the call site; REMITTED-day reason collection lives in the
     * pane. The double-dispatch guard holds from the caller's frame (same #135 pattern
     * as [addPractitioner]): a second tap reuses the stale version and would 409 against
     * the just-committed row.
     */
    fun updateSessionStatus(
        sessionId: String,
        request: UpdateSessionStatusRequest,
    ) {
        if (statusResultState.value is UiState.Loading) return
        statusConflictState.value = false
        statusResultState.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = statusResultState,
                operation = "updateSessionStatus",
                endpoint = "PATCH /api/sessions/$sessionId/status",
                block = {
                    apiClient.httpClient.patch(ApiRoutes.sessionStatus(sessionId)) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
                // A 409 is stale-version proof, not a generic failure: the pane keeps the
                // conflict banner + Reload path instead of an error line. The handled leg
                // resets to Idle itself — the handler's Loading pre-set would otherwise
                // wedge the action bar's mutating flag with no terminal ever landing.
                onNonSuccess = { response ->
                    if (response.status.value == STATUS_CONFLICT) {
                        statusResultState.value = UiState.Idle
                        statusConflictState.value = true
                        true
                    } else {
                        false
                    }
                },
            ),
        )
    }

    /** #675 — one-shot drain for status landings; the conflict flag has its own clear. */
    fun consumeStatusResult() {
        statusResultState.value = UiState.Idle
    }

    fun clearStatusConflict() {
        statusConflictState.value = false
    }

    /**
     * #675 — the day-status read behind the detail action model. Latest-wins like the
     * roster (same-scope races must not commit a superseded OPEN over a fresh REMITTED);
     * any non-Success leg reads as unknown at the pane, failing the model closed.
     */
    fun loadDayStatus(branchId: String) {
        handler.launchLatest(
            LatestLoad(
                state = dayStatusState,
                operation = "loadDayStatus",
                endpoint = "GET /api/branches/$branchId/today",
                block = { apiClient.httpClient.get(ApiRoutes.branchToday(branchId)) },
                decode = { it.body<BranchDayTodayResponse>().status },
                guard = dayGuard,
            ),
        )
    }

    private companion object {
        private const val STATUS_CONFLICT = 409
    }
}
