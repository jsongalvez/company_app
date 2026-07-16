package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.UserBranchAssignment
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object UserBranchAssignmentService {
    private val logger = KotlinLogging.logger {}

    data class CreateResult(
        val assignment: UserBranchAssignment,
        val created: Boolean,
    )

    @Suppress("LongParameterList", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        userId: UUID,
        slot: Short,
    ): CreateResult {
        requireManageUsers(callerId)

        if (slot < 1) {
            throw BadRequestResponse("Slot must be 1 or greater")
        }

        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundResponse("Branch not found")
        }

        val userFound =
            transaction {
                AppUserTable
                    .select(AppUserTable.id)
                    .where { AppUserTable.id eq userId }
                    .any()
            }
        if (!userFound) {
            throw NotFoundResponse("User not found")
        }

        val existing = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
        if (existing != null) {
            throw BadRequestResponse("User already has an active assignment at this branch")
        }

        val created = UserBranchAssignmentRepository.create(id, userId, branchId, slot, callerId)

        val assignment =
            UserBranchAssignmentRepository.findById(id)
                ?: error("Assignment not found after create for $id")

        if (created) {
            logger.info { "[CREATE-ASSIGNMENT] Created assignment $id for user $userId at branch $branchId slot=$slot" }
        }

        return CreateResult(assignment, created)
    }

    fun remove(
        callerId: UUID,
        branchId: UUID,
        userId: UUID,
    ) {
        requireManageUsers(callerId)

        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundResponse("Branch not found")
        }

        val assignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
                ?: throw NotFoundResponse("Active assignment not found")

        val auditOldValue =
            AuditLogRepository.jsonFields(
                "id" to assignment.id.toString(),
                "userId" to userId.toString(),
                "branchId" to branchId.toString(),
                "slot" to assignment.slot.toString(),
            )
        UserBranchAssignmentRepository.setEndedAt(assignment.id, callerId, auditOldValue)
        logger.info { "[REMOVE-ASSIGNMENT] Ended assignment ${assignment.id} for user $userId at branch $branchId" }
    }

    @Suppress("ThrowsCount")
    fun updateSlot(
        callerId: UUID,
        branchId: UUID,
        targetUserId: UUID,
        newSlot: Short,
    ) {
        if (newSlot < 1) {
            throw BadRequestResponse("Slot must be 1 or greater")
        }

        val canManage =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        val isSelf = callerId == targetUserId

        if (!canManage && !isSelf) {
            throw ForbiddenResponse("MANAGE_USERS capability required to change another user's slot")
        }

        val assignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, targetUserId)
                ?: throw NotFoundResponse("Active assignment not found for user at this branch")

        val oldSlot = assignment.slot
        UserBranchAssignmentRepository.updateSlot(assignment.id, newSlot, callerId, oldSlot)
        logger.info { "[UPDATE-SLOT] Changed slot for assignment ${assignment.id} from $oldSlot to $newSlot" }
    }

    @Suppress("ReturnCount")
    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        userIdA: UUID,
        userIdB: UUID,
    ) {
        requireManageUsers(callerId)

        val (assignA, assignB) = UserBranchAssignmentRepository.swapSlots(callerId, branchId, userIdA, userIdB)
        val slotA = assignA.slot
        val slotB = assignB.slot
        logger.info { "[SWAP-SLOTS] Swapped slots: user $userIdA ($slotA <-> $slotB) user $userIdB" }
    }

    fun findActiveByBranch(
        callerId: UUID,
        branchId: UUID,
    ): List<UserBranchAssignment> {
        requireManageUsers(callerId)
        return UserBranchAssignmentRepository.findActiveByBranch(branchId)
    }

    private fun requireManageUsers(callerId: UUID) {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )
    }
}
