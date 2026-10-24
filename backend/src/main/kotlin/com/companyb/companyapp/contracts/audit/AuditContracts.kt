package com.companyb.companyapp.contracts.audit

import kotlinx.serialization.Serializable

/**
 * [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `docs/architecture.md`): old clients decode newer server values as UNKNOWN instead of failing
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
    /** What the change was about, in words: "Session: Maria Santos, Oct 19", "Juan · Silang, Oct 19". */
    val subject: String? = null,
    /** Readable names for ids inside [oldValue]/[newValue] (user, client, product, day...). */
    val names: List<AuditName> = emptyList(),
)

@Serializable
data class AuditName(
    val id: String,
    val name: String,
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
