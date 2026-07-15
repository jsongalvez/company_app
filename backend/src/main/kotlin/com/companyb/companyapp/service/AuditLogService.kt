package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

private val logger = KotlinLogging.logger {}

object AuditLogService {
    fun findByTableAndRecord(
        callerId: UUID,
        tableName: String,
        recordId: UUID,
    ): List<AuditLogEntry> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = "ASSIGN_COMPENSATION",
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) throw ForbiddenResponse("ASSIGN_COMPENSATION capability required to view audit entries")

        logger.info { "[AUDIT-LIST] $callerId fetching audit entries for $tableName/$recordId" }
        return AuditLogRepository.findByTableAndRecord(tableName, recordId)
    }

    fun findFlagged(callerId: UUID): List<AuditLogEntry> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = "ASSIGN_COMPENSATION",
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) throw ForbiddenResponse("ASSIGN_COMPENSATION capability required to view flagged entries")

        logger.info { "[AUDIT-FLAGGED] $callerId fetching unacknowledged flagged entries" }
        return AuditLogRepository.findFlagged()
    }

    @Suppress("ThrowsCount")
    fun acknowledge(
        callerId: UUID,
        entryId: UUID,
    ): AuditLogEntry {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = "ASSIGN_COMPENSATION",
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) throw ForbiddenResponse("ASSIGN_COMPENSATION required to acknowledge audit entries")

        val updated = AuditLogRepository.acknowledge(entryId, callerId)
        if (!updated) throw NotFoundResponse("Audit entry not found or already acknowledged")

        return AuditLogRepository.findById(entryId)
            ?: throw NotFoundResponse("Audit entry not found")
    }
}
