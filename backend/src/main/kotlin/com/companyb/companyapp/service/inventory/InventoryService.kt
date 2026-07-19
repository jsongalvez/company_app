package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementReason
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

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
    ): InventoryMovement {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        if (ProductRepository.findById(productId) == null) throw NotFoundException("Product not found")

        StockValidator.validateMovement(callerId, branchDayId, movementType, quantityChange, notes)

        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        val expectedVersion = card.version

        val reason = InventoryMovementReason.valueOf(movementType::class.simpleName!!.uppercase())

        return try {
            val movement =
                BranchInventoryRepository.recordMovement(
                    RecordMovementParams(
                        movementId = movementId,
                        branchId = branchId,
                        productId = productId,
                        reason = reason,
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
                        )
                    },
                )

            logger.info {
                "[RECORD-MOVEMENT] $movementType product=$productId branch=$branchId qty=$quantityChange"
            }
            movement
        } catch (e: IllegalStateException) {
            if (e.message == "version_mismatch") {
                throw ConflictException("Inventory version mismatch")
            }
            throw e
        }
    }

    fun getStock(branchId: UUID): List<BranchInventoryWithProduct> {
        if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
        return BranchInventoryRepository.findByBranch(branchId)
    }

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
