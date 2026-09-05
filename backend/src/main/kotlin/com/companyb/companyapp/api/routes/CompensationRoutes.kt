package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.repository.CompensationWithUser
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.service.CompensationService
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
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.COMPENSATION) { context ->
            val branchDayId =
                when (context.method()) {
                    HandlerType.POST -> {
                        val request = context.bodyAsClass<CreateCompensationRequest>()
                        uuidOrThrow(request.workBranchDayId, "work branch day id")
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

        config.routes.before(ApiRoutes.COMPENSATIONS) { context ->
            if (context.method() != HandlerType.GET) {
                return@before
            }
            val branchDayId = context.uuidFromQuery("branchDayId")
            CapabilityFilter.requireBranchCapability(
                context,
                branchDayId,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.before(ApiRoutes.COMPENSATION_PATH) { context ->
            val compensationId = context.pathParamAsUuid("compensationId")
            val compensation =
                com.companyb.companyapp.repository.CompensationRepository
                    .findById(compensationId)
                    ?: throw io.javalin.http.NotFoundResponse("Compensation not found")
            CapabilityFilter.requireBranchCapability(
                context,
                compensation.workBranchDayId,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.post(ApiRoutes.COMPENSATION) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateCompensationRequest>()

            val id = uuidOrThrow(request.id, "compensation id")
            val workBranchDayId = uuidOrThrow(request.workBranchDayId, "work branch day id")
            val payingBranchDayId = uuidOrThrow(request.payingBranchDayId, "paying branch day id")
            val userId = uuidOrThrow(request.userId, "user id")
            val amount = parseNonNegativeBigDecimal(request.amount, "amount")

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

        config.routes.get(ApiRoutes.COMPENSATIONS) { context ->
            val branchDayId = context.uuidFromQuery("branchDayId")
            val compensations = CompensationService.findByPayingBranchDayId(branchDayId)
            context.json(compensations.map { it.toResponse() })
        }

        config.routes.patch(ApiRoutes.COMPENSATION_PATH) { context ->
            val callerId = context.callerUuid()
            val compensationId = context.pathParamAsUuid("compensationId")
            val request = context.bodyAsClass<UpdateCompensationRequest>()

            val amount = parseNonNegativeBigDecimal(request.amount, "amount")

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
