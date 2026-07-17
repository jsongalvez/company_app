package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductCategoryCreateResult
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.ProductCategory
import io.github.oshai.kotlinlogging.KotlinLogging
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
            throw ValidationException("Category name is required")
        }
        return ProductCategoryRepository.create(id, cleanName, callerId)
    }

    fun findAll(): List<ProductCategory> = ProductCategoryRepository.findAll()

    fun findById(categoryId: UUID): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundException("Product category not found")
}
