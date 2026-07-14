package com.companyb.companyapp.repository.model

import java.time.OffsetDateTime
import java.util.UUID

data class AuditLogEntry(
    val id: UUID,
    val tableName: String,
    val recordId: UUID,
    val action: AuditAction,
    val changedBy: UUID,
    val changedAt: OffsetDateTime,
    val oldValue: String?,
    val newValue: String?,
    val isFlagged: Boolean,
    val reason: String?,
    val acknowledgedBy: UUID?,
    val acknowledgedAt: OffsetDateTime?,
)
