package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
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
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.PRODUCTS,
    methods = [HttpMethod.GET],
    operationId = "products_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.PRODUCTS,
    methods = [HttpMethod.POST],
    operationId = "products_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/products/{productId}",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "productId", type = UUID::class, required = true)],
    operationId = "product_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/products/{productId}",
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "productId", type = UUID::class, required = true)],
    operationId = "product_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object ProductRoutes {
    private const val PRODUCT_ID_PARAM = "productId"

    @Suppress("ThrowsCount", "LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.PRODUCTS) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_CATALOG,
                "MANAGE_CATALOG capability required to manage products",
            )
        }

        config.routes.before(ApiRoutes.PRODUCT_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_CATALOG,
                "MANAGE_CATALOG capability required to manage products",
            )
        }

        config.routes.post(ApiRoutes.PRODUCTS) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateProductRequest>()
            val productId = uuidOrThrow(request.id, "product id")
            val categoryId = uuidOrThrow(request.productCategoryId, "product category id")
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Product name is required")
            val unitPrice = parseNonNegativeBigDecimal(request.unitPrice, "price")
            val commissionAmount = parseNonNegativeBigDecimal(request.commissionAmount, "commission")
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

        config.routes.get(ApiRoutes.PRODUCTS) { context ->
            context.json(ProductService.findAllActive().map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.PRODUCT_PATH) { context ->
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            context.json(ProductService.findById(productId).toResponse())
        }

        config.routes.patch(ApiRoutes.PRODUCT_PATH) { context ->
            val callerId = context.callerUuid()
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            val request = context.bodyAsClass<UpdateProductRequest>()
            val updateName = request.name?.trim()
            if (updateName != null && updateName.isBlank()) {
                throw BadRequestResponse("Product name cannot be blank")
            }
            val categoryId = request.productCategoryId?.let { uuidOrThrow(it, "product category id") }
            val unitPrice = request.unitPrice?.let { parseNonNegativeBigDecimal(it, "price") }
            val commissionAmount = request.commissionAmount?.let { parseNonNegativeBigDecimal(it, "commission amount") }
            val result =
                ProductService.update(
                    callerId = callerId,
                    productId = productId,
                    name = updateName,
                    productCategoryId = categoryId,
                    unitPrice = unitPrice,
                    commissionAmount = commissionAmount,
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
