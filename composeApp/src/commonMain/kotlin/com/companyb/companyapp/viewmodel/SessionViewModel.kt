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
        viewModelScope.launch {
            _sessionResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/sessions") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _sessionResult.value = UiState.Success(response.body())
                } else {
                    _sessionResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _sessionResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateStatus(
        sessionId: String,
        request: UpdateSessionStatusRequest,
    ) {
        viewModelScope.launch {
            _statusUpdateResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch("/api/sessions/$sessionId/status") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _statusUpdateResult.value = UiState.Success(response.body())
                } else {
                    _statusUpdateResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _statusUpdateResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun voidSession(
        sessionId: String,
        request: VoidSessionRequest,
    ) {
        viewModelScope.launch {
            _voidResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/void") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _voidResult.value = UiState.Success(response.body())
                } else {
                    _voidResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _voidResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun unvoidSession(
        sessionId: String,
        request: UnvoidSessionRequest,
    ) {
        viewModelScope.launch {
            _unvoidResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/unvoid") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _unvoidResult.value = UiState.Success(response.body())
                } else {
                    _unvoidResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _unvoidResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSessionPractitioners(sessionId: String) {
        viewModelScope.launch {
            _practitioners.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/sessions/$sessionId/practitioners")
                if (response.status.isSuccess()) {
                    _practitioners.value = UiState.Success(response.body())
                } else {
                    _practitioners.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _practitioners.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addPractitioner(
        sessionId: String,
        request: AddPractitionerRequest,
    ) {
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/practitioners") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _practitionerResult.value = UiState.Success(response.body())
                } else {
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updatePractitionerRemarks(
        sessionId: String,
        practitionerId: String,
        request: UpdatePractitionerRemarksRequest,
    ) {
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch(
                        "/api/sessions/$sessionId/practitioners/$practitionerId",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _practitionerResult.value = UiState.Success(response.body())
                } else {
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removePractitioner(
        sessionId: String,
        practitionerId: String,
    ) {
        viewModelScope.launch {
            _practitionerResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.delete(
                        "/api/sessions/$sessionId/practitioners/$practitionerId",
                    )
                if (response.status.isSuccess()) {
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
                    _practitionerResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _practitionerResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadAllConcerns() {
        viewModelScope.launch {
            _concerns.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/concerns")
                if (response.status.isSuccess()) {
                    _concerns.value = UiState.Success(response.body())
                } else {
                    _concerns.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _concerns.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSessionConcerns(sessionId: String) {
        viewModelScope.launch {
            _sessionConcerns.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/sessions/$sessionId/concerns")
                if (response.status.isSuccess()) {
                    _sessionConcerns.value = UiState.Success(response.body())
                } else {
                    _sessionConcerns.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _sessionConcerns.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addSessionConcern(
        sessionId: String,
        request: AddSessionConcernRequest,
    ) {
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/sessions/$sessionId/concerns") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removeSessionConcern(
        sessionId: String,
        concernId: String,
    ) {
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.delete(
                        "/api/sessions/$sessionId/concerns/$concernId",
                    )
                if (response.status.isSuccess()) {
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun promoteConcern(
        sessionId: String,
        request: PromoteConcernRequest,
    ) {
        viewModelScope.launch {
            _concernResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/sessions/$sessionId/promote-concern",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _concernResult.value = UiState.Success(Unit)
                } else {
                    _concernResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _concernResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
