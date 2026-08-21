package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.AddSessionConcernRequest
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

    private val _practitioners = MutableStateFlow<UiState<List<SessionPractitionerResponse>>>(UiState.Idle)
    val practitioners: StateFlow<UiState<List<SessionPractitionerResponse>>> = _practitioners.asStateFlow()

    private val _practitionerResult = MutableStateFlow<UiState<SessionPractitionerResponse>>(UiState.Idle)
    val practitionerResult: StateFlow<UiState<SessionPractitionerResponse>> = _practitionerResult.asStateFlow()

    private val _concerns = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val concerns: StateFlow<UiState<List<ConcernResponse>>> = _concerns.asStateFlow()

    private val _sessionConcerns = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val sessionConcerns: StateFlow<UiState<List<ConcernResponse>>> = _sessionConcerns.asStateFlow()

    private val _concernResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val concernResult: StateFlow<UiState<Unit>> = _concernResult.asStateFlow()

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

    fun loadSessionPractitioners(sessionId: String) {
        handler.launch(
            state = _practitioners,
            operation = "loadSessionPractitioners",
            endpoint = "GET /api/sessions/$sessionId/practitioners",
            block = { apiClient.httpClient.get(ApiRoutes.sessionPractitioners(sessionId)) },
            transform = { it.body() },
        )
    }

    fun addPractitioner(
        sessionId: String,
        request: AddPractitionerRequest,
    ) {
        handler.launch(
            state = _practitionerResult,
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
            state = _practitionerResult,
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
            state = _practitionerResult,
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

    fun loadAllConcerns() {
        handler.launch(
            state = _concerns,
            operation = "loadAllConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    fun loadSessionConcerns(sessionId: String) {
        handler.launch(
            state = _sessionConcerns,
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
            state = _concernResult,
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
            state = _concernResult,
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
            state = _concernResult,
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
}
