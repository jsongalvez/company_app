package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.GrantWithCapabilityParams
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

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

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

        val validTo = BranchDayService.expirationUtc(branchDay.date)

        val result =
            checkNotNull(
                ReliefAccessRepository.grantWithCapability(
                    GrantWithCapabilityParams(
                        requestId = requestId,
                        grantedBy = callerId,
                        userId = request.requestedBy,
                        branchDayId = request.branchDayId,
                        sourceId = requestId,
                        validTo = validTo,
                        requestedBy = request.requestedBy,
                    ),
                    auditFn = { before, after ->
                        AuditLogRepository.recordUpdate(
                            tableName = GrantReliefAccessTable.tableName,
                            recordId = after.id,
                            before = before,
                            after = after,
                            changedBy = callerId,
                            branchId = branchDay.branchId,
                            isFlagged = isRemitted,
                            reason = reason,
                            auditFields = GrantReliefAccessTable::auditFields,
                        )
                    },
                ),
            ) { "Grant failed: relief access request not found in transaction" }

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

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

        val result =
            ReliefAccessRepository.deny(
                requestId,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = GrantReliefAccessTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        isFlagged = isRemitted,
                        reason = reason,
                        auditFields = GrantReliefAccessTable::auditFields,
                    )
                },
            )

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
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

        val targetHasClockIn = ReliefAccessRepository.hasActiveClockIn(targetUserId, branchDayId)
        if (!targetHasClockIn) {
            throw ValidationException("Target user does not have an active clock-in on this branch day")
        }

        val requesterIsRelief = ReliefAccessRepository.isReliefUser(callerId, branchDayId)
        if (!requesterIsRelief) {
            throw ForbiddenException("Only relief users can request relief access")
        }

        val (reliefAccess, wasCreated) =
            ReliefAccessRepository.insertRequest(
                requestId,
                branchDayId,
                callerId,
                targetUserId,
                auditFn = { created ->
                    AuditLogRepository.recordInsert(
                        tableName = GrantReliefAccessTable.tableName,
                        recordId = created.id,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        fields = GrantReliefAccessTable.auditFields(created),
                        isFlagged = isRemitted,
                        reason = reason,
                    )
                },
            )

        logger.info {
            "[RELIEF-ACCESS-REQUEST] Request $requestId created " +
                "(relief=$callerId, target=$targetUserId, new=$wasCreated)"
        }

        return reliefAccess
    }
}
