package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val id: String,
    // Nullable from #356: non-session events (relief) have no session to open.
    val sessionId: String?,
    val branchId: String,
    val message: String,
    val isRead: Boolean,
    val readAt: String?,
    val createdAt: String,
)
