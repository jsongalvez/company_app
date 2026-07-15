package com.companyb.companyapp.api.routes

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
        config.routes.post("/api/branches") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<CreateBranchRequest>()
            val branchId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val result =
                BranchService.create(
                    callerId = callerId,
                    id = branchId,
                    name = request.name,
                    branchType = request.branchType,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.branch.toResponse())
        }

        config.routes.get("/api/branches") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            context.json(BranchService.findAll(callerId).map { it.toResponse() })
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            context.json(BranchService.findById(callerId, branchId).toResponse())
        }
    }

    private fun Branch.toResponse(): BranchResponse =
        BranchResponse(
            id = id.toString(),
            name = name,
            branchType = branchType,
        )
}
