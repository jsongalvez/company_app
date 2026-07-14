package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

enum class InventoryMovementReason { RESTOCK, SALE, TESTER, SAMPLE, MISSING, ADJUSTMENT }

data class BranchInventory(
    val id: UUID,
    val branchId: UUID,
    val productId: UUID,
    val currentStock: Int,
    val version: Int,
)

data class InventoryMovement(
    val id: UUID,
    val productId: UUID,
    val branchId: UUID,
    val branchDayId: UUID,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val movedBy: UUID,
    val movedAt: OffsetDateTime,
    val notes: String?,
)

data class BranchInventoryWithProduct(
    val inventory: BranchInventory,
    val productName: String,
)

object BranchInventoryTable : Table("branch_inventory") {
    val id = uuid("id").autoGenerate()
    val branchId = uuid("branch_id")
    val productId = uuid("product_id")
    val currentStock = integer("current_stock").default(0)
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)
}

object InventoryMovementTable : Table("inventory_movement") {
    val id = uuid("id").autoGenerate()
    val productId = uuid("product_id")
    val productSaleId = uuid("product_sale_id").nullable()
    val branchId = uuid("branch_id")
    val branchDayId = uuid("branch_day_id")
    val reason =
        customEnumeration<InventoryMovementReason>(
            name = "reason",
            sql = "inventory_movement_reason",
            fromDb = { value -> InventoryMovementReason.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "inventory_movement_reason"
                obj.value = it.name
                obj
            },
        )
    val quantityChange = integer("quantity_change")
    val movedBy = uuid("moved_by")
    val movedAt = timestampWithTimeZone("moved_at").defaultExpression(CurrentTimestampWithTimeZone)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
