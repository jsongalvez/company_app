package com.companyb.companyapp.commerce

import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal object ProductCategoryRepository {
    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(
        id: UUID,
        name: String,
    ): Pair<ProductCategory, Boolean> {
        val insertedCount =
            ProductCategoryTable
                .insertIgnore {
                    it[ProductCategoryTable.id] = id
                    it[ProductCategoryTable.name] = name
                }.insertedCount
        val category =
            findByIdInTransaction(id)
                ?: error("product_category row not found after idempotent insert for $id")
        return category to (insertedCount > 0)
    }

    fun findById(id: UUID): ProductCategory? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-PRODUCT-CATEGORY] Category ${id.toString().maskUUID()} found=${it != null}" } }

    fun findAll(): List<ProductCategory> =
        transaction {
            ProductCategoryTable
                .selectAll()
                .orderBy(ProductCategoryTable.name to SortOrder.ASC, ProductCategoryTable.id to SortOrder.ASC)
                .map { it.toProductCategory() }
        }.also { logger.info { "[FIND-PRODUCT-CATEGORIES] Fetched ${it.size} category(ies)" } }

    private fun findByIdInTransaction(id: UUID): ProductCategory? =
        ProductCategoryTable
            .selectAll()
            .where { ProductCategoryTable.id eq id }
            .singleOrNull()
            ?.let { it.toProductCategory() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toProductCategory(): ProductCategory =
        ProductCategory(
            id = this[ProductCategoryTable.id],
            name = this[ProductCategoryTable.name],
        )
}
