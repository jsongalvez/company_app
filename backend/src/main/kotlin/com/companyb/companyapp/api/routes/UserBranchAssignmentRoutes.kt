package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchMemberResponse
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
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/branches/{branchId}/assignments",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_assignments_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments",
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_assignments_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments/{assignmentId}",
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "branchId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "assignmentId", type = UUID::class, required = true),
    ],
    operationId = "branch_assignment_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/assignments/{assignmentId}/slot",
    methods = [HttpMethod.PATCH],
    pathParams = [
        OpenApiParam(
            name = "branchId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "assignmentId", type = UUID::class, required = true),
    ],
    operationId = "branch_assignment_slot",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/slots/swap",
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_slots_swap",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/members",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_members_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object UserBranchAssignmentRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val ASSIGNMENT_ID_PARAM = "assignmentId"

    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.BRANCH_ASSIGNMENTS_PATH, ::handleCreateAssignment)
        config.routes.get(ApiRoutes.BRANCH_ASSIGNMENTS_PATH, ::handleGetAssignments)
        config.routes.delete(ApiRoutes.BRANCH_ASSIGNMENT_PATH, ::handleRemoveAssignment)
        config.routes.patch(ApiRoutes.BRANCH_ASSIGNMENT_SLOT_PATH, ::handleUpdateSlot)
        config.routes.post(ApiRoutes.BRANCH_SLOTS_SWAP_PATH, ::handleSwapSlots)
        config.routes.get(ApiRoutes.BRANCH_MEMBERS_PATH, ::handleGetMembers)
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

    /** #366 — active-member directory for the requested-practitioner picker (membership-gated). */
    private fun handleGetMembers(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)

        context.json(
            UserBranchAssignmentService.listActiveMembers(callerId, branchId).map { member ->
                BranchMemberResponse(
                    id = member.id.toString(),
                    displayName = member.displayName,
                )
            },
        )
    }

    private fun handleRemoveAssignment(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val assignmentId = context.pathParamAsUuid(ASSIGNMENT_ID_PARAM)

        UserBranchAssignmentService.remove(callerId, branchId, assignmentId)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleUpdateSlot(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val assignmentId = context.pathParamAsUuid(ASSIGNMENT_ID_PARAM)
        val request = context.bodyAsClass<UpdateSlotRequest>()

        if (request.slot < 1) throw BadRequestResponse("Slot must be 1 or greater")
        UserBranchAssignmentService.updateSlot(callerId, branchId, assignmentId, request.slot)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleSwapSlots(context: Context) {
        val callerId = context.callerUuid()
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val request = context.bodyAsClass<SwapSlotsRequest>()
        val assignmentIdA = uuidOrThrow(request.assignmentIdA, "assignmentIdA")
        val assignmentIdB = uuidOrThrow(request.assignmentIdB, "assignmentIdB")

        UserBranchAssignmentService.swapSlots(callerId, branchId, assignmentIdA, assignmentIdB)
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
