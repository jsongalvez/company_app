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
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object ProductRoutes {
    private const val PRODUCT_ID_PARAM = "productId"
    private const val PRICE_SCALE = 2

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
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateProductRequest>()
            val productId = uuidOrThrow(request.id, "product id")
            val categoryId = uuidOrThrow(request.productCategoryId, "product category id")
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Product name is required")
            val unitPrice = parsePrice(request.unitPrice)
            val commissionAmount = parsePrice(request.commissionAmount)
            val result =
                ProductService.create(
                    callerId = callerId,
                    id = productId,
                    name = name,
                    productCategoryId = categoryId,
                    unitPrice = unitPrice,
                    commissionAmount = commissionAmount,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.product.toResponse())
        }

        config.routes.get("/api/products") { context ->
            context.json(ProductService.findAllActive().map { it.toResponse() })
        }

        config.routes.get("/api/products/{$PRODUCT_ID_PARAM}") { context ->
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            context.json(ProductService.findById(productId).toResponse())
        }

        config.routes.patch("/api/products/{$PRODUCT_ID_PARAM}") { context ->
            val callerId = context.callerUuid()
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            val request = context.bodyAsClass<UpdateProductRequest>()
            val updateName = request.name
            if (updateName != null && updateName.trim().isBlank()) {
                throw BadRequestResponse("Product name cannot be blank")
            }
            val categoryId = request.productCategoryId?.let { uuidOrThrow(it, "product category id") }
            val unitPrice = request.unitPrice?.let { parsePrice(it) }
            val commissionAmount = request.commissionAmount?.let { parsePrice(it) }
            val result =
                ProductService.update(
                    callerId = callerId,
                    productId = productId,
                    name = request.name,
                    productCategoryId = categoryId,
                    unitPrice = unitPrice,
                    commissionAmount = commissionAmount,
                    isActive = request.isActive,
                )
            context.json(result.toResponse())
        }
    }

    private fun parsePrice(value: String): BigDecimal {
        val price =
            runCatching { BigDecimal(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP) }
                .getOrElse { throw BadRequestResponse("Invalid price amount: $value") }
        if (price < BigDecimal.ZERO) throw BadRequestResponse("Price must be non-negative")
        return price
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
