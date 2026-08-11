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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "NotificationVM")

    private val _notifications = MutableStateFlow<UiState<List<NotificationResponse>>>(UiState.Idle)
    val notifications: StateFlow<UiState<List<NotificationResponse>>> = _notifications.asStateFlow()

    // #97 Q5 silent-refresh / #113 D2 keep-last-results, VM-side (audit #141 pass-5): the last
    // successful list survives Loading/Error so the screen keeps rendering it across reloads AND
    // composition re-entries (a screen-side remember would die on re-entry; the VM is
    // entry-scoped). It is also the fallback the markRead/markAll transforms operate on when
    // _notifications is Loading/Error — actions must not no-op against a rendered list. The
    // actionStamp (see loadUnreadNotifications) keeps a stale pre-action load from resurrecting
    // read rows into it.
    private val _lastUnread = MutableStateFlow<List<NotificationResponse>?>(null)
    val lastUnread: StateFlow<List<NotificationResponse>?> = _lastUnread.asStateFlow()

    private val _markReadResult = MutableStateFlow<UiState<NotificationResponse>>(UiState.Idle)
    val markReadResult: StateFlow<UiState<NotificationResponse>> = _markReadResult.asStateFlow()

    private val _markAllResult = MutableStateFlow<UiState<NotificationMarkAllReadResponse>>(UiState.Idle)
    val markAllResult: StateFlow<UiState<NotificationMarkAllReadResponse>> = _markAllResult.asStateFlow()

    private val _readThisSession = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val readThisSession: StateFlow<List<NotificationResponse>> = _readThisSession.asStateFlow()

    init {
        // Single writer for lastUnread: every Success that lands on _notifications (load, the
        // markRead/markAll transforms' mutated lists) mirrors into it (the badge-VM collector
        // pattern).
        viewModelScope.launch {
            _notifications
                .filterIsInstance<UiState.Success<List<NotificationResponse>>>()
                .collect { state -> _lastUnread.value = state.data }
        }
    }

    fun loadUnreadNotifications() {
        val loadStamp = actionStamp
        handler.launch(
            state = _notifications,
            operation = "loadUnreadNotifications",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get("/api/notifications") },
            transform = { response ->
                val body = response.body<List<NotificationResponse>>()
                if (loadStamp != actionStamp) {
                    // An action (markRead/markAll) succeeded while this load was in flight, so
                    // the snapshot predates it — committing it would resurrect read rows under
                    // the new badge count (audit #141 pass-6: the stale-overwrite class). The
                    // handler still commits the stale body; the re-issue's Loading + fresh
                    // Success then re-derive the list within a dispatch or two.
                    loadUnreadNotifications()
                }
                body
            },
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
                    // absent or not the caller's — unreachable from this UI today, and no planned
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
                // The Read-section dedupe also blocks the stale-re-render class: a row the
                // reload resurrected after an action must not decrement again (#112 decision 4).
                if (moveToReadThisSession(body)) {
                    actionStamp++
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
                // Stamp BEFORE the reload launch: the markAll-triggered reload must carry the
                // post-action stamp, and any pre-markAll load still in flight re-issues on
                // landing instead of resurrecting the rows (audit #141 pass-6).
                actionStamp++
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
        // Read-section dedupe: a row already marked this session can never move or decrement
        // again — not even when a stale reload re-renders it as unread (#112 decision 4).
        if (_readThisSession.value.any { it.id == notification.id }) return false
        val current = currentUnreadList() ?: return false
        val remaining = current.filterNot { it.id == notification.id }
        if (remaining.size == current.size) return false
        _notifications.value = UiState.Success(remaining)
        _readThisSession.value = _readThisSession.value + notification
        return true
    }

    private fun moveAllToReadThisSession() {
        val current = currentUnreadList() ?: return
        val newRead = current.filterNot { row -> _readThisSession.value.any { it.id == row.id } }
        _notifications.value = UiState.Success(emptyList())
        _readThisSession.value = _readThisSession.value + newRead
    }

    private fun currentUnreadList(): List<NotificationResponse>? =
        (_notifications.value as? UiState.Success<List<NotificationResponse>>)?.data ?: _lastUnread.value

    private var actionStamp = 0L
}
