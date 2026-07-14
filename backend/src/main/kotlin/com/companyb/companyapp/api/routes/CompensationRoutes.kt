package com.companyb.companyapp.api.routes

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
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/compensation") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateCompensationRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid compensation id") }
            val workBranchDayId =
                runCatching { UUID.fromString(request.workBranchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid work branch day id") }
            val payingBranchDayId =
                runCatching { UUID.fromString(request.payingBranchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid paying branch day id") }
            val userId =
                runCatching { UUID.fromString(request.userId) }
                    .getOrElse { throw BadRequestResponse("Invalid user id") }
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }

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
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val compensationId =
                runCatching { UUID.fromString(context.pathParam("compensationId")) }
                    .getOrElse { throw BadRequestResponse("Invalid compensation id") }
            val request = context.bodyAsClass<UpdateCompensationRequest>()

            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }

            val compensation =
                CompensationService.update(
                    callerId = callerId,
                    compensationId = compensationId,
                    amount = amount,
                    note = request.note,
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
        )
}
