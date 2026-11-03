package com.companyb.companyapp.finance
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.requireMoneyAmount
import com.companyb.companyapp.api.routes.uuidFromQuery
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.finance.AllowanceResponse
import com.companyb.companyapp.contracts.finance.CreateAllowanceRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
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
    fun register(config: JavalinConfig) {
        fun allowancesGate(branchDayIdOf: (Context) -> UUID) =
            Gate.Before { Access.require(it, Act.PAY, Place.day(branchDayIdOf(it))) }

        config.post(
            ApiRoutes.ALLOWANCES,
            allowancesGate { uuidOrThrow(it.bodyAsClass<CreateAllowanceRequest>().branchDayId, "branch day id") },
        ) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateAllowanceRequest>()

            val id = uuidOrThrow(request.id, "allowance id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val userId = uuidOrThrow(request.userId, "user id")
            val amount = parseNonNegativeBigDecimal(request.amount, "amount")
            requireMoneyAmount(amount, "amount")
            Access.requirePayable(userId, Place.day(branchDayId))

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

        config.get(ApiRoutes.ALLOWANCES, allowancesGate { it.uuidFromQuery("branchDayId") }) { context ->
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
