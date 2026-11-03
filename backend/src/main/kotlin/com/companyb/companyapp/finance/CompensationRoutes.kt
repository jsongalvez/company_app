package com.companyb.companyapp.finance
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.patch
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.requireMoneyAmount
import com.companyb.companyapp.api.routes.uuidFromQuery
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.finance.CompensationResponse
import com.companyb.companyapp.contracts.finance.CreateCompensationRequest
import com.companyb.companyapp.contracts.finance.UpdateCompensationRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HandlerType
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
    path = ApiRoutes.COMPENSATION,
    methods = [HttpMethod.POST],
    operationId = "compensation_create",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateCompensationRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = CompensationResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.COMPENSATION_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "compensationId", type = UUID::class, required = true)],
    operationId = "compensation_update",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateCompensationRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = CompensationResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.COMPENSATIONS,
    methods = [HttpMethod.GET],
    queryParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "compensations",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<CompensationResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object CompensationRoutes {
    fun register(config: JavalinConfig) {
        registerReads(config)
        registerMutations(config)
    }

    private val compensationCreateGate =
        Gate.Before { context ->
            val request = context.bodyAsClass<CreateCompensationRequest>()
            Access.require(context, Act.PAY, Place.day(uuidOrThrow(request.workBranchDayId, "work branch day id")))
        }

    private val compensationsGate =
        Gate.Before { context ->
            val branchDayId = context.uuidFromQuery("branchDayId")
            Access.require(context, Act.PAY, Place.day(branchDayId))
        }

    private val compensationGate =
        Gate.Before { context ->
            val compensationId = context.pathParamAsUuid("compensationId")
            val compensation =
                FinanceReads.findCompensationById(compensationId)
                    ?: throw io.javalin.http.NotFoundResponse("Compensation not found")
            Access.require(context, Act.PAY, Place.day(compensation.workBranchDayId))
        }

    private fun registerReads(config: JavalinConfig) {
        config.get(ApiRoutes.COMPENSATIONS, compensationsGate) { context ->
            val branchDayId = context.uuidFromQuery("branchDayId")
            val compensations = CompensationService.findByPayingBranchDayId(branchDayId)
            context.json(compensations.map { it.toResponse() })
        }
    }

    private fun registerMutations(config: JavalinConfig) {
        config.post(ApiRoutes.COMPENSATION, compensationCreateGate) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateCompensationRequest>()

            val id = uuidOrThrow(request.id, "compensation id")
            val workBranchDayId = uuidOrThrow(request.workBranchDayId, "work branch day id")
            val payingBranchDayId = uuidOrThrow(request.payingBranchDayId, "paying branch day id")
            val userId = uuidOrThrow(request.userId, "user id")
            val amount = parseNonNegativeBigDecimal(request.amount, "amount")
            requireMoneyAmount(amount, "amount")
            Access.requirePayable(userId, Place.day(workBranchDayId))

            val compensation =
                CompensationService.create(
                    callerId = callerId,
                    id = id,
                    workBranchDayId = workBranchDayId,
                    payingBranchDayId = payingBranchDayId,
                    userId = userId,
                    amount = amount,
                    note = request.note,
                    reason = request.reason,
                )

            context.status(HttpStatus.CREATED)
            context.json(compensation.toResponse())
        }

        config.patch(ApiRoutes.COMPENSATION_PATH, compensationGate) { context ->
            val callerId = context.callerUuid()
            val compensationId = context.pathParamAsUuid("compensationId")
            val request = context.bodyAsClass<UpdateCompensationRequest>()

            val amount = parseNonNegativeBigDecimal(request.amount, "amount")
            requireMoneyAmount(amount, "amount")

            val compensation =
                CompensationService.update(
                    callerId = callerId,
                    compensationId = compensationId,
                    amount = amount,
                    note = request.note,
                    expectedVersion = request.expectedVersion,
                    reason = request.reason,
                )

            context.status(HttpStatus.OK)
            context.json(compensation.toResponse())
        }
    }

    private fun Compensation.toResponse(): CompensationResponse =
        CompensationResponse(
            id = id.toString(),
            workBranchDayId = workBranchDayId.toString(),
            payingBranchDayId = payingBranchDayId.toString(),
            userId = userId.toString(),
            amount = amount.toPlainString(),
            assignedBy = assignedBy.toString(),
            assignedAt = assignedAt.toString(),
            note = note,
            version = version,
        )

    private fun CompensationWithUser.toResponse(): CompensationResponse =
        compensation.toResponse().copy(userName = userName)
}
