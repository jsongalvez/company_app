package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.ProductCategoryCreateResult
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ProductCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object ProductCategoryService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
    ): ProductCategoryCreateResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            throw BadRequestResponse("Category name is required")
        }
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_PRODUCTS capability required to create product categories",
        )
        return ProductCategoryRepository.create(id, cleanName, callerId)
    }

    fun findAll(callerId: UUID): List<ProductCategory> {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_PRODUCTS capability required to list product categories",
        )
        return ProductCategoryRepository.findAll()
    }

    fun findById(
        callerId: UUID,
        categoryId: UUID,
    ): ProductCategory {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "MANAGE_PRODUCTS capability required to view product categories",
        )
        return ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundResponse("Product category not found")
    }
}
