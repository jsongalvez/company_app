package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.network.ApiClient
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
        viewModelScope.launch {
            _deactivateState.value = UiState.Loading
            try {
                val response = apiClient.httpClient.patch("/api/users/$userId/deactivate")
                if (response.status.isSuccess()) {
                    _deactivateState.value = UiState.Success(Unit)
                } else {
                    _deactivateState.value =
                        UiState.Error(
                            "Deactivation failed: ${response.status.value}",
                        )
                }
            } catch (e: Exception) {
                _deactivateState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
