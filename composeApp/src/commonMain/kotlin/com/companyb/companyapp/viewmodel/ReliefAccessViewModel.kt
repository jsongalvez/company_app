package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReliefAccessViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _requestState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val requestState: StateFlow<UiState<ReliefAccessResponse>> = _requestState.asStateFlow()

    private val _grantState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val grantState: StateFlow<UiState<ReliefAccessResponse>> = _grantState.asStateFlow()

    private val _denyState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val denyState: StateFlow<UiState<ReliefAccessResponse>> = _denyState.asStateFlow()

    fun requestAccess(request: ReliefAccessRequest) {
        viewModelScope.launch {
            _requestState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/relief-access/request") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _requestState.value = UiState.Success(response.body())
                } else {
                    _requestState.value = UiState.Error("Request failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _requestState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun grantAccess(requestId: String) {
        viewModelScope.launch {
            _grantState.value = UiState.Loading
            try {
                val response = apiClient.httpClient.patch("/api/relief-access/$requestId/grant")
                if (response.status.isSuccess()) {
                    _grantState.value = UiState.Success(response.body())
                } else {
                    _grantState.value = UiState.Error("Grant failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _grantState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun denyAccess(requestId: String) {
        viewModelScope.launch {
            _denyState.value = UiState.Loading
            try {
                val response = apiClient.httpClient.patch("/api/relief-access/$requestId/deny")
                if (response.status.isSuccess()) {
                    _denyState.value = UiState.Success(response.body())
                } else {
                    _denyState.value = UiState.Error("Deny failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _denyState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
