package com.companyb.companyapp.audit

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.parseBrowseLimit
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.audit.AuditLogBrowseResponse
import com.companyb.companyapp.contracts.audit.AuditLogEntryResponse
import com.companyb.companyapp.contracts.audit.AuditLogTableResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OpenApi(
    path = ApiRoutes.AUDIT_LOG,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(
            name = "tableName",
            type = String::class,
            required = true,
        ), OpenApiParam(name = "recordId", type = String::class, required = true),
    ],
    operationId = "audit_log",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<AuditLogEntryResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_ENTRIES_PATH,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(
            name = "tableName",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "action",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "callerName",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "dateFrom",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "dateTo",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "cursor",
            type = String::class,
            required = false,
        ), OpenApiParam(name = "limit", type = String::class, required = false),
    ],
    operationId = "audit_log_entries",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = AuditLogBrowseResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_FLAGGED_PATH,
    methods = [HttpMethod.GET],
    operationId = "audit_log_flagged",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<AuditLogEntryResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_TABLES_PATH,
    methods = [HttpMethod.GET],
    operationId = "audit_log_tables",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<AuditLogTableResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.AUDIT_LOG_ACKNOWLEDGE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "entryId", type = UUID::class, required = true)],
    operationId = "audit_log_acknowledge",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = AuditLogEntryResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object AuditLogRoutes {
    fun register(config: JavalinConfig) {
        // No route gate (D9: always-visible; backend-authoritative read scoping
        // inside the service). The pre-#122 GLOBAL ASSIGN_COMPENSATION filter was
        // broken by construction (#104 F2) and is removed here.

        config.routes.get(ApiRoutes.AUDIT_LOG_ENTRIES_PATH) { context ->
            val response = handleBrowse(context)
            context.status(HttpStatus.OK)
            context.json(response)
        }

        config.routes.get(ApiRoutes.AUDIT_LOG_TABLES_PATH) { context ->
            val tables: List<AuditLogTableResponse> = AuditLogService.listTables()
            context.status(HttpStatus.OK)
            context.json(tables)
        }

        config.routes.get(ApiRoutes.AUDIT_LOG) { context ->
            context.json(handleHistory(context))
        }

        config.routes.get(ApiRoutes.AUDIT_LOG_FLAGGED_PATH) { context ->
            val callerId = context.callerUuid()

            val entries = AuditLogService.findFlagged(callerId)

            context.status(HttpStatus.OK)
            context.json(entries.map { it.toResponse() })
        }

        config.routes.patch(ApiRoutes.AUDIT_LOG_ACKNOWLEDGE_PATH) { context ->
            context.json(handleAcknowledge(context))
        }
    }

    private fun handleBrowse(context: io.javalin.http.Context): AuditLogBrowseResponse {
        val callerId = context.callerUuid()
        val tableName = context.queryParam("tableName")
        val action = parseAction(context.queryParam("action"))
        val callerName = context.queryParam("callerName")
        val dateFromRaw = context.queryParam("dateFrom")
        val dateToRaw = context.queryParam("dateTo")
        requireValidDateRange(dateFromRaw, dateToRaw)
        val dateFrom = parseStartOfDay(dateFromRaw)
        val dateTo = parseExclusiveEndOfDay(dateToRaw)
        val cursor = parseCursor(context.queryParam("cursor"))
        val limit = parseBrowseLimit(context.queryParam("limit"))

        return AuditLogService.browse(
            callerId = callerId,
            tableName = tableName,
            action = action,
            callerName = callerName,
            dateFrom = dateFrom,
            dateTo = dateTo,
            cursor = cursor,
            limit = limit,
        )
    }

    private fun handleHistory(context: io.javalin.http.Context): List<AuditLogEntryResponse> {
        val callerId = context.callerUuid()
        val (tableName, recordId) = requireTableAndRecord(context)

        val entries = AuditLogService.findByTableAndRecord(callerId, tableName, recordId)
        context.status(HttpStatus.OK)
        return entries.map { it.toResponse() }
    }

    private fun handleAcknowledge(context: io.javalin.http.Context): AuditLogEntryResponse {
        val callerId = context.callerUuid()
        val entryId = context.pathParamAsUuid("entryId")

        val entry: com.companyb.companyapp.audit.AuditLogEntry =
            AuditLogService.acknowledge(callerId, entryId)

        context.status(HttpStatus.OK)
        return entry.toResponse()
    }

    private fun requireValidDateRange(
        dateFromRaw: String?,
        dateToRaw: String?,
    ) {
        if (dateFromRaw != null && dateToRaw != null) {
            val from = parseLocalDate(dateFromRaw)
            val to = parseLocalDate(dateToRaw)
            if (from.isAfter(to)) throw BadRequestResponse("dateFrom must be before dateTo")
        }
    }

    private fun requireTableAndRecord(context: io.javalin.http.Context): Pair<String, UUID> {
        val tableName = context.queryParam("tableName") ?: throw BadRequestResponse("tableName is required")
        val recordIdParam = context.queryParam("recordId") ?: throw BadRequestResponse("recordId is required")
        return tableName to uuidOrThrow(recordIdParam, "recordId")
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

    private fun parseCursor(raw: String?): com.companyb.companyapp.audit.AuditBrowseCursor? {
        if (raw == null) return null
        return runCatching { decodeCursor(raw) }
            .getOrElse { throw BadRequestResponse("Invalid cursor") }
    }
}
