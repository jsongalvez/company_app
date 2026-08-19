package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogBrowseResponse
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.repository.decodeCursor
import com.companyb.companyapp.service.AuditLogService
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.toResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OpenApi(
    path = ApiRoutes.AUDIT_LOG,
    methods = [HttpMethod.GET],
    operationId = "audit_log",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_ENTRIES_PATH,
    methods = [HttpMethod.GET],
    operationId = "audit_log_entries",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_FLAGGED_PATH,
    methods = [HttpMethod.GET],
    operationId = "audit_log_flagged",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_TABLES_PATH,
    methods = [HttpMethod.GET],
    operationId = "audit_log_tables",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_ACKNOWLEDGE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "entryId", type = UUID::class, required = true)],
    operationId = "audit_log_acknowledge",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object AuditLogRoutes {
    @Suppress("LongMethod", "ThrowsCount")
    fun register(config: JavalinConfig) {
        // No route gate (D9: always-visible; backend-authoritative read scoping
        // inside the service). The pre-#122 GLOBAL ASSIGN_COMPENSATION filter was
        // broken by construction (#104 F2) and is removed here.

        config.routes.get(ApiRoutes.AUDIT_LOG_ENTRIES_PATH) { context ->
            val callerId = context.callerUuid()
            val tableName = context.queryParam("tableName")
            val action = parseAction(context.queryParam("action"))
            val callerName = context.queryParam("callerName")
            val dateFromRaw = context.queryParam("dateFrom")
            val dateToRaw = context.queryParam("dateTo")
            if (dateFromRaw != null && dateToRaw != null) {
                val from = parseLocalDate(dateFromRaw)
                val to = parseLocalDate(dateToRaw)
                if (from.isAfter(to)) throw BadRequestResponse("dateFrom must be before dateTo")
            }
            val dateFrom = parseStartOfDay(dateFromRaw)
            val dateTo = parseExclusiveEndOfDay(dateToRaw)
            val cursor = parseCursor(context.queryParam("cursor"))
            val limit = parseBrowseLimit(context.queryParam("limit"))

            val response: AuditLogBrowseResponse =
                AuditLogService.browse(
                    callerId = callerId,
                    tableName = tableName,
                    action = action,
                    callerName = callerName,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    cursor = cursor,
                    limit = limit,
                )
            context.status(HttpStatus.OK)
            context.json(response)
        }

        config.routes.get(ApiRoutes.AUDIT_LOG_TABLES_PATH) { context ->
            val tables: List<AuditLogTableResponse> = AuditLogService.listTables()
            context.status(HttpStatus.OK)
            context.json(tables)
        }

        config.routes.get(ApiRoutes.AUDIT_LOG) { context ->
            val callerId = context.callerUuid()
            val tableName = context.queryParam("tableName") ?: throw BadRequestResponse("tableName is required")
            val recordIdParam = context.queryParam("recordId") ?: throw BadRequestResponse("recordId is required")
            val recordId = uuidOrThrow(recordIdParam, "recordId")

            val entries = AuditLogService.findByTableAndRecord(callerId, tableName, recordId)

            context.status(HttpStatus.OK)
            context.json(entries.map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.AUDIT_LOG_FLAGGED_PATH) { context ->
            val callerId = context.callerUuid()

            val entries = AuditLogService.findFlagged(callerId)

            context.status(HttpStatus.OK)
            context.json(entries.map { it.toResponse() })
        }

        config.routes.patch(ApiRoutes.AUDIT_LOG_ACKNOWLEDGE_PATH) { context ->
            val callerId = context.callerUuid()
            val entryId = context.pathParamAsUuid("entryId")

            val entry: com.companyb.companyapp.repository.model.AuditLogEntry =
                AuditLogService.acknowledge(callerId, entryId)

            context.status(HttpStatus.OK)
            context.json(entry.toResponse())
        }
    }

    private fun parseAction(raw: String?): AuditAction? {
        if (raw == null) return null
        return runCatching { AuditAction.valueOf(raw) }
            .getOrElse { throw BadRequestResponse("Invalid action: $raw") }
    }

    /** Date params are inclusive Manila calendar days (`yyyy-MM-dd`). */
    private fun parseStartOfDay(raw: String?): OffsetDateTime? {
        if (raw == null) return null
        val date = parseLocalDate(raw)
        return date.atStartOfDay(BranchDayService.manilaZone).toOffsetDateTime()
    }

    /** Upper bound is exclusive — the day after `dateTo`, start of day. */
    private fun parseExclusiveEndOfDay(raw: String?): OffsetDateTime? {
        if (raw == null) return null
        val date = parseLocalDate(raw)
        return date.plusDays(1).atStartOfDay(BranchDayService.manilaZone).toOffsetDateTime()
    }

    private fun parseLocalDate(raw: String): LocalDate =
        runCatching { LocalDate.parse(raw) }
            .getOrElse { throw BadRequestResponse("Invalid date: $raw") }

    private fun parseCursor(raw: String?): com.companyb.companyapp.repository.AuditBrowseCursor? {
        if (raw == null) return null
        return runCatching { decodeCursor(raw) }
            .getOrElse { throw BadRequestResponse("Invalid cursor") }
    }
}
