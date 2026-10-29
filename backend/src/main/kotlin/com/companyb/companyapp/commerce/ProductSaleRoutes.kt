package com.companyb.companyapp.commerce
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidFromQuery
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.commerce.CreateProductSaleRequest
import com.companyb.companyapp.contracts.commerce.DeleteProductSaleRequest
import com.companyb.companyapp.contracts.commerce.ProductSaleResponse
import com.companyb.companyapp.contracts.commerce.RestoreProductSaleRequest
import com.companyb.companyapp.contracts.commerce.UpdateProductSaleRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.NotFoundResponse
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
    path = ApiRoutes.PRODUCT_SALES,
    methods = [HttpMethod.POST],
    operationId = "product_sales",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateProductSaleRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ProductSaleResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_SALES,
    methods = [HttpMethod.GET],
    queryParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "product_sales_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ProductSaleResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_SALE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "saleId", type = UUID::class, required = true)],
    operationId = "product_sale_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateProductSaleRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductSaleResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_SALE_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [OpenApiParam(name = "saleId", type = UUID::class, required = true)],
    operationId = "product_sale_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = DeleteProductSaleRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductSaleResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.PRODUCT_SALE_RESTORE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "saleId", type = UUID::class, required = true)],
    operationId = "product_sale_restore",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RestoreProductSaleRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ProductSaleResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ProductSaleRoutes {
    fun register(config: JavalinConfig) {
        registerGuards(config)
        config.routes.get(ApiRoutes.PRODUCT_SALES, ::handleList)
        config.routes.post(ApiRoutes.PRODUCT_SALES, ::handleCreate)
        config.routes.patch(ApiRoutes.PRODUCT_SALE_PATH, ::handleChange)
        config.routes.delete(ApiRoutes.PRODUCT_SALE_PATH, ::handleDelete)
        config.routes.post(ApiRoutes.PRODUCT_SALE_RESTORE_PATH, ::handleRestore)
    }

    private fun registerGuards(config: JavalinConfig) {
        config.routes.before(ApiRoutes.PRODUCT_SALES) { context ->
            when (context.method()) {
                HandlerType.POST -> {
                    val request = context.bodyAsClass<CreateProductSaleRequest>()
                    val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
                    CapabilityFilter.requireBranchOrBranchDayCapability(
                        context,
                        branchDayId,
                        CapabilityCodes.EDIT_BRANCH_DATA,
                    )
                }

                HandlerType.GET -> {
                    CapabilityFilter.requireBranchOrBranchDayCapability(
                        context,
                        context.uuidFromQuery("branchDayId"),
                        CapabilityCodes.VIEW_BRANCH_DATA,
                    )
                }

                else -> {}
            }
        }
        // A sale's own path and its restore path are separate segments, so each needs its guard.
        for (path in listOf(ApiRoutes.PRODUCT_SALE_PATH, ApiRoutes.PRODUCT_SALE_RESTORE_PATH)) {
            config.routes.before(path) { context ->
                val sale =
                    ProductSaleRepository.findById(context.pathParamAsUuid("saleId"))
                        ?: throw NotFoundResponse("Product sale not found")
                CapabilityFilter.requireBranchOrBranchDayCapability(
                    context,
                    sale.branchDayId,
                    CapabilityCodes.EDIT_BRANCH_DATA,
                )
            }
        }
    }

    private fun handleList(context: Context) {
        val branchDayId = context.uuidFromQuery("branchDayId")
        BranchDayService.requireBranchDayExists(branchDayId)
        context.json(ProductSaleRepository.findByBranchDayId(branchDayId).map { it.toResponse() })
    }

    private fun handleCreate(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateProductSaleRequest>()

        val id = uuidOrThrow(request.id, "sale id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
        val sessionId = request.sessionId?.let { uuidOrThrow(it, "session id") }
        val clientId = request.clientId?.let { uuidOrThrow(it, "client id") }
        val productId = uuidOrThrow(request.productId, "product id")

        requireQuantity(request.quantity)
        validateSaleLinks(sessionId, clientId, request.isWalkIn)

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = id,
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = clientId,
                isWalkIn = request.isWalkIn,
                productId = productId,
                quantity = request.quantity,
                expectedVersion = request.expectedVersion,
                reason = request.reason,
            )

        context.status(HttpStatus.CREATED)
        context.json(sale.toResponse())
    }

    private fun handleChange(context: Context) {
        val request = context.bodyAsClass<UpdateProductSaleRequest>()
        requireQuantity(request.quantity)
        val sale =
            ProductSaleCorrection.change(
                callerId = context.callerUuid(),
                saleId = context.pathParamAsUuid("saleId"),
                to =
                    SaleChange(
                        uuidOrThrow(request.productId, "product id"),
                        request.quantity,
                        request.expectedVersion,
                    ),
                reason = request.reason,
            )
        context.json(sale.toResponse())
    }

    private fun handleDelete(context: Context) {
        val request = context.bodyAsClass<DeleteProductSaleRequest>()
        if (request.reason.isBlank()) throw BadRequestResponse("Reason is required to delete a product sale")
        val sale =
            ProductSaleCorrection.delete(
                callerId = context.callerUuid(),
                saleId = context.pathParamAsUuid("saleId"),
                reason = request.reason,
                expectedVersion = request.expectedVersion,
            )
        context.json(sale.toResponse())
    }

    private fun handleRestore(context: Context) {
        val request = context.bodyAsClass<RestoreProductSaleRequest>()
        val sale =
            ProductSaleCorrection.restore(
                context.callerUuid(),
                context.pathParamAsUuid("saleId"),
                request.reason,
            )
        context.json(sale.toResponse())
    }

    private fun requireQuantity(quantity: Int) {
        if (quantity < 1 || quantity > Short.MAX_VALUE) {
            throw BadRequestResponse("Quantity must be between 1 and ${Short.MAX_VALUE}")
        }
    }

    private fun validateSaleLinks(
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
    ) {
        if (sessionId != null && clientId != null) {
            throw BadRequestResponse("Session-linked sale must not have a clientId")
        }
        if (sessionId != null && isWalkIn) {
            throw BadRequestResponse("Session-linked sale must not be a walk-in")
        }
        if (sessionId == null && clientId != null && !isWalkIn) {
            throw BadRequestResponse("Walk-in sale with known client must set isWalkIn=true")
        }
        if (sessionId == null && clientId == null && !isWalkIn) {
            throw BadRequestResponse("Anonymous sale must set isWalkIn=true")
        }
    }

    private fun ProductSale.toResponse(): ProductSaleResponse =
        ProductSaleResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            sessionId = sessionId?.toString(),
            clientId = clientId?.toString(),
            isWalkIn = isWalkIn,
            productId = productId.toString(),
            productName = productName,
            handledBy = handledBy.toString(),
            quantity = quantity,
            unitPriceAtTime = unitPriceAtTime.toPlainString(),
            totalAmountAtTime = totalAmountAtTime.toPlainString(),
            commissionAmountAtTime = commissionAmountAtTime.toPlainString(),
            soldAt = soldAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
            deletedReason = deletedReason,
            version = version,
        )
}
