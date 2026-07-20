package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "NotificationVM")

    private val _notifications = MutableStateFlow<UiState<List<NotificationResponse>>>(UiState.Idle)
    val notifications: StateFlow<UiState<List<NotificationResponse>>> = _notifications.asStateFlow()

    private val _markReadResult = MutableStateFlow<UiState<NotificationResponse>>(UiState.Idle)
    val markReadResult: StateFlow<UiState<NotificationResponse>> = _markReadResult.asStateFlow()

    fun loadUnreadNotifications() {
        handler.launch(
            state = _notifications,
            operation = "loadUnreadNotifications",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get("/api/notifications") },
            transform = { it.body() },
        )
    }

    fun markRead(notificationId: String) {
        handler.launch(
            state = _markReadResult,
            operation = "markRead",
            endpoint = "PATCH /api/notifications/$notificationId/read",
            block = { apiClient.httpClient.patch("/api/notifications/$notificationId/read") },
            transform = { it.body() },
        )
    }
}
