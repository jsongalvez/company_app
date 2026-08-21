package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogBrowseResponse(
    val entries: List<AuditLogEntryResponse>,
    val nextCursor: String? = null,
)
