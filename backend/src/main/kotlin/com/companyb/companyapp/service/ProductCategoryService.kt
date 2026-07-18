package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogger
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.ProductCategoryTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object ProductCategoryService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
    ): ProductCategory =
        ProductCategoryRepository.create(id, name) { category ->
            AuditLogger.insert(
                table = ProductCategoryTable.tableName,
                id = category.id,
                by = callerId,
                "id" to category.id.toString(),
                "name" to category.name,
            )
        }

    fun findAll(): List<ProductCategory> = ProductCategoryRepository.findAll()

    fun findById(categoryId: UUID): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundException("Product category not found")
}
