package com.companyb.companyapp.commerce

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.commerce.SetBranchPriceRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
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
    path = ApiRoutes.BRANCH_INVENTORY_PRICE_PATH,
    methods = [HttpMethod.PUT],
    pathParams = [
        OpenApiParam(name = "branchId", type = UUID::class, required = true),
        OpenApiParam(name = "productId", type = UUID::class, required = true),
    ],
    operationId = "branch_inventory_price_put",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SetBranchPriceRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchPriceRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val PRODUCT_ID_PARAM = "productId"

    fun register(config: JavalinConfig) {
        // 503bf228 — any branch member who can edit the branch sets its prices (unknown branch 404s first).
        config.routes.before(ApiRoutes.BRANCH_INVENTORY_PRICE_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            BranchService.findById(branchId)
            CapabilityFilter.requireBranchCapabilityForBranchId(context, branchId, CapabilityCodes.EDIT_BRANCH_DATA)
        }

        config.routes.put(ApiRoutes.BRANCH_INVENTORY_PRICE_PATH) { context ->
            val request = context.bodyAsClass<SetBranchPriceRequest>()
            BranchPriceService.setPrice(
                callerId = context.callerUuid(),
                branchId = context.pathParamAsUuid(BRANCH_ID_PARAM),
                productId = context.pathParamAsUuid(PRODUCT_ID_PARAM),
                unitPrice = request.unitPrice.optionalMoney("unitPrice"),
                commissionAmount = request.commissionAmount.optionalMoney("commissionAmount"),
            )
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    private fun String?.optionalMoney(field: String) =
        this
            ?.takeIf {
                it.isNotBlank()
            }?.let { parseNonNegativeBigDecimal(it, field) }
}
