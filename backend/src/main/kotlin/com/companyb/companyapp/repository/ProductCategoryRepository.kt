package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.ProductCategoryTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ProductCategoryCreateResult(
    val category: ProductCategory,
    val created: Boolean,
)

object ProductCategoryRepository {
    fun create(
        id: UUID,
        name: String,
        changedBy: UUID,
    ): ProductCategoryCreateResult =
        transaction {
            val insertedCount =
                ProductCategoryTable
                    .insertIgnore {
                        it[ProductCategoryTable.id] = id
                        it[ProductCategoryTable.name] = name
                    }.insertedCount
            val inserted = insertedCount > 0
            val category =
                findByIdInTransaction(id)
                    ?: error("product_category row not found after idempotent insert for $id")

            if (inserted) {
                AuditLogRepository.record(
                    tableName = ProductCategoryTable.tableName,
                    recordId = category.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to category.id.toString(),
                            "name" to category.name,
                        ),
                )
                ProductCategoryCreateResult(category, created = true)
            } else {
                ProductCategoryCreateResult(category, created = false)
            }
        }.also {
            logger.info {
                "[CREATE-PRODUCT-CATEGORY] Category ${it.category.id.toString().maskUUID()} created=${it.created}"
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

    private fun org.jetbrains.exposed.sql.ResultRow.toProductCategory(): ProductCategory =
        ProductCategory(
            id = this[ProductCategoryTable.id],
            name = this[ProductCategoryTable.name],
        )
}
