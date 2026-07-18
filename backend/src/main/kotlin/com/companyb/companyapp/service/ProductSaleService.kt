package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.SellProductParams
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.repository.model.ProductSaleTable
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
        val branchDay = BranchDayService.checkBranchDayEditable(callerId, branchDayId)

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
            try {
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
                ) { data ->
                    AuditLogRepository.record(
                        tableName = ProductSaleTable.tableName,
                        recordId = data.sale.id,
                        action = AuditAction.INSERT,
                        changedBy = callerId,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "id" to data.sale.id.toString(),
                                "branchDayId" to data.sale.branchDayId.toString(),
                                "productId" to data.sale.productId.toString(),
                                "quantity" to data.sale.quantity.toString(),
                                "totalAmount" to data.sale.totalAmountAtTime.toPlainString(),
                            ),
                    )
                    AuditLogRepository.record(
                        tableName = BranchInventoryTable.tableName,
                        recordId = data.inventoryCardId,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        oldValue =
                            AuditLogRepository.jsonFields(
                                "currentStock" to data.oldStock.toString(),
                                "version" to data.oldVersion.toString(),
                            ),
                        newValue =
                            AuditLogRepository.jsonFields(
                                "currentStock" to data.newStock.toString(),
                                "version" to data.newVersion.toString(),
                            ),
                    )
                }
            } catch (e: IllegalStateException) {
                when (e.message) {
                    "version_mismatch" -> throw ConflictException("Inventory version mismatch")
                    "insufficient_stock" -> throw ValidationException("Insufficient stock")
                    else -> throw e
                }
            }

        CommissionEngineService.recalculate(branchDayId)

        return result
    }
}
