package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.service.UserBranchAssignmentService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object UserBranchAssignmentRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val USER_ID_PARAM = "userId"

    @Suppress("LongMethod", "ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/assignments") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val request = context.bodyAsClass<CreateAssignmentRequest>()
            val assignmentId =
                runCatching { UUID.fromString(request.id) }
                    .getOrElse { throw BadRequestResponse("Invalid assignment id") }
            val targetUserId =
                runCatching { UUID.fromString(request.userId) }
                    .getOrElse { throw BadRequestResponse("Invalid user id") }

            val result =
                UserBranchAssignmentService.create(
                    callerId = callerId,
                    id = assignmentId,
                    branchId = branchId,
                    userId = targetUserId,
                    slot = request.slot,
                )

            context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            context.json(result.assignment.toResponse())
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/assignments") { context ->
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }

            context.json(
                UserBranchAssignmentService.findActiveByBranch(branchId).map { it.toResponse() },
            )
        }

        config.routes.delete("/api/branches/{$BRANCH_ID_PARAM}/assignments/{$USER_ID_PARAM}") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val targetUserId =
                runCatching { UUID.fromString(context.pathParam(USER_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid user id") }

            UserBranchAssignmentService.remove(callerId, branchId, targetUserId)
            context.status(HttpStatus.NO_CONTENT)
        }

        config.routes.patch("/api/branches/{$BRANCH_ID_PARAM}/assignments/{$USER_ID_PARAM}/slot") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val targetUserId =
                runCatching { UUID.fromString(context.pathParam(USER_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid user id") }
            val request = context.bodyAsClass<UpdateSlotRequest>()

            UserBranchAssignmentService.updateSlot(callerId, branchId, targetUserId, request.slot)
            context.status(HttpStatus.NO_CONTENT)
        }

        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/slots/swap") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam(BRANCH_ID_PARAM)) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val request = context.bodyAsClass<SwapSlotsRequest>()
            val userIdA =
                runCatching { UUID.fromString(request.userIdA) }
                    .getOrElse { throw BadRequestResponse("Invalid userIdA") }
            val userIdB =
                runCatching { UUID.fromString(request.userIdB) }
                    .getOrElse { throw BadRequestResponse("Invalid userIdB") }

            UserBranchAssignmentService.swapSlots(callerId, branchId, userIdA, userIdB)
            context.status(HttpStatus.NO_CONTENT)
        }
    }

    private fun UserBranchAssignment.toResponse(): AssignmentResponse =
        AssignmentResponse(
            id = id.toString(),
            userId = userId.toString(),
            branchId = branchId.toString(),
            slot = slot,
            assignedBy = assignedBy.toString(),
            assignedAt = assignedAt.toString(),
            endedAt = endedAt?.toString(),
        )
}
