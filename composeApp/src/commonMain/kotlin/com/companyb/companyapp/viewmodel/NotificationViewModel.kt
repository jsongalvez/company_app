package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
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

    // #97 Q5 silent-refresh / #113 D2 keep-last-results, VM-side (audit #141 pass-5; the #162
    // KeepLast unifier): the freshest unread list survives Loading/Error so the screen keeps
    // rendering it across reloads AND composition re-entries (a screen-side remember would die
    // on re-entry; the VM is entry-scoped). It is also the fallback the markRead/markAll
    // transforms operate on when the state is Loading/Error — actions must not no-op against a
    // rendered list. The actionStamp (see loadUnreadNotifications) keeps a stale pre-action
    // load from resurrecting read rows into it.
    private val keptNotifications = KeepLast<List<NotificationResponse>>(viewModelScope)
    val notifications: StateFlow<UiState<List<NotificationResponse>>> = keptNotifications.state
    val freshestNotifications: StateFlow<List<NotificationResponse>?> = keptNotifications.freshest

    private val _markReadResult = MutableStateFlow<UiState<NotificationResponse>>(UiState.Idle)
    val markReadResult: StateFlow<UiState<NotificationResponse>> = _markReadResult.asStateFlow()

    private val _markAllResult = MutableStateFlow<UiState<NotificationMarkAllReadResponse>>(UiState.Idle)
    val markAllResult: StateFlow<UiState<NotificationMarkAllReadResponse>> = _markAllResult.asStateFlow()

    private val _readThisSession = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val readThisSession: StateFlow<List<NotificationResponse>> = _readThisSession.asStateFlow()

    // Bumped on every successful action (markRead/markAll): loads capture it at launch, and a
    // landing with a mismatched stamp is a stale pre-action snapshot — see loadUnreadNotifications.
    private var actionStamp = 0L

    fun loadUnreadNotifications(): Job =
        handler.launch(
            state = keptNotifications.stateFlow,
            operation = "loadUnreadNotifications",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get(ApiRoutes.NOTIFICATIONS) },
            transform = { it.body<List<NotificationResponse>>() },
            // #165 stale-substitution guard (concentrated from the former in-transform block): a
            // load that lands after an action (markRead/markAll) moved the list must not commit
            // its pre-action snapshot (audit #141 pass-6/7) — the stamp read at landing disagrees
            // with the launch-captured read, and the handler substitutes the fallback instead.
            // The substitution is race-free (the action's assignment is synchronous
            // same-thread, and freshestValue reads the exact post-action Success — the stale
            // landing's own Loading write is current at fallback time, so the read resolves to
            // the mirror, exact on Main.immediate); the re-issue (a new load carrying the
            // post-action stamp) converges server truth — post-action arrivals surface, and the
            // resurrect frame is eliminated even if the re-issue GET fails.
            stamp = { actionStamp },
            fallback = {
                loadUnreadNotifications()
                currentUnreadList() ?: emptyList()
            },
        )

    fun markRead(notificationId: String): Job =
        handler.launch(
            state = _markReadResult,
            operation = "markRead",
            endpoint = "PATCH /api/notifications/$notificationId/read",
            block = { apiClient.httpClient.patch(ApiRoutes.notificationRead(notificationId)) },
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
                    // forever (#140 stuck-Loading class). The stamp bump keeps any pre-404 load
                    // in flight from committing its snapshot as if nothing happened.
                    _markReadResult.value = UiState.Idle
                    actionStamp++
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
            block = { apiClient.httpClient.post(ApiRoutes.NOTIFICATIONS_READ_ALL) },
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
        // mutateRemoved (#164): false when the row never left (nothing loaded / not in the
        // list — no write), so the stamp bump + badge decrement fire only on a real removal.
        val removed = keptNotifications.mutateRemoved { it.id == notification.id }
        if (removed) {
            _readThisSession.value = _readThisSession.value + notification
        }
        return removed
    }

    private fun moveAllToReadThisSession() {
        // mutate (#163): the transform computes the read-section payload from the exact read
        // and writes the constant post-action list. The transform runs synchronously in the
        // caller's frame (no dispatch), so its _readThisSession read + write are exact at the
        // Success write.
        keptNotifications.mutate { current ->
            val newRead = current.filterNot { row -> _readThisSession.value.any { it.id == row.id } }
            _readThisSession.value = _readThisSession.value + newRead
            emptyList()
        }
    }

    private fun currentUnreadList(): List<NotificationResponse>? = keptNotifications.freshestValue()
}
