package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
    private val handler = ApiCallHandler(viewModelScope, "ClientVM")

    private val _searchResults = MutableStateFlow<UiState<List<ClientResponse>>>(UiState.Idle)
    val searchResults: StateFlow<UiState<List<ClientResponse>>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchErrorMessage = MutableStateFlow<String?>(null)
    val searchErrorMessage: StateFlow<String?> = _searchErrorMessage.asStateFlow()

    private val _clientDetail = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val clientDetail: StateFlow<UiState<ClientResponse>> = _clientDetail.asStateFlow()

    private val _createClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val createClientState: StateFlow<UiState<ClientResponse>> = _createClientState.asStateFlow()

    private val _updateClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val updateClientState: StateFlow<UiState<ClientResponse>> = _updateClientState.asStateFlow()

    private val _anonymizeState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val anonymizeState: StateFlow<UiState<Unit>> = _anonymizeState.asStateFlow()

    fun clearSearch() {
        _searchResults.value = UiState.Idle
        _searchErrorMessage.value = null
        _isSearching.value = false
    }

    fun search(query: String) {
        if (query.isBlank()) return
        logInfo("ClientVM", "search called: query=$query")
        _isSearching.value = true
        _searchErrorMessage.value = null
        viewModelScope.launch {
            try {
                logInfo("ClientVM", "GET /api/clients with parameter q=$query")
                val response =
                    apiClient.httpClient.get("/api/clients") {
                        parameter("q", query)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ClientVM", "search success")
                    _searchResults.value = UiState.Success(response.body())
                } else {
                    logWarn("ClientVM", "search failed: status=${response.status.value}")
                    _searchErrorMessage.value = "search failed: ${response.status.value}"
                }
            } catch (e: Exception) {
                logError("ClientVM", "search exception", e)
                _searchErrorMessage.value = e.message ?: "Unknown error"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun loadClient(clientId: String) {
        handler.launch(
            state = _clientDetail,
            operation = "loadClient",
            endpoint = "GET /api/clients/$clientId",
            entryMessage = "loadClient called: clientId=$clientId",
            block = { apiClient.httpClient.get("/api/clients/$clientId") },
            transform = { it.body() },
        )
    }

    fun createClient(request: CreateClientRequest) {
        handler.launch(
            state = _createClientState,
            operation = "createClient",
            endpoint = "POST /api/clients",
            block = {
                apiClient.httpClient.post("/api/clients") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun updateClient(
        clientId: String,
        request: UpdateClientRequest,
    ) {
        handler.launch(
            state = _updateClientState,
            operation = "updateClient",
            endpoint = "PATCH /api/clients/$clientId",
            entryMessage = "updateClient called: clientId=$clientId",
            block = {
                apiClient.httpClient.patch("/api/clients/$clientId") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun anonymizeClient(clientId: String) {
        handler.launchUnit(
            state = _anonymizeState,
            operation = "anonymizeClient",
            endpoint = "POST /api/clients/$clientId/anonymize",
            entryMessage = "anonymizeClient called: clientId=$clientId",
            block = { apiClient.httpClient.post("/api/clients/$clientId/anonymize") },
        )
    }
}
