package com.companyb.companyapp.service

import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object CommissionManualInclusionService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        productSaleId: UUID,
        userId: UUID,
        isIncluded: Boolean,
        reason: String?,
    ): CommissionManualInclusion {
        val sale =
            ProductSaleRepository.findById(productSaleId)
                ?: throw NotFoundResponse("Product sale not found")

        val result =
            CommissionManualInclusionRepository.upsert(
                id = id,
                productSaleId = productSaleId,
                userId = userId,
                isIncluded = isIncluded,
                reason = reason,
                assignedBy = callerId,
            )

        CommissionEngineService.recalculate(sale.branchDayId)

        logger.info {
            "[COMMISSION-INCLUSION] Created inclusion ${result.id} for productSale=$productSaleId " +
                "userId=$userId isIncluded=$isIncluded"
        }
        return result
    }
}
