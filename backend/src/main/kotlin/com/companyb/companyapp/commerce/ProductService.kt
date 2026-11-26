package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Product feature commands (ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `ProductRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 */
object ProductService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        new: NewProduct,
    ): ProductCreateResult {
        return traced("ProductService.create") {
            val categoryExists = ProductCategoryRepository.findById(new.productCategoryId) != null
            if (!categoryExists) {
                throw NotFoundException("Product category not found")
            }

            validateMoneyAmount(new.unitPrice, "unitPrice")
            validateMoneyAmount(new.commissionAmount, "commissionAmount")

            return@traced transaction {
                val result =
                    ProductRepository.createInTransaction(
                        ProductCreateParams(
                            id = new.id,
                            name = new.name,
                            productCategoryId = new.productCategoryId,
                            unitPrice = new.unitPrice,
                            commissionAmount = new.commissionAmount,
                            changedBy = callerId,
                        ),
                    )
                if (result.created) {
                    ProductAudit.inserted(callerId, result.product)
                }
                result
            }
        }
    }

    fun findAllActive(): List<Product> = ProductRepository.findAllActive()

    /** Admin catalog read: active + inactive; branch/sale guards keep [findAllActive]. */
    fun findAll(): List<Product> = ProductRepository.findAll()

    fun findById(productId: UUID): Product =
        ProductRepository.findById(productId) ?: throw NotFoundException("Product not found")

    fun update(
        callerId: UUID,
        productId: UUID,
        edit: ProductEdit,
    ): Product {
        if (edit.productCategoryId != null && ProductCategoryRepository.findById(edit.productCategoryId) == null) {
            throw NotFoundException("Product category not found")
        }

        return traced("ProductService.update") {
            edit.unitPrice?.let { validateMoneyAmount(it, "unitPrice") }
            edit.commissionAmount?.let { validateMoneyAmount(it, "commissionAmount") }

            return@traced transaction {
                // Locked before-state: SELECT FOR UPDATE serializes concurrent
                // writers so the audit diff attributes only this actor's changes.
                val before =
                    ProductRepository.findByIdForUpdateInTransaction(productId)
                        ?: throw NotFoundException("Product not found")

                val (updatedCount, after) =
                    ProductRepository.updateInTransaction(
                        productId = productId,
                        update =
                            ProductUpdate(
                                name = edit.name?.takeIf { it.isNotEmpty() },
                                productCategoryId = edit.productCategoryId,
                                unitPrice = edit.unitPrice,
                                commissionAmount = edit.commissionAmount,
                                isActive = edit.isActive,
                            ),
                    )

                if (updatedCount > 0 && after != null) {
                    ProductAudit.updated(callerId, before, after)
                }
                after ?: throw NotFoundException("Product not found")
            }
        }
    }
}

/**
 * Product audit vocabulary (ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ProductAudit {
    fun inserted(
        changedBy: UUID,
        product: Product,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy),
        row = AuditedRow(ProductTable.tableName, product.id),
        fields = ProductTable.auditFields(product),
    )

    fun updated(
        changedBy: UUID,
        before: Product,
        after: Product,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy),
        row = AuditedRow(ProductTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = ProductTable::auditFields,
    )
}

data class NewProduct(
    val id: UUID,
    val name: String,
    val productCategoryId: UUID,
    val unitPrice: BigDecimal,
    val commissionAmount: BigDecimal,
)

data class ProductEdit(
    val name: String?,
    val productCategoryId: UUID?,
    val unitPrice: BigDecimal?,
    val commissionAmount: BigDecimal?,
    val isActive: Boolean?,
)
