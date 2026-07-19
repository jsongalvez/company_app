package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementTable

internal object MovementRecorder {
    fun recordBranchInventoryAudit(
        oldCard: BranchInventory,
        newCard: BranchInventory,
        movement: InventoryMovement,
    ) {
        AuditLogRepository.record(
            tableName = BranchInventoryTable.tableName,
            recordId = newCard.id,
            action = AuditAction.UPDATE,
            changedBy = movement.movedBy,
            oldValue =
                AuditLogRepository.jsonFields(
                    "currentStock" to oldCard.currentStock.toString(),
                    "version" to oldCard.version.toString(),
                ),
            newValue =
                AuditLogRepository.jsonFields(
                    "currentStock" to newCard.currentStock.toString(),
                    "version" to newCard.version.toString(),
                ),
        )
        AuditLogRepository.recordInsert(
            tableName = InventoryMovementTable.tableName,
            recordId = movement.id,
            changedBy = movement.movedBy,
            fields = InventoryMovementTable.auditFields(movement),
        )
    }
}
