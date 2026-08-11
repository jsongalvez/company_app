package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationMarkAllReadResponse
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
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

    private val _markAllResult = MutableStateFlow<UiState<NotificationMarkAllReadResponse>>(UiState.Idle)
    val markAllResult: StateFlow<UiState<NotificationMarkAllReadResponse>> = _markAllResult.asStateFlow()

    private val _readThisSession = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val readThisSession: StateFlow<List<NotificationResponse>> = _readThisSession.asStateFlow()

    fun loadUnreadNotifications() {
        handler.launch(
            state = _notifications,
            operation = "loadUnreadNotifications",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get("/api/notifications") },
            transform = { it.body() },
        )
    }

    fun markRead(notificationId: String): Job =
        handler.launch(
            state = _markReadResult,
            operation = "markRead",
            endpoint = "PATCH /api/notifications/$notificationId/read",
            block = { apiClient.httpClient.patch("/api/notifications/$notificationId/read") },
            onNonSuccess = { response ->
                if (response.status == HttpStatusCode.NotFound) {
                    // Defense-in-depth: the backend 200s an already-read OWN row (WHERE id+user
                    // matches, readAt refreshed — idempotent), so a 404 can only mean the row is
                    // absent or not the caller's — unreachable from this UI today, and no fogged
                    // backend expansion (the #102 read-history/un-read endpoints neither delete
                    // nor transfer rows) makes it reachable; kept as pure defense against a
                    // future deletion/expiry surface. Handle it as "the row is gone": reload and
                    // let the screen re-derive instead of surfacing a phantom failure, and reset
                    // the in-flight marker — leaving Loading would mark the action in-flight
                    // forever (#140 stuck-Loading class).
                    _markReadResult.value = UiState.Idle
                    loadUnreadNotifications()
                    true
                } else {
                    false
                }
            },
            transform = {
                val body = it.body<NotificationResponse>()
                // Decrement only when the row actually left the unread list — a double-tap's
                // second PATCH success must not decrement the badge twice (the first success
                // already moved the row out; the poll overwrite catches drift, #109 Q6 axis).
                if (moveToReadThisSession(body)) {
                    NotificationState.decrementUnread()
                }
                body
            },
        )

    fun markAllRead(): Job =
        handler.launch(
            state = _markAllResult,
            operation = "markAllRead",
            endpoint = "POST /api/notifications/read-all",
            block = { apiClient.httpClient.post("/api/notifications/read-all") },
            transform = {
                val body = it.body<NotificationMarkAllReadResponse>()
                // #111 count-semantics: badge assigned from the endpoint's authoritative response,
                // never client arithmetic — an arrival committed before the count is included,
                // an arrival after is caught by the next 60s poll.
                NotificationState.setUnreadCount(body.unreadCount)
                moveAllToReadThisSession()
                // Reload iff the authoritative count shows arrivals since this screen's fetch —
                // the screen has no in-screen polling (D5), so a fresh fetch is the only way the
                // new row surfaces; a zero count skips the pointless round-trip.
                if (body.unreadCount > 0) {
                    loadUnreadNotifications()
                }
                body
            },
        )

    private fun moveToReadThisSession(notification: NotificationResponse): Boolean {
        val current = currentUnreadList() ?: return false
        val remaining = current.filterNot { it.id == notification.id }
        if (remaining.size == current.size) return false
        _notifications.value = UiState.Success(remaining)
        _readThisSession.value = _readThisSession.value + notification
        return true
    }

    private fun moveAllToReadThisSession() {
        val current = currentUnreadList() ?: return
        _notifications.value = UiState.Success(emptyList())
        _readThisSession.value = _readThisSession.value + current
    }

    private fun currentUnreadList(): List<NotificationResponse>? =
        (_notifications.value as? UiState.Success<List<NotificationResponse>>)?.data
}
