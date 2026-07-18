package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.MovementAuditData
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.RecordMovementParams
import com.companyb.companyapp.repository.RestockAuditData
import com.companyb.companyapp.repository.RestockParams
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object BranchInventoryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun ensureCard(
        branchId: UUID,
        productId: UUID,
    ) {
        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundException("Branch not found")
        }

        if (ProductRepository.findById(productId) == null) {
            throw NotFoundException("Product not found")
        }

        BranchInventoryRepository.ensureCard(branchId, productId)
        logger.info { "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId" }
    }

    @Suppress("ThrowsCount", "LongParameterList", "LongMethod")
    fun restock(
        callerId: UUID,
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        quantity: Int,
        branchDayId: UUID,
    ): InventoryMovement {
        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundException("Branch not found")
        }

        if (ProductRepository.findById(productId) == null) {
            throw NotFoundException("Product not found")
        }

        BranchDayService.checkBranchDayEditable(callerId, branchDayId)

        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        val expectedVersion = card.version

        val result =
            try {
                BranchInventoryRepository.restock(
                    RestockParams(
                        movementId = movementId,
                        branchId = branchId,
                        productId = productId,
                        quantity = quantity,
                        branchDayId = branchDayId,
                        expectedVersion = expectedVersion,
                        movedBy = callerId,
                    ),
                    auditFn = { data ->
                        AuditLogRepository.record(
                            tableName = BranchInventoryTable.tableName,
                            recordId = data.newCard.id,
                            action = AuditAction.UPDATE,
                            changedBy = data.movement.movedBy,
                            oldValue =
                                AuditLogRepository.jsonFields(
                                    "currentStock" to data.oldCard.currentStock.toString(),
                                    "version" to data.oldCard.version.toString(),
                                ),
                            newValue =
                                AuditLogRepository.jsonFields(
                                    "currentStock" to data.newCard.currentStock.toString(),
                                    "version" to data.newCard.version.toString(),
                                ),
                        )
                        AuditLogRepository.recordInsert(
                            tableName = InventoryMovementTable.tableName,
                            recordId = data.movement.id,
                            changedBy = data.movement.movedBy,
                            fields = InventoryMovementTable.auditFields(data.movement),
                        )
                    },
                )
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictException("Inventory version mismatch")
                }
                throw e
            }

        return result.movement
    }

    @Suppress("ThrowsCount", "LongParameterList", "LongMethod")
    fun recordMovement(
        callerId: UUID,
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        reason: InventoryMovementReason,
        quantityChange: Int,
        notes: String?,
        branchDayId: UUID,
    ): InventoryMovement {
        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundException("Branch not found")
        }

        if (ProductRepository.findById(productId) == null) {
            throw NotFoundException("Product not found")
        }

        BranchDayService.checkBranchDayEditable(callerId, branchDayId)

        val card = BranchInventoryRepository.ensureCard(branchId, productId)
        val expectedVersion = card.version

        return try {
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
                    AuditLogRepository.record(
                        tableName = BranchInventoryTable.tableName,
                        recordId = data.newCard.id,
                        action = AuditAction.UPDATE,
                        changedBy = data.movement.movedBy,
                        oldValue =
                            AuditLogRepository.jsonFields(
                                "currentStock" to data.oldCard.currentStock.toString(),
                                "version" to data.oldCard.version.toString(),
                            ),
                        newValue =
                            AuditLogRepository.jsonFields(
                                "currentStock" to data.newCard.currentStock.toString(),
                                "version" to data.newCard.version.toString(),
                            ),
                    )
                    AuditLogRepository.recordInsert(
                        tableName = InventoryMovementTable.tableName,
                        recordId = data.movement.id,
                        changedBy = data.movement.movedBy,
                        fields = InventoryMovementTable.auditFields(data.movement),
                    )
                },
            )
        } catch (e: IllegalStateException) {
            if (e.message == "version_mismatch") {
                throw ConflictException("Inventory version mismatch")
            }
            throw e
        }
    }

    fun findByBranch(branchId: UUID): List<BranchInventoryWithProduct> {
        if (BranchRepository.findById(branchId) == null) {
            throw NotFoundException("Branch not found")
        }
        return BranchInventoryRepository.findByBranch(branchId)
    }
}
