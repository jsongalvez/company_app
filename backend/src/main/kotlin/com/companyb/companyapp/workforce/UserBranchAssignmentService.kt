package com.companyb.companyapp.workforce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Assignment feature commands (ADR-0024). Each mutating command owns exactly one
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
        Access.require(callerId, Act.MANAGE_USERS, message = message)
    }

    private fun canManageUsers(callerId: UUID): Boolean = Access.allows(callerId, Act.MANAGE_USERS)

    data class CreateResult(
        val assignment: UserBranchAssignment,
        val created: Boolean,
    )

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
        //  (UserBranchAssignmentRepository.createInTransaction).
        val branchExists = BranchService.findByIdOrNull(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        if (!AccountReads.userExists(userId)) {
            throw NotFoundException("User not found")
        }

        val result =
            transaction {
                requireNotAccountant(userId)
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

    /** "An accountant ... can't be a member of any branch" (user, message 13065). */
    private fun requireNotAccountant(userId: UUID) {
        if (AccountReads.isAccountantInTransaction(userId)) {
            throw ValidationException("An accountant can't have a home branch.")
        }
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

    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        assignmentIdA: UUID,
        assignmentIdB: UUID,
    ) {
        // swap guards split into named checks (ThrowsCount budget is 2 per function).
        requireDistinctAssignments(assignmentIdA, assignmentIdB)

        val canManage = canManageUsers(callerId)
        transaction {
            // FOR UPDATE on both rows (materialized via singleOrNull — the lazy-lock
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
            requireDistinctUsers(a, b)
            requireSwapAuthority(callerId, canManage, a, b)

            UserBranchAssignmentRepository.swapSlotsInTransaction(a.id, a.slot, b.id, b.slot)

            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), a, a.copy(slot = b.slot))
            UserBranchAssignmentAudit.updated(AuditContext(callerId, branchId), b, b.copy(slot = a.slot))
        }
        logger.info { "[SWAP-SLOTS] Swapped assignments at branch $branchId: $assignmentIdA <-> $assignmentIdB" }
    }

    private fun requireDistinctAssignments(
        assignmentIdA: UUID,
        assignmentIdB: UUID,
    ) {
        if (assignmentIdA == assignmentIdB) {
            throw ValidationException("Cannot swap an assignment with itself")
        }
    }

    private fun requireDistinctUsers(
        a: UserBranchAssignment,
        b: UserBranchAssignment,
    ) {
        if (a.userId == b.userId) {
            throw ValidationException("Cannot swap a user with themselves")
        }
    }

    private fun requireSwapAuthority(
        callerId: UUID,
        canManage: Boolean,
        a: UserBranchAssignment,
        b: UserBranchAssignment,
    ) {
        if (!canManage && callerId != a.userId && callerId != b.userId) {
            throw ForbiddenException("MANAGE_USERS capability required to swap slots")
        }
    }

    fun findActiveByBranch(
        callerId: UUID,
        branchId: UUID,
    ): List<UserBranchAssignment> {
        // Whoever manages users sees every branch's staff; anyone else only a branch they can see,
        // as its price list (feedback a11802ff). Unknown branches 404 only past that gate, so no existence leak.
        if (!canManageUsers(callerId)) {
            Access.require(
                callerId,
                Act.SEE_BRANCH,
                Place.Branch(branchId),
                "Only this branch's members can see its staff",
            )
        }
        BranchService.findById(branchId)
        return UserBranchAssignmentRepository.findActiveByBranch(branchId)
    }

    /**
     * the requested-practitioner picker's directory: ACTIVE members (id + display
     * name) of [branchId]. Gated at the service layer (the ADR-0007 #134 deviation
     * precedent for this surface): a caller passes with either an ACTIVE assignment row
     * at the branch — no capability code involved, so a practitioner without
     * MANAGE_USERS can still read their own branch's names (rule) — or, since
     * #402, the shared today gate: a BRANCH or BRANCH_DAY-for-today
     * `EDIT_BRANCH_DATA` grant (GLOBAL excluded), mirroring the session-create
     * gate exactly, so anyone allowed to create sessions at the branch today can load
     * the names those flows need; off-duty non-members still 403.
     */
    fun listActiveMembers(
        callerId: UUID,
        branchId: UUID,
    ): List<BranchMemberRow> {
        // 404 precedence for an unknown branch before the membership gate:
        // findActiveByBranchAndUser alone conflates "unknown branch" with "known but
        // non-member" and surfaces 403 (the mark order).
        BranchService.findById(branchId)
        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        if (assignment == null) {
            Access.require(
                callerId,
                Act.SEE_DAY,
                Place.today(branchId),
                "Active membership or a view of this branch's day required to view its members",
            )
        }
        return BranchMemberRepository.findActiveMemberNames(branchId)
    }
}

/**
 * User-branch-assignment audit vocabulary (ADR-0024 rule 3). Called by the command inside
 * its own transaction so the audit row commits atomically with the mutation. Owns the
 * persistence-table imports so the public command surface does not.
 */
internal object UserBranchAssignmentAudit {
    fun inserted(
        context: AuditContext,
        assignment: UserBranchAssignment,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = context.changedBy, branchId = context.branchId),
        row = AuditedRow(UserBranchAssignmentTable.tableName, assignment.id),
        fields = UserBranchAssignmentTable.auditFields(assignment),
    )

    fun updated(
        context: AuditContext,
        before: UserBranchAssignment,
        after: UserBranchAssignment,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = context.changedBy, branchId = context.branchId),
        row = AuditedRow(UserBranchAssignmentTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = UserBranchAssignmentTable::auditFields,
    )
}
