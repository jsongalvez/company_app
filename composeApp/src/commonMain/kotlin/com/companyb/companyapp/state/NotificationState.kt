package com.companyb.companyapp.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

object NotificationState {
    private val _unreadCount = MutableStateFlow<Int?>(null)
    val unreadCount: StateFlow<Int?> = _unreadCount.asStateFlow()

    fun setUnreadCount(count: Int) {
        _unreadCount.value = count
    }

    fun decrementUnread() {
        val current = _unreadCount.value ?: return
        _unreadCount.value = max(0, current - 1)
    }

    fun clear() {
        _unreadCount.value = null
    }
}
