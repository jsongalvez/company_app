package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.service.export.ExportService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Header
import io.javalin.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

object ExportRoutes {
    private const val MAX_MONTH = 12
    private const val MIN_MONTH = 1

    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.get("/api/branches/{branchId}/export/daily") { context ->
            handleDailyExport(context)
        }
        config.routes.get("/api/branches/{branchId}/export/monthly") { context ->
            handleMonthlyExport(context)
        }
        config.routes.get("/api/branches/{branchId}/export/all-time") { context ->
            handleAllTimeExport(context)
        }
        config.routes.get("/api/branches/export/provincial") { context ->
            handleBranchTypeExport(context, BranchType.PROVINCIAL_TOUR)
        }
        config.routes.get("/api/branches/export/medical-mission") { context ->
            handleBranchTypeExport(context, BranchType.MEDICAL_MISSION)
        }
    }

    private fun handleDailyExport(context: io.javalin.http.Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid("branchId")
        val dateParam =
            context.queryParam("date")
                ?: throw BadRequestResponse("date query param is required")
        val date =
            runCatching { LocalDate.parse(dateParam) }
                .getOrElse { throw BadRequestResponse("Invalid date format (expected yyyy-MM-dd)") }
        val format = ExportService.parseFormat(context.queryParam("format"))

        val result = ExportService.exportDaily(callerId, branchId, date, format)
        sendFileResponse(context, result)
    }

    private fun handleMonthlyExport(context: io.javalin.http.Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid("branchId")
        val year = parseRequiredInt(context, "year")
        val month = parseRequiredInt(context, "month")
        validateMonthRange(month)
        val format = ExportService.parseFormat(context.queryParam("format"))

        val result = ExportService.exportMonthly(callerId, branchId, year, month, format)
        sendFileResponse(context, result)
    }

    private fun handleAllTimeExport(context: io.javalin.http.Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchId = context.pathParamAsUuid("branchId")
        val format = ExportService.parseFormat(context.queryParam("format"))

        val result = ExportService.exportAllTime(callerId, branchId, format)
        sendFileResponse(context, result)
    }

    private fun handleBranchTypeExport(
        context: io.javalin.http.Context,
        branchType: BranchType,
    ) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val year = context.queryParam("year")?.toIntOrNull()
        val month = context.queryParam("month")?.toIntOrNull()
        validateOptionalMonth(year, month)
        val format = ExportService.parseFormat(context.queryParam("format"))

        val result = ExportService.exportByBranchType(callerId, branchType, year, month, format)
        sendFileResponse(context, result)
    }

    private fun sendFileResponse(
        context: io.javalin.http.Context,
        result: com.companyb.companyapp.service.export.ExportResult,
    ) {
        context.status(HttpStatus.OK)
        context.header(Header.CONTENT_TYPE, result.contentType)
        context.header("Content-Disposition", "attachment; filename=\"${result.fileName}\"")
        context.result(result.bytes)
    }

    private fun parseRequiredInt(
        context: io.javalin.http.Context,
        paramName: String,
    ): Int {
        val param =
            context.queryParam(paramName)
                ?: throw BadRequestResponse("$paramName query param is required")
        return runCatching { param.toInt() }
            .getOrElse { throw BadRequestResponse("Invalid $paramName format") }
    }

    private fun validateMonthRange(month: Int) {
        if (month < MIN_MONTH || month > MAX_MONTH) {
            throw BadRequestResponse("month must be between 1 and 12")
        }
    }

    @Suppress("ComplexCondition")
    private fun validateOptionalMonth(
        year: Int?,
        month: Int?,
    ) {
        if ((year == null) != (month == null)) {
            throw BadRequestResponse("Both year and month must be provided together, or neither")
        }
        if (year != null && month != null && (month < MIN_MONTH || month > MAX_MONTH)) {
            throw BadRequestResponse("month must be between 1 and 12")
        }
    }
}
