package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.request.patch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "UserVM")

    private val _deactivateState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deactivateState: StateFlow<UiState<Unit>> = _deactivateState.asStateFlow()

    fun deactivateUser(userId: String) {
        handler.launchUnit(
            state = _deactivateState,
            operation = "deactivateUser",
            endpoint = "PATCH /api/users/$userId/deactivate",
            block = { apiClient.httpClient.patch("/api/users/$userId/deactivate") },
        )
    }
}
