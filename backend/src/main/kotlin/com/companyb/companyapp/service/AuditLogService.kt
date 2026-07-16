package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AuditLogEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

private val logger = KotlinLogging.logger {}

object AuditLogService {
    fun findByTableAndRecord(
        callerId: UUID,
        tableName: String,
        recordId: UUID,
    ): List<AuditLogEntry> {
        logger.info { "[AUDIT-LIST] $callerId fetching audit entries for $tableName/$recordId" }
        return AuditLogRepository.findByTableAndRecord(tableName, recordId)
    }

    fun findFlagged(callerId: UUID): List<AuditLogEntry> {
        logger.info { "[AUDIT-FLAGGED] $callerId fetching unacknowledged flagged entries" }
        return AuditLogRepository.findFlagged()
    }

    @Suppress("ThrowsCount")
    fun acknowledge(
        callerId: UUID,
        entryId: UUID,
    ): AuditLogEntry {
        val updated = AuditLogRepository.acknowledge(entryId, callerId)
        if (!updated) throw NotFoundResponse("Audit entry not found or already acknowledged")

        return AuditLogRepository.findById(entryId)
            ?: throw NotFoundResponse("Audit entry not found")
    }
}
