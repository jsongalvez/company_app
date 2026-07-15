package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.repository.model.CapabilityContextType
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
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        logger.info { "[AUDIT-LIST] $callerId fetching audit entries for $tableName/$recordId" }
        return AuditLogRepository.findByTableAndRecord(tableName, recordId)
    }

    fun findFlagged(callerId: UUID): List<AuditLogEntry> {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        logger.info { "[AUDIT-FLAGGED] $callerId fetching unacknowledged flagged entries" }
        return AuditLogRepository.findFlagged()
    }

    @Suppress("ThrowsCount")
    fun acknowledge(
        callerId: UUID,
        entryId: UUID,
    ): AuditLogEntry {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        val updated = AuditLogRepository.acknowledge(entryId, callerId)
        if (!updated) throw NotFoundResponse("Audit entry not found or already acknowledged")

        return AuditLogRepository.findById(entryId)
            ?: throw NotFoundResponse("Audit entry not found")
    }
}
