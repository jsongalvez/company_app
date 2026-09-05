package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationUnreadCountResponse(
    val unreadCount: Int,
)
