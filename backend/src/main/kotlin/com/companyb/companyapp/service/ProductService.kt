package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.ProductCreateResult
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object ProductService {
    private val logger = KotlinLogging.logger {}
    private const val MANAGE_PRODUCTS = "MANAGE_PRODUCTS"
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
            throw BadRequestResponse("Product name is required")
        }
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to create products")
        }

        val parsedPrice = parsePrice(unitPrice)
        val parsedCommission = parsePrice(commissionAmount)

        val categoryExists = ProductCategoryRepository.findById(productCategoryId) != null
        if (!categoryExists) {
            throw BadRequestResponse("Product category not found")
        }

        return ProductRepository.create(id, cleanName, productCategoryId, parsedPrice, parsedCommission, callerId)
    }

    fun findAllActive(callerId: UUID): List<Product> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-PRODUCTS] User $callerId lacks $MANAGE_PRODUCTS capability" }
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to list products")
        }
        return ProductRepository.findAllActive()
    }

    fun findById(
        callerId: UUID,
        productId: UUID,
    ): Product {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-PRODUCT] User $callerId lacks $MANAGE_PRODUCTS capability" }
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to view products")
        }
        return ProductRepository.findById(productId) ?: throw NotFoundResponse("Product not found")
    }

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
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to update products")
        }

        if (name != null && name.trim().isBlank()) {
            throw BadRequestResponse("Product name cannot be blank")
        }

        if (productCategoryId != null && ProductCategoryRepository.findById(productCategoryId) == null) {
            throw BadRequestResponse("Product category not found")
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
        return updated ?: throw NotFoundResponse("Product not found")
    }

    private fun parsePrice(value: String): BigDecimal =
        runCatching { BigDecimal(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP) }
            .getOrElse { throw BadRequestResponse("Invalid price amount: $value") }
            .also { if (it < BigDecimal.ZERO) throw BadRequestResponse("Price must be non-negative") }
}
