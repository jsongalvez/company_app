package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal
import java.util.UUID

data class ProductCategory(
    override val id: UUID,
    val name: String,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "name" to name,
        )
}

data class Product(
    override val id: UUID,
    val name: String,
    val productCategoryId: UUID,
    val isActive: Boolean,
    val unitPrice: BigDecimal,
    val commissionAmount: BigDecimal,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "name" to name,
            "productCategoryId" to productCategoryId.toString(),
            "unitPrice" to unitPrice.toPlainString(),
            "commissionAmount" to commissionAmount.toPlainString(),
        )
}

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

    override val primaryKey = PrimaryKey(id)
}
