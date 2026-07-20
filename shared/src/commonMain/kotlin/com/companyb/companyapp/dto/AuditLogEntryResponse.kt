package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogEntryResponse(
    val id: String,
    val tableName: String,
    val recordId: String,
    val action: String,
    val changedBy: String,
    val changedAt: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val isFlagged: Boolean,
    val reason: String? = null,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
)
