package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ProductCategoryRepository
import com.companyb.companyapp.repository.model.AuditAction
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
            AuditLogRepository.record(
                tableName = ProductCategoryTable.tableName,
                recordId = category.id,
                action = AuditAction.INSERT,
                changedBy = callerId,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to category.id.toString(),
                        "name" to category.name,
                    ),
            )
        }

    fun findAll(): List<ProductCategory> = ProductCategoryRepository.findAll()

    fun findById(categoryId: UUID): ProductCategory =
        ProductCategoryRepository.findById(categoryId)
            ?: throw NotFoundException("Product category not found")
}
