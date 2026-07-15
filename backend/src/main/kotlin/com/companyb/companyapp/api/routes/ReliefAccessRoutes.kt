package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.service.ReliefAccessService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object ReliefAccessRoutes {
    @Suppress("ThrowsCount")
    fun grantReliefAccess(config: JavalinConfig) {
        config.routes.patch("/api/relief-access/{requestId}/grant") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val requestId = context.pathParamAsUuid("requestId")

            val result = ReliefAccessService.grantAccess(requestId, callerId)

            context.status(HttpStatus.OK)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun denyReliefAccess(config: JavalinConfig) {
        config.routes.patch("/api/relief-access/{requestId}/deny") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val requestId = context.pathParamAsUuid("requestId")

            val result = ReliefAccessService.denyAccess(requestId, callerId)

            context.status(HttpStatus.OK)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun requestReliefAccess(config: JavalinConfig) {
        config.routes.post("/api/relief-access/request") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val request = context.bodyAsClass<ReliefAccessRequest>()

            val requestId =
                runCatching { UUID.fromString(request.requestId) }
                    .getOrElse { throw BadRequestResponse("Invalid request id") }
            val branchDayId =
                runCatching { UUID.fromString(request.branchDayId) }
                    .getOrElse { throw BadRequestResponse("Invalid branch day id") }
            val targetUserId =
                runCatching { UUID.fromString(request.targetUserId) }
                    .getOrElse { throw BadRequestResponse("Invalid target user id") }

            val result = ReliefAccessService.requestReliefAccess(requestId, branchDayId, targetUserId, callerId)

            context.status(HttpStatus.CREATED)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }
}
