package com.companyb.companyapp.reporting
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCHES_EXPORT_REPORT_PATH,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
        OpenApiParam(name = "format", type = String::class, required = true),
        OpenApiParam(name = "type", type = String::class, required = false),
    ],
    operationId = "export_report_multi_branch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(mimeType = "application/octet-stream", type = "string", format = "binary")],
        ),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_EXPORT_REPORT_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
        OpenApiParam(name = "format", type = String::class, required = true),
    ],
    operationId = "export_report",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(mimeType = "application/octet-stream", type = "string", format = "binary")],
        ),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ExportRoutes {
    fun register(config: JavalinConfig) {
        // lesson: the wildcard is what makes the gate run on the 5-segment branch
        // export route (ReportsReadScopeAuthzTest proves 403 for zero-grant callers).
        config.routes.before("/api/branches/{branchId}/export/*") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            // 404 precedence for an unknown branch before the capability gate (precedent).
            BranchService.findById(branchId)
            Access.require(context, Act.SEE_BRANCH, Place.Branch(branchId))
        }

        config.routes.get(ApiRoutes.BRANCH_EXPORT_REPORT_PATH) { context ->
            val (from, to) = parseRange(context)
            val format = parseFormat(context.queryParam("format"))
            sendFileResponse(
                context,
                ExportService.exportBranchReport(context.pathParamAsUuid("branchId"), from, to, format),
            )
        }
        // JWT-only: the report includes only the branches the caller may read.
        config.routes.get(ApiRoutes.BRANCHES_EXPORT_REPORT_PATH) { context ->
            val (from, to) = parseRange(context)
            val format = parseFormat(context.queryParam("format"))
            val type = parseType(context.queryParam("type"))
            sendFileResponse(
                context,
                ExportService.exportMultiBranchReport(context.callerUuid(), type, from, to, format),
            )
        }
    }

    private fun parseRange(context: Context): Pair<LocalDate, LocalDate> {
        val from = parseRequiredDate(context, "from")
        val to = parseRequiredDate(context, "to")
        if (from.isAfter(to)) throw BadRequestResponse("from must be on or before to")
        return from to to
    }

    private fun parseType(param: String?): BranchType? =
        when (param?.uppercase()) {
            null, "", "ALL" -> null
            "CLINIC" -> BranchType.CLINIC
            "PROVINCIAL_TOUR" -> BranchType.PROVINCIAL_TOUR
            "MEDICAL_MISSION" -> BranchType.MEDICAL_MISSION
            else -> throw BadRequestResponse("type must be ALL, CLINIC, PROVINCIAL_TOUR or MEDICAL_MISSION")
        }

    private fun parseRequiredDate(
        context: Context,
        paramName: String,
    ): LocalDate {
        val param =
            context.queryParam(paramName)
                ?: throw BadRequestResponse("$paramName query param is required")
        return runCatching { LocalDate.parse(param) }
            .getOrElse { throw BadRequestResponse("Invalid $paramName format (expected yyyy-MM-dd)") }
    }

    private fun sendFileResponse(
        context: Context,
        result: ExportResult,
    ) {
        context.status(HttpStatus.OK)
        context.header(Header.CONTENT_TYPE, result.contentType)
        context.header("Content-Disposition", "attachment; filename=\"${result.fileName}\"")
        context.result(result.bytes)
    }

    private fun parseFormat(formatParam: String?): ExportFormat =
        when (formatParam?.lowercase()) {
            "xlsx" -> ExportFormat.XLSX
            "pdf" -> ExportFormat.PDF
            else -> throw BadRequestResponse("format query param is required (xlsx or pdf)")
        }
}
