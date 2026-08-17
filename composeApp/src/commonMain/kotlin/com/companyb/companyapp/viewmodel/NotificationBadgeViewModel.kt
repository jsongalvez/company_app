package com.companyb.companyapp.viewmodel
import com.companyb.companyapp.api.ApiRoutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.ui.screen.isInviteActionable
import com.companyb.companyapp.ui.screen.manilaToday
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

    // #160 — the invite poll mirrors the unread poll (same interval, own in-flight guard):
    // the badge sums pending invites + unread reminders (#159 Q5), so the drawer badge
    // needs both counts fresh. Counting only actionable invites (PENDING + not expired)
    // keeps an expired-but-unanswered invite from inflating the badge.
    private val _invitePollResult = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val invitePollResult: StateFlow<UiState<Int>> = _invitePollResult.asStateFlow()

    init {
        viewModelScope.launch {
            _pollResult
                .filterIsInstance<UiState.Success<Int>>()
                .collect { state ->
                    // accepted cost: optimistic markRead decrement + 60s authoritative poll overwrite bounces the
                    // badge count down-then-up ≤60s — accepted because grace-window would treat a local edit as
                    // authoritative for a window (small violation of "backend authoritative" axis, ADR-0022/#97).
                    // The same up-bounce direction applies to a poll snapshot taken before markAll's
                    // authoritative 0 lands (markAll sets the singleton directly, a pre-markAll poll in
                    // flight then overwrites it with the stale pre-markAll count — self-corrects next poll).
                    NotificationState.setUnreadCount(state.data)
                }
        }
        viewModelScope.launch {
            _invitePollResult
                .filterIsInstance<UiState.Success<Int>>()
                .collect { state -> NotificationState.setInviteCount(state.data) }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshUnreadCount()
                delay(REFRESH_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshInviteCount()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }

    private fun refreshUnreadCount() {
        // in-flight guard: a GET slower than the 60s interval would stack overlapping polls and
        // let an out-of-order Success overwrite the singleton with a stale count. The check is
        // reliable because viewModelScope's Main.immediate dispatcher runs handler.launch's body
        // (which pre-sets Loading as its first statement) synchronously in the poll loop's frame —
        // so by the next 60s iteration, a started-but-slow poll is already visible as Loading.
        if (_pollResult.value is UiState.Loading) return
        handler.launch(
            state = _pollResult,
            operation = "refreshUnreadCount",
            endpoint = "GET /api/notifications",
            block = { apiClient.httpClient.get(ApiRoutes.NOTIFICATIONS) },
            transform = { it.body<List<NotificationResponse>>().size },
        )
    }

    private fun refreshInviteCount() {
        if (_invitePollResult.value is UiState.Loading) return
        handler.launch(
            state = _invitePollResult,
            operation = "refreshInviteCount",
            endpoint = "GET /api/relief-invites",
            block = { apiClient.httpClient.get(ApiRoutes.RELIEF_INVITES) },
            transform = { response ->
                val today = manilaToday()
                response.body<List<ReliefInviteResponse>>().count { invite ->
                    isInviteActionable(invite, today)
                }
            },
        )
    }

    private companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L
    }
}
