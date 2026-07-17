package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.service.UserBranchAssignmentService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import java.util.UUID

object UserBranchAssignmentRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val USER_ID_PARAM = "userId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/assignments") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
            )
        }
        config.routes.before("/api/branches/{branchId}/slots") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.MANAGE_USERS,
            )
        }

        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/assignments", ::handleCreateAssignment)
        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/assignments", ::handleGetAssignments)
        config.routes.delete("/api/branches/{$BRANCH_ID_PARAM}/assignments/{$USER_ID_PARAM}", ::handleRemoveAssignment)
        config.routes.patch("/api/branches/{$BRANCH_ID_PARAM}/assignments/{$USER_ID_PARAM}/slot", ::handleUpdateSlot)
        config.routes.post("/api/branches/{$BRANCH_ID_PARAM}/slots/swap", ::handleSwapSlots)
    }

    private fun handleCreateAssignment(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val request = context.bodyAsClass<CreateAssignmentRequest>()
        val assignmentId = uuidOrThrow(request.id, "assignment id")
        val targetUserId = uuidOrThrow(request.userId, "user id")

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

    private fun handleGetAssignments(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

        context.json(
            UserBranchAssignmentService.findActiveByBranch(branchId).map { it.toResponse() },
        )
    }

    private fun handleRemoveAssignment(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)

        UserBranchAssignmentService.remove(callerId, branchId, targetUserId)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleUpdateSlot(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val targetUserId = context.pathParamAsUuid(USER_ID_PARAM)
        val request = context.bodyAsClass<UpdateSlotRequest>()

        UserBranchAssignmentService.updateSlot(callerId, branchId, targetUserId, request.slot)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleSwapSlots(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val request = context.bodyAsClass<SwapSlotsRequest>()
        val userIdA = uuidOrThrow(request.userIdA, "userIdA")
        val userIdB = uuidOrThrow(request.userIdB, "userIdB")

        UserBranchAssignmentService.swapSlots(callerId, branchId, userIdA, userIdB)
        context.status(HttpStatus.NO_CONTENT)
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
