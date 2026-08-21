package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationMarkAllReadResponse(
    val unreadCount: Int,
)
