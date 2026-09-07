package com.companyb.companyapp.commission
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.dto.CommissionInclusionResponse
import com.companyb.companyapp.dto.CommissionSplitResponse
import com.companyb.companyapp.dto.CreateCommissionInclusionRequest
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
    path = ApiRoutes.COMMISSION_INCLUSIONS,
    methods = [HttpMethod.POST],
    operationId = "commission_inclusions",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateCommissionInclusionRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = CommissionInclusionResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.COMMISSION_SPLITS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "commission_splits",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<CommissionSplitResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.COMMISSION_RECALCULATE_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "commission_recalculate",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<CommissionSplitResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object CommissionRoutes {
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.COMMISSION_INCLUSIONS) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.before(ApiRoutes.COMMISSION_SPLITS_PATH) { context ->
            val branchDayId = context.pathParamAsUuid("branchDayId")
            CapabilityFilter.requireBranchCapability(context, branchDayId, CapabilityCodes.VIEW_BRANCH_DATA)
        }

        config.routes.before(ApiRoutes.COMMISSION_RECALCULATE_PATH) { context ->
            val branchDayId = context.pathParamAsUuid("branchDayId")
            CapabilityFilter.requireBranchCapability(
                context,
                branchDayId,
                CapabilityCodes.EDIT_PAST_DAY,
            )
        }

        config.routes.post(ApiRoutes.COMMISSION_INCLUSIONS, ::handleCreateInclusion)
        config.routes.get(ApiRoutes.COMMISSION_SPLITS_PATH, ::handleGetSplits)
        config.routes.post(ApiRoutes.COMMISSION_RECALCULATE_PATH, ::handleRecalculate)
    }

    @Suppress("ThrowsCount")
    private fun handleCreateInclusion(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateCommissionInclusionRequest>()

        val id = uuidOrThrow(request.id, "inclusion id")
        val productSaleId = uuidOrThrow(request.productSaleId, "product sale id")
        val userId = uuidOrThrow(request.userId, "user id")

        val inclusion =
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = id,
                productSaleId = productSaleId,
                userId = userId,
                isIncluded = request.isIncluded,
                reason = request.reason,
            )

        context.status(HttpStatus.CREATED)
        context.json(inclusion.toResponse())
    }

    private fun handleGetSplits(context: Context) {
        val branchDayId = context.pathParamAsUuid("branchDayId")

        val splits = CommissionService.getByBranchDayId(branchDayId)

        context.status(HttpStatus.OK)
        context.json(splits.map { it.toResponse() })
    }

    private fun handleRecalculate(context: Context) {
        val branchDayId = context.pathParamAsUuid("branchDayId")

        CommissionService.manualRecalculate(branchDayId)

        val splits = CommissionService.getByBranchDayId(branchDayId)
        context.status(HttpStatus.OK)
        context.json(splits.map { it.toResponse() })
    }

    private fun CommissionManualInclusion.toResponse(): CommissionInclusionResponse =
        CommissionInclusionResponse(
            id = id.toString(),
            productSaleId = productSaleId.toString(),
            userId = userId.toString(),
            isIncluded = isIncluded,
            reason = reason,
            assignedBy = assignedBy.toString(),
            assignedAt = assignedAt.toString(),
        )

    private fun CommissionSplit.toResponse(): CommissionSplitResponse =
        CommissionSplitResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            userId = userId.toString(),
            amount = amount.toPlainString(),
        )
}
