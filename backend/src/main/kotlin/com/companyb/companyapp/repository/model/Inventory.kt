package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

enum class InventoryMovementReason { RESTOCK, SALE, TESTER, SAMPLE, MISSING, ADJUSTMENT }

data class BranchInventory(
    override val id: UUID,
    val branchId: UUID,
    val productId: UUID,
    val currentStock: Int,
    val version: Int,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "branchId" to branchId.toString(),
            "productId" to productId.toString(),
            "currentStock" to currentStock.toString(),
            "version" to version.toString(),
        )
}

data class InventoryMovement(
    override val id: UUID,
    val productId: UUID,
    val branchId: UUID,
    val branchDayId: UUID,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val movedBy: UUID,
    val movedAt: OffsetDateTime,
    val notes: String?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "productId" to productId.toString(),
            "branchId" to branchId.toString(),
            "branchDayId" to branchDayId.toString(),
            "reason" to reason.name,
            "quantityChange" to quantityChange.toString(),
            "notes" to (notes ?: ""),
        )
}

data class BranchInventoryWithProduct(
    val inventory: BranchInventory,
    val productName: String,
)

object BranchInventoryTable : Table("branch_inventory") {
    val id = javaUUID("id").autoGenerate()
    val branchId = javaUUID("branch_id")
    val productId = javaUUID("product_id")
    val currentStock = integer("current_stock").default(0)
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)
}

object InventoryMovementTable : Table("inventory_movement") {
    val id = javaUUID("id").autoGenerate()
    val productId = javaUUID("product_id")
    val productSaleId = javaUUID("product_sale_id").nullable()
    val branchId = javaUUID("branch_id")
    val branchDayId = javaUUID("branch_day_id")
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
    val movedBy = javaUUID("moved_by")
    val movedAt = timestampWithTimeZone("moved_at").defaultExpression(CurrentTimestampWithTimeZone)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
