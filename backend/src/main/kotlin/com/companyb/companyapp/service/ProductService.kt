package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogger
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductCreateResult
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductCreateParams
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.UUID

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

        return ProductRepository.create(
            ProductCreateParams(
                id = id,
                name = name,
                productCategoryId = productCategoryId,
                unitPrice = unitPrice,
                commissionAmount = commissionAmount,
                changedBy = callerId,
            ),
        ) { product ->
            AuditLogger.insert(
                table = ProductTable.tableName,
                id = product.id,
                by = callerId,
                "id" to product.id.toString(),
                "name" to product.name,
                "productCategoryId" to product.productCategoryId.toString(),
                "unitPrice" to product.unitPrice.toPlainString(),
                "commissionAmount" to product.commissionAmount.toPlainString(),
            )
        }
    }

    fun findAllActive(): List<Product> = ProductRepository.findAllActive()

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

        val old = ProductRepository.findById(productId) ?: throw NotFoundException("Product not found")

        val updated =
            ProductRepository.update(
                productId = productId,
                name = name?.takeIf { it.isNotEmpty() },
                productCategoryId = productCategoryId,
                unitPrice = unitPrice,
                commissionAmount = commissionAmount,
                isActive = isActive,
            ) { updated ->
                AuditLogger.update(
                    table = ProductTable.tableName,
                    id = productId,
                    by = callerId,
                    oldFields =
                        arrayOf(
                            "name" to old.name,
                            "unitPrice" to old.unitPrice.toPlainString(),
                            "commissionAmount" to old.commissionAmount.toPlainString(),
                        ),
                    newFields =
                        arrayOf(
                            "name" to updated.name,
                            "unitPrice" to updated.unitPrice.toPlainString(),
                            "commissionAmount" to updated.commissionAmount.toPlainString(),
                        ),
                )
            }
        return updated ?: throw NotFoundException("Product not found")
    }
}
