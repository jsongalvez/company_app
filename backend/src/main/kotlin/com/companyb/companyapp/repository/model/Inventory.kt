package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.InventoryMovementReason
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

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
    val unitPrice: BigDecimal,
    val commissionAmount: BigDecimal,
)

object BranchInventoryTable : Table("branch_inventory") {
    val id = javaUUID("id").autoGenerate()
    val branchId = javaUUID("branch_id")
    val productId = javaUUID("product_id")
    val currentStock = integer("current_stock").default(0)
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: BranchInventory): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "branchId" to entity.branchId.toString(),
            "productId" to entity.productId.toString(),
            "currentStock" to entity.currentStock.toString(),
            "version" to entity.version.toString(),
        )
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
            // SAFETY: PG enum column binds as String via customEnumeration #467
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

    fun auditFields(entity: InventoryMovement): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "productId" to entity.productId.toString(),
            "branchId" to entity.branchId.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            "reason" to entity.reason.name,
            "quantityChange" to entity.quantityChange.toString(),
            "notes" to entity.notes,
        )
}
