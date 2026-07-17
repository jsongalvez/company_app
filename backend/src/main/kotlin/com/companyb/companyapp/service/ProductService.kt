package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductCreateResult
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.Product
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

        return ProductRepository.create(id, name, productCategoryId, unitPrice, commissionAmount, callerId)
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

        val updated =
            ProductRepository.update(
                productId = productId,
                name = name?.takeIf { it.isNotEmpty() },
                productCategoryId = productCategoryId,
                unitPrice = unitPrice,
                commissionAmount = commissionAmount,
                isActive = isActive,
                changedBy = callerId,
            )
        return updated ?: throw NotFoundException("Product not found")
    }
}
