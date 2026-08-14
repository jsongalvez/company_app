package com.companyb.companyapp.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

object NotificationState {
    private val _unreadCount = MutableStateFlow<Int?>(null)
    val unreadCount: StateFlow<Int?> = _unreadCount.asStateFlow()

    // #160 — the badge sums pending invites + unread reminders (#159 Q5); the invite
    // poller (NotificationBadgeViewModel) owns this slot, the invite VM decrements it
    // optimistically on accept/decline.
    private val _inviteCount = MutableStateFlow<Int?>(null)
    val inviteCount: StateFlow<Int?> = _inviteCount.asStateFlow()

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count
    }

    fun decrementUnread() {
        val current = _unreadCount.value ?: return
        _unreadCount.value = max(0, current - 1)
    }

    fun setInviteCount(count: Int) {
        _inviteCount.value = count
    }

    fun decrementInvites() {
        val current = _inviteCount.value ?: return
        _inviteCount.value = max(0, current - 1)
    }

    /** The badge total — null only before the first poll of either slot (alert-not-status). */
    fun badgeSum(
        unread: Int?,
        invites: Int?,
    ): Int? =
        when {
            unread == null && invites == null -> null
            else -> (unread ?: 0) + (invites ?: 0)
        }

    fun clear() {
        _unreadCount.value = null
        _inviteCount.value = null
    }
}
