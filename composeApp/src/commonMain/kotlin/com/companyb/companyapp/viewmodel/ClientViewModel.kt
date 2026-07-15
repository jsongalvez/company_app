package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _searchResults = MutableStateFlow<UiState<List<ClientResponse>>>(UiState.Idle)
    val searchResults: StateFlow<UiState<List<ClientResponse>>> = _searchResults.asStateFlow()

    private val _clientDetail = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val clientDetail: StateFlow<UiState<ClientResponse>> = _clientDetail.asStateFlow()

    private val _createClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val createClientState: StateFlow<UiState<ClientResponse>> = _createClientState.asStateFlow()

    private val _updateClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val updateClientState: StateFlow<UiState<ClientResponse>> = _updateClientState.asStateFlow()

    private val _anonymizeState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val anonymizeState: StateFlow<UiState<Unit>> = _anonymizeState.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        logInfo("ClientVM", "search called: query=$query")
        viewModelScope.launch {
            _searchResults.value = UiState.Loading
            try {
                logInfo("ClientVM", "GET /api/clients?q=$query")
                val response = apiClient.httpClient.get("/api/clients?q=$query")
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "search success")
                    _searchResults.value = UiState.Success(response.body())
                } else {
                    logInfo("ClientVM", "search failed: status=${response.status.value}")
                    _searchResults.value = UiState.Error("Search failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ClientVM", "search exception", e)
                _searchResults.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadClient(clientId: String) {
        logInfo("ClientVM", "loadClient called: clientId=$clientId")
        viewModelScope.launch {
            _clientDetail.value = UiState.Loading
            try {
                logInfo("ClientVM", "GET /api/clients/$clientId")
                val response = apiClient.httpClient.get("/api/clients/$clientId")
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "loadClient success")
                    _clientDetail.value = UiState.Success(response.body())
                } else {
                    logInfo("ClientVM", "loadClient failed: status=${response.status.value}")
                    _clientDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ClientVM", "loadClient exception", e)
                _clientDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createClient(request: CreateClientRequest) {
        logInfo("ClientVM", "createClient called")
        viewModelScope.launch {
            _createClientState.value = UiState.Loading
            try {
                logInfo("ClientVM", "POST /api/clients")
                val response =
                    apiClient.httpClient.post("/api/clients") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "createClient success")
                    _createClientState.value = UiState.Success(response.body())
                } else {
                    logInfo("ClientVM", "createClient failed: status=${response.status.value}")
                    _createClientState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ClientVM", "createClient exception", e)
                _createClientState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateClient(
        clientId: String,
        request: UpdateClientRequest,
    ) {
        logInfo("ClientVM", "updateClient called: clientId=$clientId")
        viewModelScope.launch {
            _updateClientState.value = UiState.Loading
            try {
                logInfo("ClientVM", "PATCH /api/clients/$clientId")
                val response =
                    apiClient.httpClient.patch("/api/clients/$clientId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "updateClient success")
                    _updateClientState.value = UiState.Success(response.body())
                } else {
                    logInfo("ClientVM", "updateClient failed: status=${response.status.value}")
                    _updateClientState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ClientVM", "updateClient exception", e)
                _updateClientState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun anonymizeClient(clientId: String) {
        logInfo("ClientVM", "anonymizeClient called: clientId=$clientId")
        viewModelScope.launch {
            _anonymizeState.value = UiState.Loading
            try {
                logInfo("ClientVM", "POST /api/clients/$clientId/anonymize")
                val response = apiClient.httpClient.post("/api/clients/$clientId/anonymize")
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "anonymizeClient success")
                    _anonymizeState.value = UiState.Success(Unit)
                } else {
                    logInfo("ClientVM", "anonymizeClient failed: status=${response.status.value}")
                    _anonymizeState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ClientVM", "anonymizeClient exception", e)
                _anonymizeState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
