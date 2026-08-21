package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by both persisting
 * INACTIVE status (so future tokens fail DB authorization) and adding the user to the
 * in-memory [DenyList] (so already-issued tokens are rejected before any DB lookup).
 *
 * Mutating commands own exactly one transaction (#323, ADR-0024): persistence runs on it
 * via `UserRepository.*InTransaction` store operations and the audit row is inserted into
 * the same transaction, so status change + audit commit atomically or not at all.
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
        val transition =
            transaction {
                val t =
                    UserRepository.deactivateInTransaction(targetUserId)
                        ?: throw NotFoundException("User not found")
                if (t.changed) {
                    UserAudit.statusUpdated(AuditContext(callerId), t.before, t.after)
                }
                t
            }
        DenyList.denyAt(
            targetUserId,
            transition.after.jwtRevokedAt?.toInstant()
                ?: error("Deactivation did not persist JWT revocation"),
        )
        logger.info { "[DEACTIVATE] Deactivation request processed; user added to deny list" }
    }

    fun reactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        transaction {
            val t =
                UserRepository.reactivateInTransaction(targetUserId)
                    ?: throw NotFoundException("User not found")
            if (t.changed) {
                UserAudit.statusUpdated(AuditContext(callerId), t.before, t.after)
            }
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
                status =
                    com.companyb.companyapp.domain.UserStatus
                        .valueOf(user.status.name),
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

/**
 * User audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not. Global-scoped — user administration has no
 * branch/day context.
 */
internal object UserAudit {
    fun statusUpdated(
        context: AuditContext,
        before: AppUser,
        after: AppUser,
    ) = AuditLogRepository.recordUpdate(
        tableName = AppUserTable.tableName,
        recordId = UUID.fromString(after.id),
        before = before,
        after = after,
        changedBy = context.changedBy,
        auditFields = AppUserTable::auditFields,
    )
}
