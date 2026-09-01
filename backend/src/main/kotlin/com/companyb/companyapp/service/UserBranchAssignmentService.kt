package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchMemberRepository
import com.companyb.companyapp.repository.BranchMemberRow
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.service.branchday.BranchDayService
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
        assignmentId: UUID,
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
        val canManage =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
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

        val canManage =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = CapabilityCodes.MANAGE_USERS,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
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
     * #402, an `EDIT_BRANCH_DATA` grant at the branch: BRANCH or BRANCH_DAY-for-today
     * via [CapabilityService.hasCapabilityForBranchDay] when a day row exists, plain
     * BRANCH when none does yet (the session-create fallback; GLOBAL deliberately
     * excluded per the #131 strictness). The relief leg mirrors the
     * session-create gate exactly (#157 find-only day resolution), so anyone allowed to
     * create sessions or add practitioners at the branch today can load the names those
     * flows need; off-duty non-members still 403.
     */
    fun listActiveMembers(
        callerId: UUID,
        branchId: UUID,
    ): List<BranchMemberRow> {
        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        if (assignment == null) {
            // Same resolution as the session-create gate (#157): find-only day lookup so
            // gate and downstream writes never disagree about which day governs. With a
            // day row, BRANCH and BRANCH_DAY grants pass (GLOBAL excluded — #131); with
            // no day row yet, the plain BRANCH leg alone governs, matching
            // SessionRoutes' create fallback.
            val todayBranchDay = BranchDayService.findToday(branchId)
            val authorized =
                if (todayBranchDay != null) {
                    CapabilityService.hasCapabilityForBranchDay(
                        userId = callerId,
                        capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                        branchId = branchId,
                        branchDayId = todayBranchDay.id,
                    )
                } else {
                    CapabilityService.hasCapability(
                        userId = callerId,
                        capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                        contextType = CapabilityContextType.BRANCH,
                        contextId = branchId,
                    )
                }
            if (!authorized) {
                throw ForbiddenException(
                    "Active membership or a today-scoped edit grant required to view this branch's members",
                )
            }
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
