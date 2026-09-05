package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.service.AllowanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.ALLOWANCES,
    methods = [HttpMethod.GET],
    queryParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "allowances_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<AllowanceResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.ALLOWANCES,
    methods = [HttpMethod.POST],
    operationId = "allowances_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateAllowanceRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = AllowanceResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object AllowanceRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.ALLOWANCES) { context ->
            val branchDayId =
                when (context.method()) {
                    io.javalin.http.HandlerType.POST -> {
                        val request = context.bodyAsClass<CreateAllowanceRequest>()
                        uuidOrThrow(request.branchDayId, "branch day id")
                    }

                    io.javalin.http.HandlerType.GET -> {
                        context.uuidFromQuery("branchDayId")
                    }

                    else -> {
                        return@before
                    }
                }
            CapabilityFilter.requireBranchCapability(
                context,
                branchDayId,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.post(ApiRoutes.ALLOWANCES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateAllowanceRequest>()

            val id = uuidOrThrow(request.id, "allowance id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val userId = uuidOrThrow(request.userId, "user id")
            val amount = parseNonNegativeBigDecimal(request.amount, "amount")

            val allowance =
                AllowanceService.create(
                    callerId = callerId,
                    id = id,
                    branchDayId = branchDayId,
                    userId = userId,
                    amount = amount,
                    reason = request.reason,
                )

            context.status(HttpStatus.CREATED)
            context.json(allowance.toResponse())
        }

        config.routes.get(ApiRoutes.ALLOWANCES) { context ->
            val branchDayId = context.uuidFromQuery("branchDayId")

            val allowances = AllowanceService.findByBranchDayId(branchDayId)
            context.json(allowances.map { it.toResponse() })
        }
    }

    private fun Allowance.toResponse(): AllowanceResponse =
        AllowanceResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            userId = userId.toString(),
            amount = amount.toPlainString(),
            assignedBy = assignedBy.toString(),
            assignedAt = assignedAt.toString(),
        )
}
