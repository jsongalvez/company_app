package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
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
)

object ProductCategoryTable : Table("product_category") {
    val id = uuid("id").autoGenerate()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}

object ProductTable : Table("product") {
    private const val PRECISION = 10
    private const val SCALE = 2

    val id = uuid("id").autoGenerate()
    val name = text("name")
    val productCategoryId = uuid("product_category_id").references(ProductCategoryTable.id)
    val isActive = bool("is_active").default(true)
    val unitPrice = decimal("unit_price", PRECISION, SCALE)
    val commissionAmount = decimal("commission_amount", PRECISION, SCALE)

    override val primaryKey = PrimaryKey(id)
}
