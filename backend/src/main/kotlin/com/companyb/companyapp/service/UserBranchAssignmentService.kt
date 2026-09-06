package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.repository.BranchMemberRepository
import com.companyb.companyapp.repository.BranchMemberRow
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
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
 * lock ordering (ascending assignment id) lives inside the command's transaction.
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

    private fun canManageUsers(callerId: UUID): Boolean =
        CapabilityService.hasCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

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

        // Existence checks stay here for 404 precedence; ownership (same-ID replay vs
        // conflict, active-key guard) lives in the single transaction-local seam
        // (UserBranchAssignmentRepository.createInTransaction, #453).
        val branchExists = BranchService.findByIdOrNull(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        if (!AccountReads.userExists(userId)) {
            throw NotFoundException("User not found")
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
        assignmentId: UUID,
    ) {
        requireManageUsers(callerId, "MANAGE_USERS capability required to remove assignments")

        val branchExists = BranchService.findByIdOrNull(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        val assignment =
            transaction {
                val target =
                    UserBranchAssignmentRepository
                        .findActiveByIdInTransaction(branchId, assignmentId, forUpdate = true)
                        ?: throw NotFoundException("Active assignment not found")
                val mutation = UserBranchAssignmentRepository.setEndedAtInTransaction(target.id)
                UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), mutation.before, mutation.after)
                mutation.after
            }
        logger.info { "[REMOVE-ASSIGNMENT] Ended assignment ${assignment.id} at branch $branchId" }
    }

    @Suppress("ThrowsCount")
    fun updateSlot(
        callerId: UUID,
        branchId: UUID,
        assignmentId: UUID,
        newSlot: Short,
    ) {
        val canManage = canManageUsers(callerId)
        val assignment =
            transaction {
                val target =
                    UserBranchAssignmentRepository
                        .findActiveByIdInTransaction(branchId, assignmentId, forUpdate = true)
                        ?: throw NotFoundException("Active assignment not found for user at this branch")
                if (!canManage && callerId != target.userId) {
                    throw ForbiddenException("MANAGE_USERS capability required to change another user's slot")
                }
                val mutation = UserBranchAssignmentRepository.updateSlotInTransaction(target.id, newSlot)
                UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), mutation.before, mutation.after)
                mutation.after
            }
        logger.info { "[UPDATE-SLOT] Changed slot for assignment ${assignment.id} to $newSlot" }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        assignmentIdA: UUID,
        assignmentIdB: UUID,
    ) {
        if (assignmentIdA == assignmentIdB) {
            throw ValidationException("Cannot swap an assignment with itself")
        }

        val canManage = canManageUsers(callerId)
        transaction {
            // FOR UPDATE on both rows (materialized via singleOrNull — the #136 lazy-lock
            // lesson) serializes swap against concurrent remove/updateSlot on either user.
            // Locks are taken in ascending assignment-ID order so concurrent opposite-direction
            // swaps (A,B vs B,A) cannot cross-deadlock.
            val lockOrder = listOf(assignmentIdA, assignmentIdB).sorted()
            val assignments =
                lockOrder.associateWith { id ->
                    UserBranchAssignmentRepository
                        .findActiveByIdInTransaction(branchId, id, forUpdate = true)
                        ?: throw NotFoundException("Active assignment not found at this branch")
                }
            val a = assignments.getValue(assignmentIdA)
            val b = assignments.getValue(assignmentIdB)
            if (a.userId == b.userId) {
                throw ValidationException("Cannot swap a user with themselves")
            }
            if (!canManage && callerId != a.userId && callerId != b.userId) {
                throw ForbiddenException("MANAGE_USERS capability required to swap slots")
            }

            UserBranchAssignmentRepository.swapSlotsInTransaction(a.id, a.slot, b.id, b.slot)

            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), a, a.copy(slot = b.slot))
            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), b, b.copy(slot = a.slot))
        }
        logger.info { "[SWAP-SLOTS] Swapped assignments at branch $branchId: $assignmentIdA <-> $assignmentIdB" }
    }

    fun findActiveByBranch(
        callerId: UUID,
        branchId: UUID,
    ): List<UserBranchAssignment> {
        requireManageUsers(callerId, "MANAGE_USERS capability required to view assignments")
        return UserBranchAssignmentRepository.findActiveByBranch(branchId)
    }

    /**
     * #366 — the requested-practitioner picker's directory: ACTIVE members (id + display
     * name) of [branchId]. Gated at the service layer (the ADR-0007 #134 deviation
     * precedent for this surface): a caller passes with either an ACTIVE assignment row
     * at the branch — no capability code involved, so a practitioner without
     * MANAGE_USERS can still read their own branch's names (#366 rule) — or, since
     * #402, the shared today gate (#452): a BRANCH or BRANCH_DAY-for-today
     * `EDIT_BRANCH_DATA` grant (GLOBAL excluded per #131), mirroring the session-create
     * gate exactly, so anyone allowed to create sessions at the branch today can load
     * the names those flows need; off-duty non-members still 403.
     */
    fun listActiveMembers(
        callerId: UUID,
        branchId: UUID,
    ): List<BranchMemberRow> {
        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        if (assignment == null) {
            BranchDayService.requireBranchOrDayForToday(
                userId = callerId,
                branchId = branchId,
                message = "Active membership or a today-scoped edit grant required to view this branch's members",
            )
        }
        return BranchMemberRepository.findActiveMemberNames(branchId)
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
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordUpdate(
        tableName = UserBranchAssignmentTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = UserBranchAssignmentTable::auditFields,
    )
}
