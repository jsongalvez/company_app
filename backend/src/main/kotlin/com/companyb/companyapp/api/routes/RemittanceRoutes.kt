package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.service.finance.remittance.RemittanceDetail
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.finance.remittance.RemittanceSubmissionResult
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.time.LocalDate
import java.util.UUID

@Suppress("TooManyFunctions")
object RemittanceRoutes {
    fun register(config: JavalinConfig) {
        config.routes.before("/api/remittances") { context ->
            if (context.method() != io.javalin.http.HandlerType.POST) return@before
            val request = context.bodyAsClass<CreateRemittanceDraftRequest>()
            val branchId = uuidOrThrow(request.branchId, "branch id")
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.SUBMIT_REMITTANCE,
            )
        }

        config.routes.before("/api/remittances/{remittanceId}") { context ->
            val remittanceId = context.pathParamAsUuid("remittanceId")
            CapabilityFilter.requireBranchCapabilityForRemittance(context, remittanceId)
        }

        config.routes.post("/api/remittances", ::handleCreateDraft)
        config.routes.get("/api/remittances/{remittanceId}", ::handleGetRemittance)
        config.routes.post("/api/remittances/{remittanceId}/lines", ::handleAddLine)
        config.routes.delete("/api/remittances/{remittanceId}/lines/{lineId}", ::handleRemoveLine)
        config.routes.post("/api/remittances/{remittanceId}/day-breakdowns", ::handleAddDayBreakdown)
        config.routes.post("/api/remittances/{remittanceId}/submit", ::handleSubmit)
    }

    @Suppress("ThrowsCount")
    private fun handleCreateDraft(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateRemittanceDraftRequest>()

        val id = uuidOrThrow(request.id, "remittance id")
        val type =
            runCatching { RemittanceType.valueOf(request.type.uppercase()) }
                .getOrElse {
                    throw BadRequestResponse(
                        "Invalid remittance type: must be SESSION or PRODUCT",
                    )
                }
        val branchId = uuidOrThrow(request.branchId, "branch id")
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
        if (dateRangeEnd.isBefore(dateRangeStart)) {
            throw BadRequestResponse("dateRangeEnd must not be before dateRangeStart")
        }

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

    private fun handleGetRemittance(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")

        val detail = RemittanceService.getRemittance(remittanceId)
        context.json(detail.toResponse())
    }

    @Suppress("ThrowsCount")
    private fun handleAddLine(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<CreateRemittanceLineRequest>()

        val id = uuidOrThrow(request.id, "line id")
        val type =
            runCatching { RemittanceLineType.valueOf(request.type.uppercase()) }
                .getOrElse {
                    throw BadRequestResponse(
                        "Invalid remittance line type: must be SESSION or PRODUCT_SALE",
                    )
                }
        val sessionId = request.sessionId?.let { uuidOrThrow(it, "session id") }
        val productSaleId = request.productSaleId?.let { uuidOrThrow(it, "product sale id") }
        val amount = parsePositiveBigDecimal(request.amount, "amount")

        when (type) {
            RemittanceLineType.SESSION -> {
                if (sessionId == null) throw BadRequestResponse("sessionId is required for SESSION line type")
                if (productSaleId != null) throw BadRequestResponse("productSaleId must be null for SESSION line type")
            }

            RemittanceLineType.PRODUCT_SALE -> {
                if (productSaleId == null) {
                    throw BadRequestResponse("productSaleId is required for PRODUCT_SALE line type")
                }
                if (sessionId != null) throw BadRequestResponse("sessionId must be null for PRODUCT_SALE line type")
            }
        }

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

    private fun handleRemoveLine(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val lineId = context.pathParamAsUuid("lineId")

        val line = RemittanceService.removeLine(callerId, remittanceId, lineId)
        context.json(line.toResponse())
    }

    private fun handleAddDayBreakdown(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<AddDayBreakdownRequest>()

        val id = uuidOrThrow(request.id, "day breakdown id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")

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

    private fun handleSubmit(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<SubmitRemittanceRequest>()

        val result =
            RemittanceService.submit(
                callerId = callerId,
                remittanceId = remittanceId,
                expectedVersion = request.expectedVersion,
            )

        context.json(result.toSubmitResponse())
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

    private fun RemittanceSubmissionResult.toSubmitResponse(): RemittanceSubmitResponse =
        RemittanceSubmitResponse(
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
            grossIncome = grossIncome.toPlainString(),
            totalCompensation = totalCompensation.toPlainString(),
            totalExpenses = totalExpenses.toPlainString(),
            netIncome = netIncome.toPlainString(),
        )
}
