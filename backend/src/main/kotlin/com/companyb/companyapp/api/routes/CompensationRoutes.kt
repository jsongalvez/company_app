package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateCompensationRequest
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
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/compensation",
    methods = [HttpMethod.POST],
    operationId = "compensation_create",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/compensation/{compensationId}",
    methods = [HttpMethod.PATCH],
    operationId = "compensation_update",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/compensations",
    methods = [HttpMethod.GET],
    operationId = "compensations",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object CompensationRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/compensation") { context ->
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

        config.routes.before("/api/compensations") { context ->
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

        config.routes.before("/api/compensation/{compensationId}") { context ->
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

        config.routes.post("/api/compensation") { context ->
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

        config.routes.get("/api/compensations") { context ->
            val branchDayId = context.uuidFromQuery("branchDayId")
            val compensations = CompensationService.findByPayingBranchDayId(branchDayId)
            context.json(compensations.map { it.toResponse() })
        }

        config.routes.patch("/api/compensation/{compensationId}") { context ->
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
