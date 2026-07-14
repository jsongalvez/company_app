package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DelegateViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _assignResult = MutableStateFlow<UiState<DelegateResponse>>(UiState.Idle)
    val assignResult: StateFlow<UiState<DelegateResponse>> = _assignResult.asStateFlow()

    private val _revokeResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val revokeResult: StateFlow<UiState<Unit>> = _revokeResult.asStateFlow()

    fun assignDelegate(request: AssignDelegateRequest) {
        viewModelScope.launch {
            _assignResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/delegates") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _assignResult.value = UiState.Success(response.body())
                } else {
                    _assignResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _assignResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun revokeDelegate(delegateId: String) {
        viewModelScope.launch {
            _revokeResult.value = UiState.Loading
            try {
                val response = apiClient.httpClient.delete("/api/delegates/$delegateId")
                if (response.status.isSuccess()) {
                    _revokeResult.value = UiState.Success(Unit)
                } else {
                    _revokeResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _revokeResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
