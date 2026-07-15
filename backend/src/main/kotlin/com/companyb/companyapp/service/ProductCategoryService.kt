package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ProductCategoryCreateResult
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ProductCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object ProductCategoryService {
    private val logger = KotlinLogging.logger {}
    private const val MANAGE_PRODUCTS = "MANAGE_PRODUCTS"

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
    ): ProductCategoryCreateResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            throw BadRequestResponse("Category name is required")
        }
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to create product categories")
        }
        return ProductCategoryRepository.create(id, cleanName, callerId)
    }

    fun findAll(callerId: UUID): List<ProductCategory> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-CATEGORIES] User $callerId lacks $MANAGE_PRODUCTS capability" }
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to list product categories")
        }
        return ProductCategoryRepository.findAll()
    }

    fun findById(
        callerId: UUID,
        categoryId: UUID,
    ): ProductCategory {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = MANAGE_PRODUCTS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-CATEGORY] User $callerId lacks $MANAGE_PRODUCTS capability" }
            throw ForbiddenResponse("MANAGE_PRODUCTS capability required to view product categories")
        }
        return ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundResponse("Product category not found")
    }
}
