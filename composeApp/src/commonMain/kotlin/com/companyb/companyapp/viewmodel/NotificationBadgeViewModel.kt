package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotificationBadgeViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "NotificationBadgeVM")

    private val _pollResult = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val pollResult: StateFlow<UiState<Int>> = _pollResult.asStateFlow()

    init {
        viewModelScope.launch {
            _pollResult
                .filterIsInstance<UiState.Success<Int>>()
                .collect { state ->
                    // accepted cost: optimistic markRead decrement + 60s authoritative poll overwrite bounces the
                    // badge count down-then-up ≤60s — accepted because grace-window would treat a local edit as
                    // authoritative for a window (small violation of "backend authoritative" axis, ADR-0022/#97).
                    NotificationState.setUnreadCount(state.data)
                }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshUnreadCount()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }

    private fun refreshUnreadCount() {
        handler.launch(
            state = _pollResult,
            operation = "refreshUnreadCount",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get("/api/notifications") },
            transform = { it.body<List<NotificationResponse>>().size },
        )
    }

    private companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L
    }
}
