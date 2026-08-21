package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Assignment feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: persistence runs on it via `UserBranchAssignmentRepository.*InTransaction`
 * store operations and the audit row is inserted into the same transaction. The slot-swap
 * lock ordering (ascending user id) lives inside the command's transaction.
 */
object UserBranchAssignmentService {
    private val logger = KotlinLogging.logger {}

    private fun requireManageUsers(
        callerId: UUID,
        message: String,
    ) {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = message,
        )
    }

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
        requireManageUsers(callerId, "MANAGE_USERS capability required to create assignments")

        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        if (!UserRepository.existsById(userId)) {
            throw NotFoundException("User not found")
        }

        val existing = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
        if (existing != null) {
            if (existing.id == id) {
                return CreateResult(existing, created = false)
            }
            throw ValidationException("User already has an active assignment at this branch")
        }

        val result =
            transaction {
                val r =
                    UserBranchAssignmentRepository.createInTransaction(
                        UserBranchAssignmentCreateParams(
                            id = id,
                            userId = userId,
                            branchId = branchId,
                            slot = slot,
                            assignedBy = callerId,
                        ),
                    )
                if (r.created) {
                    UserBranchAssignmentAudit.inserted(AuditContext(callerId, branchId), r.assignment)
                }
                r
            }

        if (result.created) {
            logger.info { "[CREATE-ASSIGNMENT] Created assignment $id for user $userId at branch $branchId slot=$slot" }
        }

        return CreateResult(result.assignment, result.created)
    }

    fun remove(
        callerId: UUID,
        branchId: UUID,
        userId: UUID,
    ) {
        requireManageUsers(callerId, "MANAGE_USERS capability required to remove assignments")

        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        val assignment =
            transaction {
                val target =
                    UserBranchAssignmentRepository
                        .findActiveByBranchAndUserInTransaction(branchId, userId, forUpdate = false)
                        ?: throw NotFoundException("Active assignment not found")
                val mutation = UserBranchAssignmentRepository.setEndedAtInTransaction(target.id)
                UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), mutation.before, mutation.after)
                mutation.after
            }
        logger.info { "[REMOVE-ASSIGNMENT] Ended assignment ${assignment.id} for user $userId at branch $branchId" }
    }

    @Suppress("ThrowsCount")
    fun updateSlot(
        callerId: UUID,
        branchId: UUID,
        targetUserId: UUID,
        newSlot: Short,
    ) {
        val canManage =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        val isSelf = callerId == targetUserId

        if (!canManage && !isSelf) {
            throw ForbiddenException("MANAGE_USERS capability required to change another user's slot")
        }

        val assignment =
            transaction {
                val target =
                    UserBranchAssignmentRepository
                        .findActiveByBranchAndUserInTransaction(branchId, targetUserId, forUpdate = false)
                        ?: throw NotFoundException("Active assignment not found for user at this branch")
                val mutation = UserBranchAssignmentRepository.updateSlotInTransaction(target.id, newSlot)
                UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), mutation.before, mutation.after)
                mutation.after
            }
        logger.info { "[UPDATE-SLOT] Changed slot for assignment ${assignment.id} to $newSlot" }
    }

    @Suppress("ReturnCount")
    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        userIdA: UUID,
        userIdB: UUID,
    ) {
        if (userIdA == userIdB) {
            throw ValidationException("Cannot swap a user with themselves")
        }

        val canManage =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        val isParticipant = callerId == userIdA || callerId == userIdB

        if (!canManage && !isParticipant) {
            throw ForbiddenException("MANAGE_USERS capability required to swap slots")
        }

        transaction {
            // FOR UPDATE on both rows (materialized via singleOrNull — the #136 lazy-lock
            // lesson) serializes swap against concurrent remove/updateSlot on either user.
            // Locks are taken in ascending user-ID order so concurrent opposite-direction
            // swaps (A,B vs B,A) cannot cross-deadlock.
            val lockOrder = listOf(userIdA, userIdB).sorted()
            val assignments =
                lockOrder.associateWith { id ->
                    UserBranchAssignmentRepository
                        .findActiveByBranchAndUserInTransaction(branchId, id, forUpdate = true)
                        ?: throw NotFoundException("Active assignment not found for user $id at this branch")
                }
            val a = assignments.getValue(userIdA)
            val b = assignments.getValue(userIdB)

            UserBranchAssignmentRepository.swapSlotsInTransaction(a.id, a.slot, b.id, b.slot)

            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), a, a.copy(slot = b.slot))
            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), b, b.copy(slot = a.slot))
        }
        logger.info { "[SWAP-SLOTS] Swapped slots at branch $branchId: user $userIdA <-> user $userIdB" }
    }

    fun findActiveByBranch(
        callerId: UUID,
        branchId: UUID,
    ): List<UserBranchAssignment> {
        requireManageUsers(callerId, "MANAGE_USERS capability required to view assignments")
        return UserBranchAssignmentRepository.findActiveByBranch(branchId)
    }
}

/**
 * User-branch-assignment audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside
 * its own transaction so the audit row commits atomically with the mutation. Owns the
 * persistence-table imports so the public command surface does not.
 */
internal object UserBranchAssignmentAudit {
    fun inserted(
        context: AuditContext,
        assignment: UserBranchAssignment,
    ) = AuditLogRepository.recordInsert(
        tableName = UserBranchAssignmentTable.tableName,
        recordId = assignment.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = UserBranchAssignmentTable.auditFields(assignment),
    )

    fun updated(
        context: AuditContext,
        before: UserBranchAssignment,
        after: UserBranchAssignment,
    ) = AuditLogRepository.recordUpdate(
        tableName = UserBranchAssignmentTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = UserBranchAssignmentTable::auditFields,
    )
}
