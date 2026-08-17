package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
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
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/product-categories",
    methods = [HttpMethod.GET],
    operationId = "product_categories_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/product-categories",
    methods = [HttpMethod.POST],
    operationId = "product_categories_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/product-categories/{categoryId}",
    methods = [HttpMethod.GET],
    operationId = "product_category",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
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
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Category name is required")
            val category =
                ProductCategoryService.create(
                    callerId = callerId,
                    id = categoryId,
                    name = name,
                )

            context.status(HttpStatus.CREATED)
            context.json(category.toResponse())
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
