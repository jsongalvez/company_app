package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.RecordMovementParams
import com.companyb.companyapp.repository.RecordMovementResult
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

private const val LOW_STOCK_DEFAULT_THRESHOLD = 5

/**
 * Inventory feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: the day gate and guards run inside it, persistence runs via
 * `BranchInventoryRepository.*InTransaction` store operations, and the audit rows are
 * inserted into the same transaction — mutation + audit commit atomically or not at all.
 */
object InventoryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ThrowsCount", "ReturnCount", "LongMethod")
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
        val movementReason = movementType.toInventoryMovementReason()

        val result =
            transaction {
                val existingMovement = BranchInventoryRepository.findMovementInTransaction(movementId)
                if (existingMovement != null) {
                    ensureRequestOwnership(
                        existingMovement,
                        branchId,
                        productId,
                        movementReason,
                        quantityChange,
                        notes,
                        branchDayId,
                        callerId,
                    )
                    return@transaction RecordMovementResult(existingMovement, created = false)
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

                // The card read that supplies expectedVersion now happens in this same command
                // transaction, fixing the former cross-boundary stale version read.
                val oldCard = BranchInventoryRepository.ensureCardInTransaction(branchId, productId).card

                val inserted =
                    BranchInventoryRepository.insertMovementInTransaction(
                        RecordMovementParams(
                            movementId = movementId,
                            branchId = branchId,
                            productId = productId,
                            reason = movementReason,
                            quantityChange = quantityChange,
                            notes = notes,
                            branchDayId = branchDayId,
                            movedBy = callerId,
                        ),
                    )
                if (!inserted.created) return@transaction inserted

                val newCard =
                    BranchInventoryRepository.requireCardForUpdate(
                        oldCard,
                        oldCard.version,
                        quantityChange,
                    )

                val context = AuditContext(callerId, branchId, isRemitted, reason)
                BranchInventoryAudit.updated(context, oldCard, newCard)
                BranchInventoryAudit.inserted(context, inserted.movement)
                inserted
            }

        if (result.created) {
            logger.info {
                "[RECORD-MOVEMENT] $movementType product=$productId branch=$branchId qty=$quantityChange"
            }
        }
        return result.movement
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

    @Suppress("ComplexCondition", "LongParameterList")
    private fun ensureRequestOwnership(
        existingMovement: InventoryMovement,
        branchId: UUID,
        productId: UUID,
        movementReason: InventoryMovementReason,
        quantityChange: Int,
        notes: String?,
        branchDayId: UUID,
        callerId: UUID,
    ) {
        if (
            existingMovement.branchId != branchId ||
            existingMovement.productId != productId ||
            existingMovement.reason != movementReason ||
            existingMovement.quantityChange != quantityChange ||
            existingMovement.notes != notes ||
            existingMovement.branchDayId != branchDayId ||
            existingMovement.movedBy != callerId
        ) {
            throw ConflictException("Movement ID already belongs to another request")
        }
    }

    @Suppress("ThrowsCount")
    fun ensureCard(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
    ): BranchInventory =
        transaction {
            if (BranchRepository.findById(branchId) == null) throw NotFoundException("Branch not found")
            if (ProductRepository.findById(productId) == null) throw NotFoundException("Product not found")

            val result = BranchInventoryRepository.ensureCardInTransaction(branchId, productId)
            val context = AuditContext(callerId, branchId)
            if (result.created) {
                BranchInventoryAudit.inserted(context, result.card)
            } else {
                BranchInventoryAudit.updated(context, result.card, result.card)
            }
            result.card
        }.also {
            logger.info { "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId" }
        }
}

/**
 * Inventory audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit rows commit atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object BranchInventoryAudit {
    fun updated(
        context: AuditContext,
        before: BranchInventory,
        after: BranchInventory,
    ) = AuditLogRepository.recordUpdate(
        tableName = BranchInventoryTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = BranchInventoryTable::auditFields,
    )

    fun inserted(
        context: AuditContext,
        movement: InventoryMovement,
    ) = AuditLogRepository.recordInsert(
        tableName = InventoryMovementTable.tableName,
        recordId = movement.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = InventoryMovementTable.auditFields(movement),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    /** INSERT vocabulary for the inventory card itself (#393 — ensure-card audit gap). */
    fun inserted(
        context: AuditContext,
        card: BranchInventory,
    ) = AuditLogRepository.recordInsert(
        tableName = BranchInventoryTable.tableName,
        recordId = card.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = BranchInventoryTable.auditFields(card),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )
}
