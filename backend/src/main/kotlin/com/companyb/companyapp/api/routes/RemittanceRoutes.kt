package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.service.RemittanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.time.LocalDate
import java.util.UUID

object RemittanceRoutes {
    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/remittances") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateRemittanceDraftRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance id") }
            val type =
                runCatching { RemittanceType.valueOf(request.type.uppercase()) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance type: must be SESSION or PRODUCT") }
            val branchId =
                runCatching { UUID.fromString(request.branchId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val method =
                runCatching { RemittanceMethod.valueOf(request.method.uppercase()) }
                    .getOrElse {
                        throw BadRequestResponse(
                            "Invalid remittance method: must be BANK_TRANSFER or HANDED_TO_ACCOUNTANT",
                        )
                    }
            val dateRangeStart =
                runCatching { LocalDate.parse(request.dateRangeStart) }
                    .getOrElse { throw BadRequestResponse("Invalid dateRangeStart") }
            val dateRangeEnd =
                runCatching { LocalDate.parse(request.dateRangeEnd) }
                    .getOrElse { throw BadRequestResponse("Invalid dateRangeEnd") }

            val remittance =
                RemittanceService.createDraft(
                    callerId = callerId,
                    id = id,
                    type = type,
                    branchId = branchId,
                    method = method,
                    dateRangeStart = dateRangeStart,
                    dateRangeEnd = dateRangeEnd,
                )

            context.status(HttpStatus.CREATED)
            context.json(remittance.toResponse())
        }
    }

    private fun Remittance.toResponse(): RemittanceResponse =
        RemittanceResponse(
            id = id.toString(),
            type = type.name,
            status = status.name,
            branchId = branchId.toString(),
            method = method.name,
            submittedDate = submittedDate.toString(),
            submittedBy = submittedBy.toString(),
            dateRangeStart = dateRangeStart.toString(),
            dateRangeEnd = dateRangeEnd.toString(),
            createdAt = createdAt.toString(),
            version = version,
        )
}
