package com.companyb.companyapp.contracts.notification

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
    // #358 — the branch day a relief notification points at; the tap destination is the
    // dashboard scoped to (branchId, targetDate). Appointment reminders leave it null.
    val targetDate: String? = null,
)

@Serializable
data class NotificationHistoryResponse(
    val entries: List<NotificationResponse>,
    val nextCursor: String? = null,
)

@Serializable
data class NotificationMarkAllReadResponse(
    val unreadCount: Int,
)

@Serializable
data class NotificationUnreadCountResponse(
    val unreadCount: Int,
)
