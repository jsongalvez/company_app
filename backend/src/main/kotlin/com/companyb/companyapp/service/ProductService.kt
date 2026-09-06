package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductCreateResult
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.ProductUpdate
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductCreateParams
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Product feature commands (#323, ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `ProductRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 */
object ProductService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
        productCategoryId: UUID,
        unitPrice: BigDecimal,
        commissionAmount: BigDecimal,
    ): ProductCreateResult {
        val categoryExists = ProductCategoryRepository.findById(productCategoryId) != null
        if (!categoryExists) {
            throw ValidationException("Product category not found")
        }

        return transaction {
            val result =
                ProductRepository.createInTransaction(
                    ProductCreateParams(
                        id = id,
                        name = name,
                        productCategoryId = productCategoryId,
                        unitPrice = unitPrice,
                        commissionAmount = commissionAmount,
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                ProductAudit.inserted(callerId, result.product)
            }
            result
        }
    }

    fun findAllActive(): List<Product> = ProductRepository.findAllActive()

    /** Admin catalog read (#445): active + inactive; branch/sale guards keep [findAllActive]. */
    fun findAll(): List<Product> = ProductRepository.findAll()

    fun findById(productId: UUID): Product =
        ProductRepository.findById(productId) ?: throw NotFoundException("Product not found")

    @Suppress("LongParameterList", "ThrowsCount")
    fun update(
        callerId: UUID,
        productId: UUID,
        name: String?,
        productCategoryId: UUID?,
        unitPrice: BigDecimal?,
        commissionAmount: BigDecimal?,
        isActive: Boolean?,
    ): Product {
        if (productCategoryId != null && ProductCategoryRepository.findById(productCategoryId) == null) {
            throw ValidationException("Product category not found")
        }

        return transaction {
            // Locked before-state (#522): SELECT FOR UPDATE serializes concurrent
            // writers so the audit diff attributes only this actor's changes.
            val before =
                ProductRepository.findByIdForUpdateInTransaction(productId)
                    ?: throw NotFoundException("Product not found")

            val (updatedCount, after) =
                ProductRepository.updateInTransaction(
                    productId = productId,
                    update =
                        ProductUpdate(
                            name = name?.takeIf { it.isNotEmpty() },
                            productCategoryId = productCategoryId,
                            unitPrice = unitPrice,
                            commissionAmount = commissionAmount,
                            isActive = isActive,
                        ),
                )

            if (updatedCount > 0 && after != null) {
                ProductAudit.updated(callerId, before, after)
            }
            after ?: throw NotFoundException("Product not found")
        }
    }
}

/**
 * Product audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ProductAudit {
    fun inserted(
        changedBy: UUID,
        product: Product,
    ) = AuditLog.recordInsert(
        tableName = ProductTable.tableName,
        recordId = product.id,
        changedBy = changedBy,
        fields = ProductTable.auditFields(product),
    )

    fun updated(
        changedBy: UUID,
        before: Product,
        after: Product,
    ) = AuditLog.recordUpdate(
        tableName = ProductTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        auditFields = ProductTable::auditFields,
    )
}
