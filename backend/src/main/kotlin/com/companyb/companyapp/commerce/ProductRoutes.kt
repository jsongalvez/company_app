package com.companyb.companyapp.commerce
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.commerce.CreateProductRequest
import com.companyb.companyapp.contracts.commerce.ProductResponse
import com.companyb.companyapp.contracts.commerce.UpdateProductRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.PRODUCTS,
    methods = [HttpMethod.GET],
    operationId = "products_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ProductResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCTS,
    methods = [HttpMethod.POST],
    operationId = "products_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateProductRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ProductResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "productId", type = UUID::class, required = true)],
    operationId = "product_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "productId", type = UUID::class, required = true)],
    operationId = "product_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateProductRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ProductRoutes {
    private const val PRODUCT_ID_PARAM = "productId"
    private const val INCLUDE_INACTIVE_PARAM = "includeInactive"

    fun register(config: JavalinConfig) {
        registerGuards(config)
        registerMutations(config)
        registerReads(config)
    }

    private fun registerGuards(config: JavalinConfig) {
        config.routes.before(ApiRoutes.PRODUCTS) { context ->
            CapabilityFilter.requireManageCatalog(
                context,
                "MANAGE_CATALOG capability required to manage products",
            )
        }

        config.routes.before(ApiRoutes.PRODUCT_PATH) { context ->
            CapabilityFilter.requireManageCatalog(
                context,
                "MANAGE_CATALOG capability required to manage products",
            )
        }
    }

    private fun registerMutations(config: JavalinConfig) {
        config.routes.post(ApiRoutes.PRODUCTS, ::handleCreate)
        config.routes.patch(ApiRoutes.PRODUCT_PATH, ::handleUpdate)
    }

    private fun registerReads(config: JavalinConfig) {
        config.routes.get(ApiRoutes.PRODUCTS) { context ->
            val includeInactive = context.queryParam(INCLUDE_INACTIVE_PARAM)?.toBooleanStrictOrNull() == true
            val products =
                if (includeInactive) {
                    ProductService.findAll()
                } else {
                    ProductService.findAllActive()
                }
            context.json(products.map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.PRODUCT_PATH) { context ->
            val productId = context.pathParamAsUuid(PRODUCT_ID_PARAM)
            context.json(ProductService.findById(productId).toResponse())
        }
    }

    private fun handleCreate(context: Context) {
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

    private fun handleUpdate(context: Context) {
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
