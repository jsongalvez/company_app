package com.companyb.companyapp.branch
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.BranchReadScope
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.CreateBranchRequest
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
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
    path = ApiRoutes.BRANCHES,
    methods = [HttpMethod.GET],
    operationId = "branches_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCHES,
    methods = [HttpMethod.POST],
    operationId = "branches_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateBranchRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCHES_ACCESSIBLE,
    methods = [HttpMethod.GET],
    operationId = "branches_accessible",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCHES) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                "MANAGE_USERS capability required to manage branches",
            )
        }

        // #656 (same class as #653): before(BRANCHES) is exact-segment (#114)
        // and never fires on this 3-segment item path, which had no gate of its
        // own — any authenticated caller could read any branch by UUID,
        // bypassing the BranchReadScope window. Gate on the #131 read window
        // (BRANCH or GLOBAL VIEW_BRANCH_DATA, mirroring the summary/browse reads).
        config.routes.before(ApiRoutes.BRANCH_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        // #131: the Reports picker data source — the caller's read window
        // (distinct BRANCH grants, or all branches for a GLOBAL
        // VIEW_BRANCH_DATA holder). No capability gate: zero-grant callers
        // get an empty list (audit-log "zero-grant empty-not-403" pattern).
        // The 2-segment MANAGE_USERS before-filter above is exact-match and
        // does not fire on this 3-segment path (Javalin 7 segment matching).
        config.routes.get(ApiRoutes.BRANCHES_ACCESSIBLE) { context ->
            val callerId = context.callerUuid()
            val window = BranchReadScope.windowBranchIds(callerId)
            val allBranches = BranchService.findAll()
            val branches =
                if (window == null) {
                    allBranches
                } else {
                    allBranches.filter { it.id in window }
                }
            context.json(branches.map { it.toResponse() })
        }

        config.routes.post(ApiRoutes.BRANCHES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<CreateBranchRequest>()
            val branchId = uuidOrThrow(request.id, "branch id")
            val name = request.name.trim()
            if (name.isBlank()) throw BadRequestResponse("Branch name is required")
            val result =
                BranchService.create(
                    callerId = callerId,
                    id = branchId,
                    name = name,
                    branchType = request.branchType,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.branch.toResponse())
        }

        config.routes.get(ApiRoutes.BRANCHES) { context ->
            context.json(BranchService.findAll().map { it.toResponse() })
        }

        config.routes.get(ApiRoutes.BRANCH_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            context.json(BranchService.findById(branchId).toResponse())
        }
    }

    private fun Branch.toResponse(): BranchResponse =
        BranchResponse(
            id = id.toString(),
            name = name,
            branchType = branchType,
        )
}
