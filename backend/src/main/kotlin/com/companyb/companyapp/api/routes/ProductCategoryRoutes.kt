package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
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
        config.routes.post("/api/product-categories") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateProductCategoryRequest>()
            val categoryId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid category id") }
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
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            context.json(ProductCategoryService.findAll(callerId).map { it.toResponse() })
        }

        config.routes.get("/api/product-categories/{$CATEGORY_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val categoryId = context.pathParamAsUuid(CATEGORY_ID_PARAM)
            context.json(ProductCategoryService.findById(callerId, categoryId).toResponse())
        }
    }

    private fun ProductCategory.toResponse(): ProductCategoryResponse =
        ProductCategoryResponse(
            id = id.toString(),
            name = name,
        )
}
