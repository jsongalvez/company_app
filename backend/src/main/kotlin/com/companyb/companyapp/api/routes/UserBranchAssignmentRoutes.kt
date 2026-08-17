package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
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
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/branches/{branchId}/assignments",
    methods = [HttpMethod.GET],
    operationId = "branch_assignments_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments",
    methods = [HttpMethod.POST],
    operationId = "branch_assignments_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments/{userId}",
    methods = [HttpMethod.DELETE],
    operationId = "branch_assignment_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments/{userId}/slot",
    methods = [HttpMethod.PATCH],
    operationId = "branch_assignment_slot",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/slots/swap",
    methods = [HttpMethod.POST],
    operationId = "branch_slots_swap",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object UserBranchAssignmentRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val USER_ID_PARAM = "userId"

    fun register(config: JavalinConfig) {
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
        if (request.slot < 1) throw BadRequestResponse("Slot must be 1 or greater")
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
            UserBranchAssignmentService.findActiveByBranch(callerId, branchId).map { it.toResponse() },
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

        if (request.slot < 1) throw BadRequestResponse("Slot must be 1 or greater")
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
