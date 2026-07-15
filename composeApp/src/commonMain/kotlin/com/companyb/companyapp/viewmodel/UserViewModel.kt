package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.request.patch
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _deactivateState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deactivateState: StateFlow<UiState<Unit>> = _deactivateState.asStateFlow()

    fun deactivateUser(userId: String) {
        logInfo("UserVM", "deactivateUser called")
        viewModelScope.launch {
            _deactivateState.value = UiState.Loading
            logInfo("UserVM", "PATCH /api/users/$userId/deactivate")
            try {
                val response = apiClient.httpClient.patch("/api/users/$userId/deactivate")
                if (response.status.isSuccess()) {
                    logInfo("UserVM", "deactivateUser success")
                    _deactivateState.value = UiState.Success(Unit)
                } else {
                    logInfo("UserVM", "deactivateUser failed: status=${response.status.value}")
                    _deactivateState.value =
                        UiState.Error(
                            "Deactivation failed: ${response.status.value}",
                        )
                }
            } catch (e: Exception) {
                logError("UserVM", "deactivateUser exception", e)
                _deactivateState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
