package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal
import java.util.UUID

data class ProductCategory(
    val id: UUID,
    val name: String,
)

data class Product(
    val id: UUID,
    val name: String,
    val productCategoryId: UUID,
    val isActive: Boolean,
    val unitPrice: BigDecimal,
    val commissionAmount: BigDecimal,
    val reorderPoint: Int? = null,
)

data class ProductCreateParams(
    val id: UUID,
    val name: String,
    val productCategoryId: UUID,
    val unitPrice: BigDecimal,
    val commissionAmount: BigDecimal,
    val changedBy: UUID,
)

object ProductCategoryTable : Table("product_category") {
    val id = javaUUID("id").autoGenerate()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: ProductCategory): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "name" to entity.name,
        )
}

object ProductTable : Table("product") {
    private const val PRECISION = 10
    private const val SCALE = 2

    val id = javaUUID("id").autoGenerate()
    val name = text("name")
    val productCategoryId = javaUUID("product_category_id").references(ProductCategoryTable.id)
    val isActive = bool("is_active").default(true)
    val unitPrice = decimal("unit_price", PRECISION, SCALE)
    val commissionAmount = decimal("commission_amount", PRECISION, SCALE)
    val reorderPoint = integer("reorder_point").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Product): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "name" to entity.name,
            "productCategoryId" to entity.productCategoryId.toString(),
            "isActive" to entity.isActive.toString(),
            "unitPrice" to entity.unitPrice.toPlainString(),
            "commissionAmount" to entity.commissionAmount.toPlainString(),
        )
}
