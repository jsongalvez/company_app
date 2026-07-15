package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.service.MedicalMissionDelegateService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object MedicalMissionDelegateRoutes {
    @Suppress("ThrowsCount")
    fun assignDelegate(config: JavalinConfig) {
        config.routes.post("/api/delegates") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<AssignDelegateRequest>()

            val delegateId =
                runCatching { UUID.fromString(request.delegateId) }
                    .getOrElse { throw BadRequestResponse("Invalid delegate id") }
            val targetUserId =
                runCatching { UUID.fromString(request.targetUserId) }
                    .getOrElse { throw BadRequestResponse("Invalid target user id") }
            val branchId =
                runCatching { UUID.fromString(request.branchId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }

            val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

            context.status(HttpStatus.CREATED)
            context.json(
                DelegateResponse(
                    id = result.id.toString(),
                    targetUser = result.targetUser.toString(),
                    assignedAt = result.assignedAt.toString(),
                    assignedBy = result.assignedBy.toString(),
                    branchId = result.branchId.toString(),
                    endedAt = result.endedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun revokeDelegate(config: JavalinConfig) {
        config.routes.delete("/api/delegates/{delegateId}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val delegateId = context.pathParamAsUuid("delegateId")

            val result = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

            context.status(HttpStatus.OK)
            context.json(
                DelegateResponse(
                    id = result.id.toString(),
                    targetUser = result.targetUser.toString(),
                    assignedAt = result.assignedAt.toString(),
                    assignedBy = result.assignedBy.toString(),
                    branchId = result.branchId.toString(),
                    endedAt = result.endedAt?.toString(),
                ),
            )
        }
    }
}
