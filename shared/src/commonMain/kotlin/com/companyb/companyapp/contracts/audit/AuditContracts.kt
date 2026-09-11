package com.companyb.companyapp.contracts.audit

import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`): old clients decode newer server values as UNKNOWN instead of failing
 * the whole response. Never persisted, never sent.
 */
@Serializable
enum class AuditAction { INSERT, UPDATE, DELETE, UNKNOWN }

@Serializable
data class AuditLogEntryResponse(
    val id: String,
    val tableName: String,
    val recordId: String,
    val action: AuditAction = AuditAction.UNKNOWN,
    val changedBy: String,
    val changedByName: String? = null,
    val branchId: String? = null,
    val branchName: String? = null,
    val changedAt: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val isFlagged: Boolean,
    val reason: String? = null,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
)

@Serializable
data class AuditLogBrowseResponse(
    val entries: List<AuditLogEntryResponse>,
    val nextCursor: String? = null,
)

@Serializable
data class AuditLogTableResponse(
    val tableName: String,
    val label: String,
)
