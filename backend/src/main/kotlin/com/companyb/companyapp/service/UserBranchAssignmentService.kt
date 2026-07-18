package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        val userFound =
            transaction {
                AppUserTable
                    .select(AppUserTable.id)
                    .where { AppUserTable.id eq userId }
                    .any()
            }
        if (!userFound) {
            throw NotFoundException("User not found")
        }

        val existing = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
        if (existing != null) {
            throw ValidationException("User already has an active assignment at this branch")
        }

        val created =
            UserBranchAssignmentRepository.create(
                UserBranchAssignmentCreateParams(
                    id = id,
                    userId = userId,
                    branchId = branchId,
                    slot = slot,
                    assignedBy = callerId,
                ),
                auditFn = { assignment ->
                    AuditLogRepository.record(
                        tableName = UserBranchAssignmentTable.tableName,
                        recordId = assignment.id,
                        action = AuditAction.INSERT,
                        changedBy = callerId,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "id" to assignment.id.toString(),
                                "userId" to assignment.userId.toString(),
                                "branchId" to assignment.branchId.toString(),
                                "slot" to assignment.slot.toString(),
                            ),
                    )
                },
            )

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
        val branchExists = BranchRepository.findById(branchId)
        if (branchExists == null) {
            throw NotFoundException("Branch not found")
        }

        val assignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId)
                ?: throw NotFoundException("Active assignment not found")

        val auditOldValue =
            AuditLogRepository.jsonFields(
                "id" to assignment.id.toString(),
                "userId" to userId.toString(),
                "branchId" to branchId.toString(),
                "slot" to assignment.slot.toString(),
            )
        UserBranchAssignmentRepository.setEndedAt(
            assignment.id,
            auditFn = { updated ->
                AuditLogRepository.record(
                    tableName = UserBranchAssignmentTable.tableName,
                    recordId = updated.id,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue = auditOldValue,
                )
            },
        )
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
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, targetUserId)
                ?: throw NotFoundException("Active assignment not found for user at this branch")

        val oldSlot = assignment.slot
        UserBranchAssignmentRepository.updateSlot(
            assignment.id,
            newSlot,
            auditFn = { updated ->
                AuditLogRepository.record(
                    tableName = UserBranchAssignmentTable.tableName,
                    recordId = updated.id,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue = AuditLogRepository.jsonField("slot", oldSlot.toString()),
                    newValue = AuditLogRepository.jsonField("slot", newSlot.toString()),
                )
            },
        )
        logger.info { "[UPDATE-SLOT] Changed slot for assignment ${assignment.id} from $oldSlot to $newSlot" }
    }

    @Suppress("ReturnCount")
    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        userIdA: UUID,
        userIdB: UUID,
    ) {
        val (assignA, assignB) =
            UserBranchAssignmentRepository.swapSlots(
                branchId,
                userIdA,
                userIdB,
                auditFn = { a, b ->
                    AuditLogRepository.record(
                        tableName = UserBranchAssignmentTable.tableName,
                        recordId = a.id,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "userId" to a.userId.toString(),
                                "oldSlot" to a.slot.toString(),
                                "newSlot" to b.slot.toString(),
                            ),
                    )
                    AuditLogRepository.record(
                        tableName = UserBranchAssignmentTable.tableName,
                        recordId = b.id,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "userId" to b.userId.toString(),
                                "oldSlot" to b.slot.toString(),
                                "newSlot" to a.slot.toString(),
                            ),
                    )
                },
            )
        val slotA = assignA.slot
        val slotB = assignB.slot
        logger.info { "[SWAP-SLOTS] Swapped slots: user $userIdA ($slotA <-> $slotB) user $userIdB" }
    }

    fun findActiveByBranch(branchId: UUID): List<UserBranchAssignment> =
        UserBranchAssignmentRepository.findActiveByBranch(branchId)
}
