package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.service.AllowanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.util.UUID

object AllowanceRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/allowances") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateAllowanceRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid allowance id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }
            val userId =
                runCatching { UUID.fromString(request.userId) }
                    .getOrElse { throw BadRequestResponse("Invalid user id") }
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }

            val allowance =
                AllowanceService.create(
                    callerId = callerId,
                    id = id,
                    branchDayId = branchDayId,
                    userId = userId,
                    amount = amount,
                )

            context.status(HttpStatus.CREATED)
            context.json(allowance.toResponse())
        }

        config.routes.get("/api/allowances") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchDayId =
                runCatching { UUID.fromString(context.queryParam("branchDayId")!!) }
                    .getOrElse { throw BadRequestResponse("Invalid or missing branchDayId query parameter") }

            val allowances = AllowanceService.findByBranchDayId(callerId, branchDayId)
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
