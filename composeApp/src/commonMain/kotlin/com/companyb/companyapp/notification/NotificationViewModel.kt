package com.companyb.companyapp.notification
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ActionStamp
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.KeepLast
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.ReconcilingLoad
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.async.mutateRemoved
import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationMarkAllReadResponse
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.network.ApiClient
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

    // #356 — server-backed history (read + unread, newest first). No stamp machinery: no
    // action mutates the history list, so a plain UiState flow carries load/reload.
    private val _history = MutableStateFlow<UiState<List<NotificationResponse>>>(UiState.Idle)
    val history: StateFlow<UiState<List<NotificationResponse>>> = _history.asStateFlow()

    // Bumped on every successful action (markRead/markAll): a load that lands with a
    // mismatched capture predates the action — see loadUnreadNotifications.
    private val actionStamp = ActionStamp()

    // #679 — armed by refreshQueue, consumed by onRefreshLanded: plain flag (not a flow —
    // only the screen effect reads it via the call, never composition).
    private var refreshArmed = false

    // #611 retain-and-reload: a load that lands after an action (markRead/markAll) moved the
    // list must not commit its pre-action snapshot (audit #141 pass-6/7) — the handler
    // reissues instead, and the substitution is race-free (the action's assignment is
    // synchronous same-thread, and the re-issue — a new load carrying the post-action stamp —
    // converges server truth: post-action arrivals surface, and the resurrect frame is
    // eliminated even if the re-issue GET fails).
    fun loadUnreadNotifications(): Job =
        handler.launchReconciling(
            ReconcilingLoad(
                state = keptNotifications.stateFlow,
                operation = "loadUnreadNotifications",
                endpoint = "GET /api/notifications",
                block = { apiClient.httpClient.get(ApiRoutes.NOTIFICATIONS) },
                decode = { it.body<List<NotificationResponse>>() },
                stamp = actionStamp,
                reissue = ::loadUnreadNotifications,
            ),
        )

    // #356 — history load; runs alongside the unread fetch on screen entry and on retry.
    // #508 — keyset-paged under the hood: bounded pages accumulate into the full Earlier
    // list, so the screen keeps rendering every row while no single response is unbounded.
    fun loadHistory(): Job =
        handler.launch(
            state = _history,
            operation = "loadHistory",
            endpoint = "GET /api/notifications/history",
            block = {
                apiClient.httpClient.get(ApiRoutes.notificationsHistory(cursor = null, limit = HISTORY_PAGE_LIMIT))
            },
            transform = { first ->
                var page = first.body<NotificationHistoryResponse>()
                val entries = page.entries.toMutableList()
                while (page.nextCursor != null) {
                    page =
                        apiClient.httpClient
                            .get(ApiRoutes.notificationsHistory(cursor = page.nextCursor, limit = HISTORY_PAGE_LIMIT))
                            .body<NotificationHistoryResponse>()
                    entries.addAll(page.entries)
                }
                entries.toList()
            },
        )

    fun markRead(notificationId: String): Job =
        handler.launch(
            LaunchRequest(
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
                        actionStamp.bump()
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
                        actionStamp.bump()
                        NotificationState.decrementUnread()
                    }
                    body
                },
            ),
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
                actionStamp.bump()
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

    // #679 — explicit refresh reconciles the visit-local read marks to server groups: rows
    // read this visit leave the in-place queue and reappear under Earlier via the history
    // reload, while the unread reload converges the queue. User-initiated, so the
    // resulting shift is not a reorder under the pointer. The marks clear only once the
    // history leg lands (see onRefreshLanded) — clearing upfront would vaporize rows read
    // this visit if both reloads failed, since markRead already removed them from the feed.
    fun refreshQueue() {
        refreshArmed = true
        loadUnreadNotifications()
        loadHistory()
    }

    /**
     * Converges a refresh: drops the visit-local read marks once the history reload lands,
     * re-homing those rows under Earlier from server truth. No-op unless a refresh armed it
     * (entry loads, markAll-triggered reloads and history retries never arm). A markRead that
     * lands between arm and history Success is cleared with the rest — it reconverges on the
     * next refresh.
     */
    fun onRefreshLanded() {
        if (refreshArmed) {
            refreshArmed = false
            _readThisSession.value = emptyList()
        }
    }

    private fun moveToReadThisSession(notification: NotificationResponse): Boolean {
        // Read-section dedupe: a row already marked this session can never move or decrement
        // again — not even when a stale reload re-renders it as unread (#112 decision 4).
        if (_readThisSession.value.any { it.id == notification.id }) return false
        // mutateRemoved (#164): false when the row never left (nothing loaded / not in the
        // list — no write), so the stamp bump + badge decrement fire only on a real removal.
        val removed = keptNotifications.mutateRemoved { it.id == notification.id }
        if (removed) {
            _readThisSession.value += notification
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
            _readThisSession.value += newRead
            emptyList()
        }
    }

    private companion object {
        // #508 — matches the server's max browse page: fewest round-trips per history load.
        private const val HISTORY_PAGE_LIMIT = 100
    }
}
