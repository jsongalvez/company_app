package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.ProductCategoryTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object ProductCategoryRepository {
    fun create(
        id: UUID,
        name: String,
        auditFn: (ProductCategory) -> Unit = {},
    ): ProductCategory =
        transaction {
            val insertedCount =
                ProductCategoryTable
                    .insertIgnore {
                        it[ProductCategoryTable.id] = id
                        it[ProductCategoryTable.name] = name
                    }.insertedCount
            val category =
                findByIdInTransaction(id)
                    ?: error("product_category row not found after idempotent insert for $id")

            if (insertedCount > 0) {
                auditFn(category)
            }
            category
        }.also {
            logger.info {
                "[CREATE-PRODUCT-CATEGORY] Category ${it.id.toString().maskUUID()}"
            }
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
