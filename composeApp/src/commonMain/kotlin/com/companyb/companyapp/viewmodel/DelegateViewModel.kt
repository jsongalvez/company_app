package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
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
        logInfo("DelegateVM", "assignDelegate called")
        viewModelScope.launch {
            _assignResult.value = UiState.Loading
            try {
                logInfo("DelegateVM", "POST /api/delegates")
                val response =
                    apiClient.httpClient.post("/api/delegates") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("DelegateVM", "assignDelegate success")
                    _assignResult.value = UiState.Success(response.body())
                } else {
                    logInfo("DelegateVM", "assignDelegate failed: status=${response.status.value}")
                    _assignResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("DelegateVM", "assignDelegate exception", e)
                _assignResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun revokeDelegate(delegateId: String) {
        logInfo("DelegateVM", "revokeDelegate called")
        viewModelScope.launch {
            _revokeResult.value = UiState.Loading
            try {
                logInfo("DelegateVM", "DELETE /api/delegates/$delegateId")
                val response = apiClient.httpClient.delete("/api/delegates/$delegateId")
                if (response.status.isSuccess()) {
                    logInfo("DelegateVM", "revokeDelegate success")
                    _revokeResult.value = UiState.Success(Unit)
                } else {
                    logInfo("DelegateVM", "revokeDelegate failed: status=${response.status.value}")
                    _revokeResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("DelegateVM", "revokeDelegate exception", e)
                _revokeResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
