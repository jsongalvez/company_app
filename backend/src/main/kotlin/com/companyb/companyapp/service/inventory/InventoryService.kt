package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.RecordMovementParams
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.util.UUID

private const val LOW_STOCK_DEFAULT_THRESHOLD = 5

object InventoryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ThrowsCount")
    fun recordMovement(
        callerId: UUID,
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        movementType: MovementType,
        quantityChange: Int,
        notes: String?,
        branchDayId: UUID,
        reason: String? = null,
    ): InventoryMovement {
        BranchInventoryRepository.findMovementById(movementId)?.let { existingMovement ->
            if (existingMovement.branchId != branchId) {
                throw ConflictException("Movement ID already belongs to another branch")
            }
            return existingMovement
        }
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        if (ProductRepository.findById(productId) == null) throw NotFoundException("Product not found")
        BranchDayService.requireBranchDayForBranch(branchDayId, branchId)

        val isRemitted =
            StockValidator.validateMovement(
                callerId,
                branchDayId,
                movementType,
                quantityChange,
                notes,
                reason,
            )

        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        val expectedVersion = card.version

        val movementReason = movementType.toInventoryMovementReason()

        val movement =
            BranchInventoryRepository.recordMovement(
                RecordMovementParams(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    reason = movementReason,
                    quantityChange = quantityChange,
                    notes = notes,
                    branchDayId = branchDayId,
                    expectedVersion = expectedVersion,
                    movedBy = callerId,
                ),
                auditFn = { data ->
                    MovementRecorder.recordBranchInventoryAudit(
                        oldCard = data.oldCard,
                        newCard = data.newCard,
                        movement = data.movement,
                        isFlagged = isRemitted,
                        reason = reason,
                    )
                },
            )

        logger.info {
            "[RECORD-MOVEMENT] $movementType product=$productId branch=$branchId qty=$quantityChange"
        }
        return movement
    }

    fun getStock(branchId: UUID): List<BranchInventoryWithProduct> {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        return BranchInventoryRepository.findByBranch(branchId)
    }

    fun getMovementHistory(
        branchId: UUID,
        date: LocalDate? = null,
    ): List<InventoryMovement> {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        return BranchInventoryRepository.findMovements(branchId, date)
    }

    @Suppress("ReturnCount")
    fun getLowStockAlerts(
        branchId: UUID,
        thresholdOverride: Int? = null,
    ): List<BranchInventoryWithProduct> {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        val allInventory = BranchInventoryRepository.findByBranch(branchId)
        if (allInventory.isEmpty()) return allInventory

        if (thresholdOverride != null) {
            return allInventory.filter { it.inventory.currentStock <= thresholdOverride }
        }

        val productIds = allInventory.map { it.inventory.productId }
        val products = ProductRepository.findByIds(productIds)
        val productThresholds =
            products.associate { product ->
                product.id to resolveThreshold(product)
            }
        return allInventory.filter { item ->
            val threshold =
                productThresholds[item.inventory.productId]
                    ?: LOW_STOCK_DEFAULT_THRESHOLD
            item.inventory.currentStock <= threshold
        }
    }

    private fun resolveThreshold(product: Product): Int = product.reorderPoint ?: LOW_STOCK_DEFAULT_THRESHOLD

    @Suppress("ThrowsCount")
    fun ensureCard(
        branchId: UUID,
        productId: UUID,
    ): BranchInventory {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        if (ProductRepository.findById(productId) == null) throw NotFoundException("Product not found")
        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        logger.info { "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId" }
        return card
    }
}
