package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.repository.AuditLogRepository
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
        AuditLogRepository.recordUpdate(
            tableName = BranchInventoryTable.tableName,
            recordId = newCard.id,
            before = oldCard,
            after = newCard,
            changedBy = movement.movedBy,
            auditFields = BranchInventoryTable::auditFields,
        )
        AuditLogRepository.recordInsert(
            tableName = InventoryMovementTable.tableName,
            recordId = movement.id,
            changedBy = movement.movedBy,
            fields = InventoryMovementTable.auditFields(movement),
        )
    }
}
