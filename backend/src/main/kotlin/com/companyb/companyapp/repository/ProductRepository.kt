package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductCreateParams
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
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

/** Nullable-field update payload (#323): only non-null fields are written to the product row. */
data class ProductUpdate(
    val name: String? = null,
    val productCategoryId: UUID? = null,
    val unitPrice: BigDecimal? = null,
    val commissionAmount: BigDecimal? = null,
    val isActive: Boolean? = null,
)

object ProductRepository {
    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(params: ProductCreateParams): ProductCreateResult {
        val insertedCount =
            ProductTable
                .insertIgnore {
                    it[ProductTable.id] = params.id
                    it[ProductTable.name] = params.name
                    it[ProductTable.productCategoryId] = params.productCategoryId
                    it[ProductTable.unitPrice] = params.unitPrice
                    it[ProductTable.commissionAmount] = params.commissionAmount
                }.insertedCount
        val product =
            findByIdInTransaction(params.id)
                ?: error("product row not found after idempotent insert for ${params.id}")
        return ProductCreateResult(product, created = insertedCount > 0)
    }

    fun findById(id: UUID): Product? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-PRODUCT] Product ${id.toString().maskUUID()} found=${it != null}" } }

    fun findByIds(ids: Collection<UUID>): List<Product> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            transaction {
                ProductTable
                    .selectAll()
                    .where { ProductTable.id inList ids.toList() }
                    .map { it.toProduct() }
            }
        }

    fun findAllActive(): List<Product> =
        transaction {
            ProductTable
                .selectAll()
                .where { ProductTable.isActive eq true }
                .orderBy(ProductTable.name to SortOrder.ASC, ProductTable.id to SortOrder.ASC)
                .map { it.toProduct() }
        }.also { logger.info { "[FIND-PRODUCTS] Fetched ${it.size} active product(s)" } }

    /** Admin catalog read (#445): active + inactive, same ordering as [findAllActive]. */
    fun findAll(): List<Product> =
        transaction {
            ProductTable
                .selectAll()
                .orderBy(ProductTable.name to SortOrder.ASC, ProductTable.id to SortOrder.ASC)
                .map { it.toProduct() }
        }.also { logger.info { "[FIND-PRODUCTS] Fetched ${it.size} product(s) including inactive" } }

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction.
     * Returns the updated row count and the post-write row (null when the product does not exist);
     * the command decides audit from the count.
     */
    fun updateInTransaction(
        productId: UUID,
        update: ProductUpdate,
    ): Pair<Int, Product?> {
        val updatedCount =
            ProductTable.update({ ProductTable.id eq productId }) { statement ->
                update.name?.let { statement[ProductTable.name] = it }
                update.productCategoryId?.let { statement[ProductTable.productCategoryId] = it }
                update.unitPrice?.let { statement[ProductTable.unitPrice] = it }
                update.commissionAmount?.let { statement[ProductTable.commissionAmount] = it }
                update.isActive?.let { statement[ProductTable.isActive] = it }
            }
        return updatedCount to findByIdInTransaction(productId)
    }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Product? =
        ProductTable
            .selectAll()
            .where { ProductTable.id eq id }
            .singleOrNull()
            ?.let { it.toProduct() }

    /** Locked in-transaction read for commands that must not race product deactivation. */
    fun findByIdForUpdateInTransaction(id: UUID): Product? =
        ProductTable
            .selectAll()
            .where { ProductTable.id eq id }
            .forUpdate(ForUpdateOption.ForUpdate)
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
            reorderPoint = this[ProductTable.reorderPoint],
        )
}
