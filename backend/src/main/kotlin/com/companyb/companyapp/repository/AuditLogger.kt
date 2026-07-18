package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import java.util.UUID

@Suppress("SpreadOperator")
object AuditLogger {
    fun insert(
        table: String,
        id: UUID,
        by: UUID,
        vararg fields: Pair<String, String>,
    ) {
        AuditLogRepository.record(
            tableName = table,
            recordId = id,
            action = AuditAction.INSERT,
            changedBy = by,
            newValue = AuditLogRepository.jsonFields(*fields),
        )
    }

    fun update(
        table: String,
        id: UUID,
        by: UUID,
        oldFields: Array<Pair<String, String>>,
        newFields: Array<Pair<String, String>>,
    ) {
        AuditLogRepository.record(
            tableName = table,
            recordId = id,
            action = AuditAction.UPDATE,
            changedBy = by,
            oldValue = AuditLogRepository.jsonFields(*oldFields),
            newValue = AuditLogRepository.jsonFields(*newFields),
        )
    }

    @Suppress("LongParameterList")
    fun delete(
        table: String,
        id: UUID,
        by: UUID,
        oldFields: Array<Pair<String, String>>,
        newFields: Array<Pair<String, String>>,
        reason: String? = null,
    ) {
        AuditLogRepository.record(
            tableName = table,
            recordId = id,
            action = AuditAction.DELETE,
            changedBy = by,
            oldValue = AuditLogRepository.jsonFields(*oldFields),
            newValue = AuditLogRepository.jsonFields(*newFields),
            reason = reason,
        )
    }
}
