package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ProductCategoryCreateResult
import com.companyb.companyapp.repository.ProductCategoryRepository
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
        return ProductCategoryRepository.create(id, cleanName, callerId)
    }

    @Suppress("UnusedParameter")
    fun findAll(callerId: UUID): List<ProductCategory> = ProductCategoryRepository.findAll()

    @Suppress("UnusedParameter")
    fun findById(
        callerId: UUID,
        categoryId: UUID,
    ): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundResponse("Product category not found")
}
