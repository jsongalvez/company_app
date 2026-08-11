package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUserTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by both persisting
 * INACTIVE status (so future tokens fail DB authorization) and adding the user to the
 * in-memory [DenyList] (so already-issued tokens are rejected before any DB lookup).
 */
object UserService {
    private val logger = KotlinLogging.logger {}
    private const val SELF_DEACTIVATE_MESSAGE = "Cannot deactivate yourself"

    fun deactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        if (callerId == targetUserId) {
            throw ValidationException(SELF_DEACTIVATE_MESSAGE)
        }
        val user =
            UserRepository.deactivate(
                targetUserId,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = AppUserTable.tableName,
                        recordId = targetUserId,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        auditFields = AppUserTable::auditFields,
                    )
                },
            )
        if (user == null) {
            throw NotFoundException("User not found")
        }
        DenyList.deny(targetUserId)
        logger.info { "[DEACTIVATE] Deactivation request processed; user added to deny list" }
    }

    fun reactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        val user =
            UserRepository.reactivate(
                targetUserId,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = AppUserTable.tableName,
                        recordId = targetUserId,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        auditFields = AppUserTable::auditFields,
                    )
                },
            )
        if (user == null) {
            throw NotFoundException("User not found")
        }
        logger.info { "[REACTIVATE] Reactivation request processed" }
    }

    fun listUsers(): List<UserSummaryResponse> {
        val users = UserRepository.findAll()
        val assignments =
            UserRepository
                .findActiveAssignmentsWithBranch()
                .groupBy { it.userId }
        return users.map { user ->
            UserSummaryResponse(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                status = user.status.name,
                deactivatedAt =
                    user.deactivatedAt
                        ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                assignments =
                    assignments[UUID.fromString(user.id)]
                        .orEmpty()
                        .map { assignment ->
                            UserAssignmentResponse(
                                branchId = assignment.branchId.toString(),
                                branchName = assignment.branchName,
                                slot = assignment.slot,
                            )
                        },
            )
        }
    }
}
