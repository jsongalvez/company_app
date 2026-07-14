package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ProductCategoryCreateResult
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ProductCategory
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import java.util.UUID

object ProductCategoryService {
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

    fun findAll(): List<ProductCategory> = ProductCategoryRepository.findAll()

    fun findById(categoryId: UUID): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw io.javalin.http.NotFoundResponse("Product category not found")
}
