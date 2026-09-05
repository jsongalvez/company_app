package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.BranchMemberResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionViewModel(
    internal val apiClient: ApiClient,
) : ViewModel() {
    internal val handler = ApiCallHandler(viewModelScope, "SessionVM")

    private val _sessionResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val sessionResult: StateFlow<UiState<SessionResponse>> = _sessionResult.asStateFlow()

    private val _statusUpdateResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val statusUpdateResult: StateFlow<UiState<SessionResponse>> = _statusUpdateResult.asStateFlow()

    private val _voidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val voidResult: StateFlow<UiState<SessionVoidResponse>> = _voidResult.asStateFlow()

    private val _unvoidResult = MutableStateFlow<UiState<SessionVoidResponse>>(UiState.Idle)
    val unvoidResult: StateFlow<UiState<SessionVoidResponse>> = _unvoidResult.asStateFlow()

    internal val practitionersState = MutableStateFlow<UiState<List<SessionPractitionerResponse>>>(UiState.Idle)
    val practitioners: StateFlow<UiState<List<SessionPractitionerResponse>>> = practitionersState.asStateFlow()

    internal val practitionerResultState = MutableStateFlow<UiState<SessionPractitionerResponse>>(UiState.Idle)
    val practitionerResult: StateFlow<UiState<SessionPractitionerResponse>> = practitionerResultState.asStateFlow()

    internal val concernsState = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val concerns: StateFlow<UiState<List<ConcernResponse>>> = concernsState.asStateFlow()

    internal val sessionConcernsState = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val sessionConcerns: StateFlow<UiState<List<ConcernResponse>>> = sessionConcernsState.asStateFlow()

    internal val concernResultState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val concernResult: StateFlow<UiState<Unit>> = concernResultState.asStateFlow()

    // #382 — post-create practitioner adds need the branch member directory (the same read
    // SessionCreate uses); loaded lazily when the picker dialog opens.
    // #382 — generation counters for the superseded-landing guards in the roster/members
    // loaders (one VM serves one selection since #486; the guards cover same-scope races).
    internal var rosterGeneration = 0L
    internal var membersGeneration = 0L

    internal val branchMembersState = MutableStateFlow<UiState<List<BranchMemberResponse>>>(UiState.Idle)
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
}
