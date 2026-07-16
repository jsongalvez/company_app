package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.CommissionInclusionResponse
import com.companyb.companyapp.dto.CommissionSplitResponse
import com.companyb.companyapp.dto.CreateCommissionInclusionRequest
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.service.CommissionEngineService
import com.companyb.companyapp.service.CommissionManualInclusionService
import com.companyb.companyapp.service.CommissionSplitService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object CommissionRoutes {
    fun register(config: JavalinConfig) {
        config.routes.post("/api/commission-inclusions", ::handleCreateInclusion)
        config.routes.get("/api/commission-splits/{branchDayId}", ::handleGetSplits)
        config.routes.post("/api/commission/recalculate/{branchDayId}", ::handleRecalculate)
    }

    @Suppress("ThrowsCount")
    private fun handleCreateInclusion(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val request = context.bodyAsClass<CreateCommissionInclusionRequest>()

        val id =
            runCatching { UUID.fromString(request.id) }
                .getOrElse { throw BadRequestResponse("Invalid inclusion id") }
        val productSaleId =
            runCatching { UUID.fromString(request.productSaleId) }
                .getOrElse { throw BadRequestResponse("Invalid product sale id") }
        val userId =
            runCatching { UUID.fromString(request.userId) }
                .getOrElse { throw BadRequestResponse("Invalid user id") }

        val inclusion =
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = id,
                productSaleId = productSaleId,
                userId = userId,
                isIncluded = request.isIncluded,
                reason = request.reason,
            )

        context.status(HttpStatus.CREATED)
        context.json(inclusion.toResponse())
    }

    private fun handleGetSplits(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchDayId = context.pathParamAsUuid("branchDayId")

        val splits = CommissionSplitService.getByBranchDayId(callerId, branchDayId)

        context.status(HttpStatus.OK)
        context.json(splits.map { it.toResponse() })
    }

    private fun handleRecalculate(context: Context) {
        val callerId = UUID.fromString(context.attribute<String>("userId"))
        val branchDayId = context.pathParamAsUuid("branchDayId")

        CommissionEngineService.manualRecalculate(callerId, branchDayId)

        val splits = CommissionSplitService.getByBranchDayId(callerId, branchDayId)
        context.status(HttpStatus.OK)
        context.json(splits.map { it.toResponse() })
    }

    private fun CommissionManualInclusion.toResponse(): CommissionInclusionResponse =
        CommissionInclusionResponse(
            id = id.toString(),
            productSaleId = productSaleId.toString(),
            userId = userId.toString(),
            isIncluded = isIncluded,
            reason = reason,
            assignedBy = assignedBy.toString(),
            assignedAt = assignedAt.toString(),
        )

    private fun CommissionSplit.toResponse(): CommissionSplitResponse =
        CommissionSplitResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            userId = userId.toString(),
            amount = amount.toPlainString(),
        )
}
