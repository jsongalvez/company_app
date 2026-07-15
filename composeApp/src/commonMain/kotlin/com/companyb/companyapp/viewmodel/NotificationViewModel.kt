package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
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
        logInfo("NotificationVM", "loadUnreadNotifications called")
        viewModelScope.launch {
            _notifications.value = UiState.Loading
            logInfo("NotificationVM", "GET /api/notifications")
            try {
                val response = apiClient.httpClient.get("/api/notifications")
                if (response.status.isSuccess()) {
                    logInfo("NotificationVM", "loadUnreadNotifications success")
                    _notifications.value = UiState.Success(response.body())
                } else {
                    logInfo("NotificationVM", "loadUnreadNotifications failed: status=${response.status.value}")
                    _notifications.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("NotificationVM", "loadUnreadNotifications exception", e)
                _notifications.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun markRead(notificationId: String) {
        logInfo("NotificationVM", "markRead called")
        viewModelScope.launch {
            _markReadResult.value = UiState.Loading
            logInfo("NotificationVM", "PATCH /api/notifications/$notificationId/read")
            try {
                val response =
                    apiClient.httpClient.patch(
                        "/api/notifications/$notificationId/read",
                    )
                if (response.status.isSuccess()) {
                    logInfo("NotificationVM", "markRead success")
                    _markReadResult.value = UiState.Success(response.body())
                } else {
                    logInfo("NotificationVM", "markRead failed: status=${response.status.value}")
                    _markReadResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("NotificationVM", "markRead exception", e)
                _markReadResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
