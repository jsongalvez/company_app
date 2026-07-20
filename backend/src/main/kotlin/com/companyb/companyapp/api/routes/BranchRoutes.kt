package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.service.BranchService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

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
