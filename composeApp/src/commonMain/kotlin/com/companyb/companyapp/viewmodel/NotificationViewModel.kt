package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _notifications = MutableStateFlow<UiState<List<NotificationResponse>>>(UiState.Idle)
    val notifications: StateFlow<UiState<List<NotificationResponse>>> = _notifications.asStateFlow()

    private val _markReadResult = MutableStateFlow<UiState<NotificationResponse>>(UiState.Idle)
    val markReadResult: StateFlow<UiState<NotificationResponse>> = _markReadResult.asStateFlow()

    fun loadUnreadNotifications() {
        viewModelScope.launch {
            _notifications.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/notifications")
                if (response.status.isSuccess()) {
                    _notifications.value = UiState.Success(response.body())
                } else {
                    _notifications.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _notifications.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            _markReadResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch(
                        "/api/notifications/$notificationId/read",
                    )
                if (response.status.isSuccess()) {
                    _markReadResult.value = UiState.Success(response.body())
                } else {
                    _markReadResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _markReadResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
