package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.service.ProductCategoryService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object ProductCategoryRoutes {
    private const val CATEGORY_ID_PARAM = "categoryId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/product-categories") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_PRODUCTS,
                "MANAGE_PRODUCTS capability required to manage product categories",
            )
        }

        config.routes.post("/api/product-categories") { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateProductCategoryRequest>()
            val categoryId = uuidOrThrow(request.id, "category id")
            val result =
                ProductCategoryService.create(
                    callerId = callerId,
                    id = categoryId,
                    name = request.name,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.category.toResponse())
        }

        config.routes.get("/api/product-categories") { context ->
            context.json(ProductCategoryService.findAll().map { it.toResponse() })
        }

        config.routes.get("/api/product-categories/{$CATEGORY_ID_PARAM}") { context ->
            val categoryId = context.pathParamAsUuid(CATEGORY_ID_PARAM)
            context.json(ProductCategoryService.findById(categoryId).toResponse())
        }
    }

    private fun ProductCategory.toResponse(): ProductCategoryResponse =
        ProductCategoryResponse(
            id = id.toString(),
            name = name,
        )
}
