package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.service.ProductService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object ProductRoutes {
    private const val PRODUCT_ID_PARAM = "productId"

    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/products") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_PRODUCTS,
                "MANAGE_PRODUCTS capability required to manage products",
            )
        }

        config.routes.post("/api/products") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateProductRequest>()
            val productId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid product id") }
            val categoryId =
                runCatching { UUID.fromString(request.productCategoryId) }
                    .getOrElse { throw BadRequestResponse("Invalid product category id") }
            val result =
                ProductService.create(
                    callerId = callerId,
                    id = productId,
                    name = request.name,
                    productCategoryId = categoryId,
                    unitPrice = request.unitPrice,
                    commissionAmount = request.commissionAmount,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.product.toResponse())
        }

        config.routes.get("/api/products") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            context.json(ProductService.findAllActive(callerId).map { it.toResponse() })
        }

        config.routes.get("/api/products/{$PRODUCT_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            context.json(ProductService.findById(callerId, productId).toResponse())
        }

        config.routes.patch("/api/products/{$PRODUCT_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            val request = context.bodyAsClass<UpdateProductRequest>()
            val categoryId =
                request.productCategoryId?.let {
                    runCatching { UUID.fromString(it) }
                        .getOrElse { throw BadRequestResponse("Invalid product category id") }
                }
            val result =
                ProductService.update(
                    callerId = callerId,
                    productId = productId,
                    name = request.name,
                    productCategoryId = categoryId,
                    unitPrice = request.unitPrice,
                    commissionAmount = request.commissionAmount,
                    isActive = request.isActive,
                )
            context.json(result.toResponse())
        }
    }

    private fun Product.toResponse(): ProductResponse =
        ProductResponse(
            id = id.toString(),
            name = name,
            productCategoryId = productCategoryId.toString(),
            isActive = isActive,
            unitPrice = unitPrice.toPlainString(),
            commissionAmount = commissionAmount.toPlainString(),
        )
}
