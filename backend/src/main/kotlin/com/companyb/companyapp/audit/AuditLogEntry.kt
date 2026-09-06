package com.companyb.companyapp.audit

import com.companyb.companyapp.domain.AuditAction
import java.time.OffsetDateTime
import java.util.UUID

data class AuditLogEntry(
    val id: UUID,
    val tableName: String,
    val recordId: UUID,
    val action: AuditAction,
    val changedBy: UUID,
    val changedByName: String?,
    val branchId: UUID?,
    val branchName: String?,
    val changedAt: OffsetDateTime,
    val oldValue: String?,
    val newValue: String?,
    val isFlagged: Boolean,
    val reason: String?,
    val acknowledgedBy: UUID?,
    val acknowledgedAt: OffsetDateTime?,
)
