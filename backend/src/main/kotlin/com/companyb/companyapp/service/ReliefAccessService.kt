package com.companyb.companyapp.service

import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.GrantWithCapabilityParams
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Relief-access feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: the Branch Day gate runs inside it, persistence runs via
 * `ReliefAccessRepository.*InTransaction` store operations, and the audit row is inserted
 * into the same transaction — so mutation + audit commit atomically or not at all.
 */
object ReliefAccessService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "ReturnCount")
    fun grantAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can grant this request")
        }

        if (request.requestStatus == ReliefAccessStatus.GRANTED) {
            return request
        }

        val result =
            transaction {
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

                val mutation =
                    ReliefAccessRepository.grantInTransaction(
                        GrantWithCapabilityParams(
                            requestId = requestId,
                            grantedBy = callerId,
                            userId = request.requestedBy,
                            branchDayId = request.branchDayId,
                            sourceId = requestId,
                            validTo = BranchDayService.expirationUtc(branchDay.date),
                            requestedBy = request.requestedBy,
                        ),
                    ) ?: error("Grant failed: relief access request not found in transaction")

                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                }
                mutation.after
            }

        if (result.requestStatus == ReliefAccessStatus.DENIED) {
            logger.info { "[RELIEF-ACCESS-GRANT] Request $requestId was already denied" }
        } else if (result.id == requestId) {
            logger.info { "[RELIEF-ACCESS-GRANT] Request $requestId granted by $callerId" }
        } else {
            logger.info {
                "[RELIEF-ACCESS-GRANT] Duplicate grant: " +
                    "returning existing ${result.id} for request $requestId"
            }
        }

        return result
    }

    @Suppress("ThrowsCount")
    fun denyAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can deny this request")
        }

        if (request.requestStatus == ReliefAccessStatus.DENIED) {
            return request
        }

        if (request.requestStatus == ReliefAccessStatus.GRANTED) {
            throw ValidationException("Cannot deny a request that has already been granted")
        }

        val result =
            transaction {
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

                val mutation = ReliefAccessRepository.denyInTransaction(requestId)
                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                }
                mutation.after
            }

        if (result.requestStatus == ReliefAccessStatus.GRANTED) {
            throw ValidationException("Cannot deny a request that has already been granted")
        }

        logger.info { "[RELIEF-ACCESS-DENY] Request $requestId denied by $callerId" }

        return result
    }

    @Suppress("ThrowsCount")
    fun requestReliefAccess(
        requestId: UUID,
        branchDayId: UUID,
        targetUserId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val targetHasClockIn = ReliefAccessRepository.hasActiveClockIn(targetUserId, branchDayId)
        if (!targetHasClockIn) {
            throw ValidationException("Target user does not have an active clock-in on this branch day")
        }

        val requesterIsRelief = ReliefAccessRepository.isReliefUser(callerId, branchDayId)
        if (!requesterIsRelief) {
            throw ForbiddenException("Only relief users can request relief access")
        }

        val (reliefAccess, wasCreated) =
            transaction {
                val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

                val pair =
                    ReliefAccessRepository.insertRequestInTransaction(
                        id = requestId,
                        branchDayId = branchDayId,
                        requestedBy = callerId,
                        targetUser = targetUserId,
                    )
                if (pair.second) {
                    ReliefAccessAudit.inserted(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        pair.first,
                    )
                }
                pair
            }

        logger.info {
            "[RELIEF-ACCESS-REQUEST] Request $requestId created " +
                "(relief=$callerId, target=$targetUserId, new=$wasCreated)"
        }

        return reliefAccess
    }
}

/**
 * Relief-access audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ReliefAccessAudit {
    fun inserted(
        context: AuditContext,
        reliefAccess: ReliefAccess,
    ) = AuditLogRepository.recordInsert(
        tableName = GrantReliefAccessTable.tableName,
        recordId = reliefAccess.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = GrantReliefAccessTable.auditFields(reliefAccess),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: ReliefAccess,
        after: ReliefAccess,
    ) = AuditLogRepository.recordUpdate(
        tableName = GrantReliefAccessTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = GrantReliefAccessTable::auditFields,
    )
}
