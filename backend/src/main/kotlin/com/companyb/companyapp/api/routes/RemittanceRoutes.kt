package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.service.RemittanceDetail
import com.companyb.companyapp.service.RemittanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object RemittanceRoutes {
    @Suppress("ThrowsCount", "LongMethod", "MaxLineLength")
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

        config.routes.get("/api/remittances/{remittanceId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val remittanceId =
                runCatching { UUID.fromString(context.pathParam("remittanceId")) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance id") }

            val detail = RemittanceService.getRemittance(callerId, remittanceId)
            context.json(detail.toResponse())
        }

        config.routes.post("/api/remittances/{remittanceId}/lines") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val remittanceId =
                runCatching { UUID.fromString(context.pathParam("remittanceId")) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance id") }
            val request = context.bodyAsClass<CreateRemittanceLineRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid line id") }
            val type =
                runCatching { RemittanceLineType.valueOf(request.type.uppercase()) }
                    .getOrElse {
                        throw BadRequestResponse(
                            "Invalid remittance line type: must be SESSION or PRODUCT_SALE",
                        )
                    }
            val sessionId =
                request.sessionId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid session id") }
                }
            val productSaleId =
                request.productSaleId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid product sale id") }
                }
            val amount =
                runCatching { BigDecimal(request.amount) }
                    .getOrElse { throw BadRequestResponse("Invalid amount") }

            val line =
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittanceId,
                    id = id,
                    type = type,
                    sessionId = sessionId,
                    productSaleId = productSaleId,
                    amount = amount,
                )

            context.status(HttpStatus.CREATED)
            context.json(line.toResponse())
        }

        config.routes.delete("/api/remittances/{remittanceId}/lines/{lineId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val remittanceId =
                runCatching { UUID.fromString(context.pathParam("remittanceId")) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance id") }
            val lineId =
                runCatching { UUID.fromString(context.pathParam("lineId")) }
                    .getOrElse { throw BadRequestResponse("Invalid line id") }

            val line = RemittanceService.removeLine(callerId, remittanceId, lineId)
            context.json(line.toResponse())
        }

        config.routes.post("/api/remittances/{remittanceId}/day-breakdowns") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val remittanceId =
                runCatching { UUID.fromString(context.pathParam("remittanceId")) }
                    .getOrElse { throw BadRequestResponse("Invalid remittance id") }
            val request = context.bodyAsClass<AddDayBreakdownRequest>()

            val id =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid day breakdown id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }

            val breakdown =
                RemittanceService.addDayBreakdown(
                    callerId = callerId,
                    remittanceId = remittanceId,
                    id = id,
                    branchDayId = branchDayId,
                )

            context.status(HttpStatus.CREATED)
            context.json(breakdown.toResponse())
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

    private fun RemittanceLine.toResponse(): RemittanceLineResponse =
        RemittanceLineResponse(
            id = id.toString(),
            remittanceId = remittanceId.toString(),
            type = type.name,
            sessionId = sessionId?.toString(),
            productSaleId = productSaleId?.toString(),
            createdBy = createdBy.toString(),
            createdAt = createdAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
            amount = amount.toPlainString(),
        )

    private fun RemittanceDayBreakdown.toResponse(): RemittanceDayBreakdownResponse =
        RemittanceDayBreakdownResponse(
            id = id.toString(),
            remittanceId = remittanceId.toString(),
            branchDayId = branchDayId.toString(),
        )

    private fun RemittanceDetail.toResponse(): RemittanceDetailResponse =
        RemittanceDetailResponse(
            id = remittance.id.toString(),
            type = remittance.type.name,
            status = remittance.status.name,
            branchId = remittance.branchId.toString(),
            method = remittance.method.name,
            submittedDate = remittance.submittedDate.toString(),
            submittedBy = remittance.submittedBy.toString(),
            dateRangeStart = remittance.dateRangeStart.toString(),
            dateRangeEnd = remittance.dateRangeEnd.toString(),
            createdAt = remittance.createdAt.toString(),
            version = remittance.version,
            lines = lines.map { it.toResponse() },
            totalAmount = totalAmount.toPlainString(),
            dayBreakdowns = dayBreakdowns.map { it.toResponse() },
        )
}
