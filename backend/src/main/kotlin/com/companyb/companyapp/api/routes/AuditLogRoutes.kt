package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.repository.model.AuditLogEntry
import com.companyb.companyapp.service.AuditLogService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.util.UUID

object AuditLogRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/audit-log") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.get("/api/audit-log") { context ->
            val callerId = context.callerUuid()
            val tableName = context.queryParam("tableName") ?: throw BadRequestResponse("tableName is required")
            val recordIdParam = context.queryParam("recordId") ?: throw BadRequestResponse("recordId is required")
            val recordId = uuidOrThrow(recordIdParam, "recordId")

            val entries = AuditLogService.findByTableAndRecord(callerId, tableName, recordId)

            context.status(HttpStatus.OK)
            context.json(entries.map { it.toResponse() })
        }

        config.routes.get("/api/audit-log/flagged") { context ->
            val callerId = context.callerUuid()

            val entries = AuditLogService.findFlagged(callerId)

            context.status(HttpStatus.OK)
            context.json(entries.map { it.toResponse() })
        }

        config.routes.patch("/api/audit-log/{entryId}/acknowledge") { context ->
            val callerId = context.callerUuid()
            val entryId = context.pathParamAsUuid("entryId")

            val entry = AuditLogService.acknowledge(callerId, entryId)

            context.status(HttpStatus.OK)
            context.json(entry.toResponse())
        }
    }

    private fun AuditLogEntry.toResponse(): AuditLogEntryResponse =
        AuditLogEntryResponse(
            id = id.toString(),
            tableName = tableName,
            recordId = recordId.toString(),
            action = action.name,
            changedBy = changedBy.toString(),
            changedAt = changedAt.toString(),
            oldValue = oldValue,
            newValue = newValue,
            isFlagged = isFlagged,
            reason = reason,
            acknowledgedBy = acknowledgedBy?.toString(),
            acknowledgedAt = acknowledgedAt?.toString(),
        )
}
