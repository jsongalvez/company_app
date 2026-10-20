package com.companyb.companyapp.branch

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.parseNonNegativeBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branch.SetBranchCommissionRequest
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
    path = ApiRoutes.BRANCH_COMMISSION_PATH,
    methods = [HttpMethod.PUT],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_commission_put",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SetBranchCommissionRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchCommissionRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        // 503bf228 — one commission for every product the branch sells; any member who can edit the branch.
        config.routes.before(ApiRoutes.BRANCH_COMMISSION_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            BranchService.findById(branchId)
            CapabilityFilter.requireBranchCapabilityForBranchId(context, branchId, CapabilityCodes.EDIT_BRANCH_DATA)
        }

        config.routes.put(ApiRoutes.BRANCH_COMMISSION_PATH) { context ->
            val request = context.bodyAsClass<SetBranchCommissionRequest>()
            BranchService.setCommission(
                callerId = context.callerUuid(),
                id = context.pathParamAsUuid(BRANCH_ID_PARAM),
                commissionAmount =
                    request.commissionAmount?.takeIf { it.isNotBlank() }?.let {
                        parseNonNegativeBigDecimal(it, "commissionAmount")
                    },
            )
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
