package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationHistoryResponse(
    val entries: List<NotificationResponse>,
    val nextCursor: String? = null,
)
