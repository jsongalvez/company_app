package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductCreateParams
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ProductCreateResult(
    val product: Product,
    val created: Boolean,
)

object ProductRepository {
    fun create(
        params: ProductCreateParams,
        auditFn: (Product) -> Unit = {},
    ): ProductCreateResult =
        transaction {
            val insertedCount =
                ProductTable
                    .insertIgnore {
                        it[ProductTable.id] = params.id
                        it[ProductTable.name] = params.name
                        it[ProductTable.productCategoryId] = params.productCategoryId
                        it[ProductTable.unitPrice] = params.unitPrice
                        it[ProductTable.commissionAmount] = params.commissionAmount
                    }.insertedCount
            val inserted = insertedCount > 0
            val product =
                findByIdInTransaction(params.id)
                    ?: error("product row not found after idempotent insert for ${params.id}")

            if (inserted) {
                auditFn(product)
                ProductCreateResult(product, created = true)
            } else {
                ProductCreateResult(product, created = false)
            }
        }.also {
            logger.info {
                "[CREATE-PRODUCT] Product ${it.product.id.toString().maskUUID()} created=${it.created}"
            }
        }

    fun findById(id: UUID): Product? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-PRODUCT] Product ${id.toString().maskUUID()} found=${it != null}" } }

    fun findAllActive(): List<Product> =
        transaction {
            ProductTable
                .selectAll()
                .where { ProductTable.isActive eq true }
                .orderBy(ProductTable.name to SortOrder.ASC, ProductTable.id to SortOrder.ASC)
                .map { it.toProduct() }
        }.also { logger.info { "[FIND-PRODUCTS] Fetched ${it.size} active product(s)" } }

    @Suppress("LongParameterList")
    fun update(
        productId: UUID,
        name: String?,
        productCategoryId: UUID?,
        unitPrice: BigDecimal?,
        commissionAmount: BigDecimal?,
        isActive: Boolean?,
        auditFn: (Product) -> Unit = {},
    ): Product? =
        transaction {
            val updatedCount =
                ProductTable.update({ ProductTable.id eq productId }) {
                    if (name != null) it[ProductTable.name] = name
                    if (productCategoryId != null) it[ProductTable.productCategoryId] = productCategoryId
                    if (unitPrice != null) it[ProductTable.unitPrice] = unitPrice
                    if (commissionAmount != null) it[ProductTable.commissionAmount] = commissionAmount
                    if (isActive != null) it[ProductTable.isActive] = isActive
                }
            val updated = findByIdInTransaction(productId) ?: return@transaction null

            if (updatedCount > 0) {
                auditFn(updated)
            }
            updated
        }

    private fun findByIdInTransaction(id: UUID): Product? =
        ProductTable
            .selectAll()
            .where { ProductTable.id eq id }
            .singleOrNull()
            ?.let { it.toProduct() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toProduct(): Product =
        Product(
            id = this[ProductTable.id],
            name = this[ProductTable.name],
            productCategoryId = this[ProductTable.productCategoryId],
            isActive = this[ProductTable.isActive],
            unitPrice = this[ProductTable.unitPrice],
            commissionAmount = this[ProductTable.commissionAmount],
        )
}
