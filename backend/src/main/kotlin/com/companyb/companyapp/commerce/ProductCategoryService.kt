package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

object ProductCategoryService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
    ): ProductCategory =
        transaction {
            val (category, created) = ProductCategoryRepository.createInTransaction(id, name)
            if (created) {
                ProductCategoryAudit.inserted(callerId, category)
            }
            category
        }.also {
            logger.info { "[CREATE-PRODUCT-CATEGORY] Category ${it.id.toString().maskUUID()}" }
        }

    fun findAll(): List<ProductCategory> = ProductCategoryRepository.findAll()

    fun findById(categoryId: UUID): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundException("Product category not found")
}

/**
 * Product-category audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ProductCategoryAudit {
    fun inserted(
        changedBy: UUID,
        category: ProductCategory,
    ) = AuditLog.recordInsert(
        tableName = ProductCategoryTable.tableName,
        recordId = category.id,
        changedBy = changedBy,
        fields = ProductCategoryTable.auditFields(category),
    )
}
