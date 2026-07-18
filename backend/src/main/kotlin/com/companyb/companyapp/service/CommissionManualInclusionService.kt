package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import io.github.oshai.kotlinlogging.KotlinLogging
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
                ?: throw NotFoundException("Product sale not found")

        val result =
            CommissionManualInclusionRepository.upsert(
                id = id,
                productSaleId = productSaleId,
                userId = userId,
                isIncluded = isIncluded,
                reason = reason,
                assignedBy = callerId,
            ) { existing, updated ->
                if (existing == null) {
                    AuditLogRepository.record(
                        tableName = CommissionManualInclusionTable.tableName,
                        recordId = updated.id,
                        action = AuditAction.INSERT,
                        changedBy = callerId,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "id" to updated.id.toString(),
                                "productSaleId" to updated.productSaleId.toString(),
                                "userId" to updated.userId.toString(),
                                "isIncluded" to updated.isIncluded.toString(),
                            ),
                    )
                } else {
                    AuditLogRepository.record(
                        tableName = CommissionManualInclusionTable.tableName,
                        recordId = updated.id,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        oldValue =
                            AuditLogRepository.jsonFields(
                                "isIncluded" to existing.isIncluded.toString(),
                                "reason" to (existing.reason ?: "null"),
                            ),
                        newValue =
                            AuditLogRepository.jsonFields(
                                "isIncluded" to updated.isIncluded.toString(),
                                "reason" to (updated.reason ?: "null"),
                            ),
                    )
                }
            }

        CommissionEngineService.recalculate(sale.branchDayId)

        logger.info {
            "[COMMISSION-INCLUSION] Created inclusion ${result.id} for productSale=$productSaleId " +
                "userId=$userId isIncluded=$isIncluded"
        }
        return result
    }
}
