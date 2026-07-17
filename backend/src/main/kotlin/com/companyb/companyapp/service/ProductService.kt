package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductCreateResult
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object ProductService {
    private val logger = KotlinLogging.logger {}
    private const val PRICE_SCALE = 2

    @Suppress("LongParameterList", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
        productCategoryId: UUID,
        unitPrice: String,
        commissionAmount: String,
    ): ProductCreateResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            throw ValidationException("Product name is required")
        }

        val parsedPrice = parsePrice(unitPrice)
        val parsedCommission = parsePrice(commissionAmount)

        val categoryExists = ProductCategoryRepository.findById(productCategoryId) != null
        if (!categoryExists) {
            throw ValidationException("Product category not found")
        }

        return ProductRepository.create(id, cleanName, productCategoryId, parsedPrice, parsedCommission, callerId)
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
        unitPrice: String?,
        commissionAmount: String?,
        isActive: Boolean?,
    ): Product {
        if (name != null && name.trim().isBlank()) {
            throw ValidationException("Product name cannot be blank")
        }

        if (productCategoryId != null && ProductCategoryRepository.findById(productCategoryId) == null) {
            throw ValidationException("Product category not found")
        }

        val parsedPrice = unitPrice?.let { parsePrice(it) }
        val parsedCommission = commissionAmount?.let { parsePrice(it) }

        val updated =
            ProductRepository.update(
                productId = productId,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                productCategoryId = productCategoryId,
                unitPrice = parsedPrice,
                commissionAmount = parsedCommission,
                isActive = isActive,
                changedBy = callerId,
            )
        return updated ?: throw NotFoundException("Product not found")
    }

    private fun parsePrice(value: String): BigDecimal =
        runCatching { BigDecimal(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP) }
            .getOrElse { throw ValidationException("Invalid price amount: $value") }
            .also { if (it < BigDecimal.ZERO) throw ValidationException("Price must be non-negative") }
}
