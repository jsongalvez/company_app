package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.service.CompensationService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.util.UUID

object CompensationRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/compensation") { context ->
            val branchDayId =
                when (context.method()) {
                    io.javalin.http.HandlerType.POST -> {
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
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }
            if (amount < BigDecimal.ZERO) throw BadRequestResponse("Amount must be non-negative")

            val compensation =
                CompensationService.create(
                    callerId = callerId,
                    id = id,
                    workBranchDayId = workBranchDayId,
                    payingBranchDayId = payingBranchDayId,
                    userId = userId,
                    amount = amount,
                    note = request.note,
                )

            context.status(HttpStatus.CREATED)
            context.json(compensation.toResponse())
        }

        config.routes.patch("/api/compensation/{compensationId}") { context ->
            val callerId = context.callerUuid()
            val compensationId = context.pathParamAsUuid("compensationId")
            val request = context.bodyAsClass<UpdateCompensationRequest>()

            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }
            if (amount < BigDecimal.ZERO) throw BadRequestResponse("Amount must be non-negative")

            val compensation =
                CompensationService.update(
                    callerId = callerId,
                    compensationId = compensationId,
                    amount = amount,
                    note = request.note,
                    expectedVersion = request.expectedVersion,
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
}
