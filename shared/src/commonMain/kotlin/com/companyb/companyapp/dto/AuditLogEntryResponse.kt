package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.AuditAction
import kotlinx.serialization.Serializable

@Serializable
data class AuditLogEntryResponse(
    val id: String,
    val tableName: String,
    val recordId: String,
    val action: AuditAction,
    val changedBy: String,
    val changedByName: String? = null,
    val branchId: String? = null,
    val changedAt: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val isFlagged: Boolean,
    val reason: String? = null,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
)
