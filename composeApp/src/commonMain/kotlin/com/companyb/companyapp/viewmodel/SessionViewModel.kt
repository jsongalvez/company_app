package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
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
        logInfo("SessionVM", "createSession called")
        viewModelScope.launch {
            _sessionResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions")
                val response =
                    apiClient.httpClient.post("/api/sessions") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "createSession success")
                    _sessionResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "createSession failed: status=${response.status.value}")
                    _sessionResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "createSession exception", e)
                _sessionResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateStatus(
        sessionId: String,
        request: UpdateSessionStatusRequest,
    ) {
        logInfo("SessionVM", "updateStatus called: sessionId=$sessionId")
        viewModelScope.launch {
            _statusUpdateResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "PATCH /api/sessions/$sessionId/status")
                val response =
                    apiClient.httpClient.patch("/api/sessions/$sessionId/status") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "updateStatus success")
                    _statusUpdateResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "updateStatus failed: status=${response.status.value}")
                    _statusUpdateResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "updateStatus exception", e)
                _statusUpdateResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun voidSession(
        sessionId: String,
        request: VoidSessionRequest,
    ) {
        logInfo("SessionVM", "voidSession called: sessionId=$sessionId")
        viewModelScope.launch {
            _voidResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions/$sessionId/void")
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/void") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "voidSession success")
                    _voidResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "voidSession failed: status=${response.status.value}")
                    _voidResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "voidSession exception", e)
                _voidResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun unvoidSession(
        sessionId: String,
        request: UnvoidSessionRequest,
    ) {
        logInfo("SessionVM", "unvoidSession called: sessionId=$sessionId")
        viewModelScope.launch {
            _unvoidResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions/$sessionId/unvoid")
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/unvoid") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "unvoidSession success")
                    _unvoidResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "unvoidSession failed: status=${response.status.value}")
                    _unvoidResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "unvoidSession exception", e)
                _unvoidResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSessionPractitioners(sessionId: String) {
        logInfo("SessionVM", "loadSessionPractitioners called: sessionId=$sessionId")
        viewModelScope.launch {
            _practitioners.value = UiState.Loading
            try {
                logInfo("SessionVM", "GET /api/sessions/$sessionId/practitioners")
                val response = apiClient.httpClient.get("/api/sessions/$sessionId/practitioners")
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "loadSessionPractitioners success")
                    _practitioners.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "loadSessionPractitioners failed: status=${response.status.value}")
                    _practitioners.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "loadSessionPractitioners exception", e)
                _practitioners.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addPractitioner(
        sessionId: String,
        request: AddPractitionerRequest,
    ) {
        logInfo("SessionVM", "addPractitioner called: sessionId=$sessionId")
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions/$sessionId/practitioners")
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/practitioners") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "addPractitioner success")
                    _practitionerResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "addPractitioner failed: status=${response.status.value}")
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "addPractitioner exception", e)
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updatePractitionerRemarks(
        sessionId: String,
        practitionerId: String,
        request: UpdatePractitionerRemarksRequest,
    ) {
        logInfo("SessionVM", "updatePractitionerRemarks called: sessionId=$sessionId, practitionerId=$practitionerId")
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "PATCH /api/sessions/$sessionId/practitioners/$practitionerId")
                val response =
                    apiClient.httpClient.patch(
                        "/api/sessions/$sessionId/practitioners/$practitionerId",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "updatePractitionerRemarks success")
                    _practitionerResult.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "updatePractitionerRemarks failed: status=${response.status.value}")
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "updatePractitionerRemarks exception", e)
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removePractitioner(
        sessionId: String,
        practitionerId: String,
    ) {
        logInfo("SessionVM", "removePractitioner called: sessionId=$sessionId, practitionerId=$practitionerId")
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "DELETE /api/sessions/$sessionId/practitioners/$practitionerId")
                val response =
                    apiClient.httpClient.delete(
                        "/api/sessions/$sessionId/practitioners/$practitionerId",
                    )
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "removePractitioner success")
                    _practitionerResult.value =
                        UiState.Success(
                            SessionPractitionerResponse(
                                id = "",
                                sessionId = sessionId,
                                practitionerId = practitionerId,
                                remarks = null,
                                slotAtTime = 0,
                            ),
                        )
                } else {
                    logInfo("SessionVM", "removePractitioner failed: status=${response.status.value}")
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "removePractitioner exception", e)
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadAllConcerns() {
        logInfo("SessionVM", "loadAllConcerns called")
        viewModelScope.launch {
            _concerns.value = UiState.Loading
            try {
                logInfo("SessionVM", "GET /api/concerns")
                val response = apiClient.httpClient.get("/api/concerns")
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "loadAllConcerns success")
                    _concerns.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "loadAllConcerns failed: status=${response.status.value}")
                    _concerns.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "loadAllConcerns exception", e)
                _concerns.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSessionConcerns(sessionId: String) {
        logInfo("SessionVM", "loadSessionConcerns called: sessionId=$sessionId")
        viewModelScope.launch {
            _sessionConcerns.value = UiState.Loading
            try {
                logInfo("SessionVM", "GET /api/sessions/$sessionId/concerns")
                val response = apiClient.httpClient.get("/api/sessions/$sessionId/concerns")
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "loadSessionConcerns success")
                    _sessionConcerns.value = UiState.Success(response.body())
                } else {
                    logInfo("SessionVM", "loadSessionConcerns failed: status=${response.status.value}")
                    _sessionConcerns.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "loadSessionConcerns exception", e)
                _sessionConcerns.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addSessionConcern(
        sessionId: String,
        request: AddSessionConcernRequest,
    ) {
        logInfo("SessionVM", "addSessionConcern called: sessionId=$sessionId")
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions/$sessionId/concerns")
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/concerns") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "addSessionConcern success")
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    logInfo("SessionVM", "addSessionConcern failed: status=${response.status.value}")
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "addSessionConcern exception", e)
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removeSessionConcern(
        sessionId: String,
        concernId: String,
    ) {
        logInfo("SessionVM", "removeSessionConcern called: sessionId=$sessionId, concernId=$concernId")
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "DELETE /api/sessions/$sessionId/concerns/$concernId")
                val response =
                    apiClient.httpClient.delete(
                        "/api/sessions/$sessionId/concerns/$concernId",
                    )
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "removeSessionConcern success")
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    logInfo("SessionVM", "removeSessionConcern failed: status=${response.status.value}")
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "removeSessionConcern exception", e)
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun promoteConcern(
        sessionId: String,
        request: PromoteConcernRequest,
    ) {
        logInfo("SessionVM", "promoteConcern called: sessionId=$sessionId")
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                logInfo("SessionVM", "POST /api/sessions/$sessionId/promote-concern")
                val response =
                    apiClient.httpClient.post(
                        "/api/sessions/$sessionId/promote-concern",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("SessionVM", "promoteConcern success")
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    logInfo("SessionVM", "promoteConcern failed: status=${response.status.value}")
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("SessionVM", "promoteConcern exception", e)
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
