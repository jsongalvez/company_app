package com.companyb.companyapp.service
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.SellProductParams
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.commission.CommissionService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object ProductSaleService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ReturnCount", "ThrowsCount", "LongParameterList", "CyclomaticComplexMethod", "LongMethod")
    fun sell(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
        productId: UUID,
        quantity: Int,
        expectedVersion: Int,
    ): ProductSale {
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId)

        if (BranchRepository.findById(branchDay.branchId) == null) {
            throw NotFoundException("Branch not found")
        }

        val product =
            ProductRepository.findById(productId)
                ?: throw NotFoundException("Product not found")

        if (!product.isActive) {
            throw ValidationException("Product is not active")
        }

        if (sessionId != null && SessionRepository.findById(sessionId) == null) {
            throw NotFoundException("Session not found")
        }

        val result =
            ProductSaleRepository.sell(
                SellProductParams(
                    id = id,
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = clientId,
                    isWalkIn = isWalkIn,
                    productId = productId,
                    branchId = branchDay.branchId,
                    quantity = quantity,
                    expectedVersion = expectedVersion,
                    handledBy = callerId,
                    product = product,
                ),
            ) { sale, beforeCard, afterCard ->
                AuditLogRepository.recordInsert(
                    tableName = ProductSaleTable.tableName,
                    recordId = sale.id,
                    changedBy = callerId,
                    fields = ProductSaleTable.auditFields(sale),
                    isFlagged = isRemitted,
                )
                AuditLogRepository.recordUpdate(
                    tableName = BranchInventoryTable.tableName,
                    recordId = beforeCard.id,
                    before = beforeCard,
                    after = afterCard,
                    changedBy = callerId,
                    isFlagged = isRemitted,
                    auditFields = BranchInventoryTable::auditFields,
                )
            }

        CommissionService.recalculate(branchDayId)

        return result
    }
}
