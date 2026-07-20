package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val id: String,
    val sessionId: String,
    val branchId: String,
    val message: String,
    val isRead: Boolean,
    val readAt: String?,
    val createdAt: String,
)
