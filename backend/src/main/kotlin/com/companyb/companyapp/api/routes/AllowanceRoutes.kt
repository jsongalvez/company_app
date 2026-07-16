package com.companyb.companyapp.api.routes

import com.companyb.companyapp.domain.CapabilityCodes
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
        config.routes.before("/api/allowances") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.post("/api/allowances") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateAllowanceRequest>()

            val id = uuidOrThrow(request.id, "allowance id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val userId = uuidOrThrow(request.userId, "user id")
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
