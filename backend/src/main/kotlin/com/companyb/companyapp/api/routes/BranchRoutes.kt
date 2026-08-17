package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.service.BranchReadScope
import com.companyb.companyapp.service.BranchService
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
    path = "/api/branches",
    methods = [HttpMethod.GET],
    operationId = "branches_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches",
    methods = [HttpMethod.POST],
    operationId = "branches_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/accessible",
    methods = [HttpMethod.GET],
    operationId = "branches_accessible",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object BranchRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
                "MANAGE_USERS capability required to manage branches",
            )
        }

        // #131: the Reports picker data source — the caller's read window
        // (distinct BRANCH grants, or all branches for a GLOBAL
        // VIEW_BRANCH_DATA holder). No capability gate: zero-grant callers
        // get an empty list (audit-log "zero-grant empty-not-403" pattern).
        // The 2-segment MANAGE_USERS before-filter above is exact-match and
        // does not fire on this 3-segment path (Javalin 7 segment matching).
        config.routes.get("/api/branches/accessible") { context ->
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

        config.routes.post("/api/branches") { context ->
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

        config.routes.get("/api/branches") { context ->
            context.json(BranchService.findAll().map { it.toResponse() })
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}") { context ->
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
