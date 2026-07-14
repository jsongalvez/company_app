package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ProductCreateResult(
    val product: Product,
    val created: Boolean,
)

object ProductRepository {
    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        name: String,
        productCategoryId: UUID,
        unitPrice: BigDecimal,
        commissionAmount: BigDecimal,
        changedBy: UUID,
    ): ProductCreateResult =
        transaction {
            val insertedCount =
                ProductTable
                    .insertIgnore {
                        it[ProductTable.id] = id
                        it[ProductTable.name] = name
                        it[ProductTable.productCategoryId] = productCategoryId
                        it[ProductTable.unitPrice] = unitPrice
                        it[ProductTable.commissionAmount] = commissionAmount
                    }.insertedCount
            val inserted = insertedCount > 0
            val product =
                findByIdInTransaction(id)
                    ?: error("product row not found after idempotent insert for $id")

            if (inserted) {
                AuditLogRepository.record(
                    tableName = ProductTable.tableName,
                    recordId = product.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to product.id.toString(),
                            "name" to product.name,
                            "productCategoryId" to product.productCategoryId.toString(),
                            "unitPrice" to product.unitPrice.toPlainString(),
                            "commissionAmount" to product.commissionAmount.toPlainString(),
                        ),
                )
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
        changedBy: UUID,
    ): Product? =
        transaction {
            val old = findByIdInTransaction(productId) ?: return@transaction null

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
                AuditLogRepository.record(
                    tableName = ProductTable.tableName,
                    recordId = productId,
                    action = AuditAction.UPDATE,
                    changedBy = changedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "name" to old.name,
                            "unitPrice" to old.unitPrice.toPlainString(),
                            "commissionAmount" to old.commissionAmount.toPlainString(),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "name" to updated.name,
                            "unitPrice" to updated.unitPrice.toPlainString(),
                            "commissionAmount" to updated.commissionAmount.toPlainString(),
                        ),
                )
            }
            updated
        }

    private fun findByIdInTransaction(id: UUID): Product? =
        ProductTable
            .selectAll()
            .where { ProductTable.id eq id }
            .singleOrNull()
            ?.let { it.toProduct() }

    private fun org.jetbrains.exposed.sql.ResultRow.toProduct(): Product =
        Product(
            id = this[ProductTable.id],
            name = this[ProductTable.name],
            productCategoryId = this[ProductTable.productCategoryId],
            isActive = this[ProductTable.isActive],
            unitPrice = this[ProductTable.unitPrice],
            commissionAmount = this[ProductTable.commissionAmount],
        )
}
