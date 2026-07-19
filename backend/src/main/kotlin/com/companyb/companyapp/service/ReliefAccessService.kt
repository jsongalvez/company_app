package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.GrantWithCapabilityParams
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.ReliefStatus
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object ReliefAccessService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "ReturnCount")
    fun grantAccess(
        requestId: UUID,
        callerId: UUID,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (request.requestStatus == ReliefStatus.GRANTED) {
            return request
        }

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can grant this request")
        }

        val capabilityId =
            checkNotNull(
                CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
            ) { "EDIT_BRANCH_DATA capability not found" }

        val branchDay =
            BranchDayService.requireBranchDayExists(request.branchDayId)
        val validTo = BranchDayService.expirationUtc(branchDay.date)

        val result =
            checkNotNull(
                ReliefAccessRepository.grantWithCapability(
                    GrantWithCapabilityParams(
                        requestId = requestId,
                        grantedBy = callerId,
                        userId = request.requestedBy,
                        capabilityId = capabilityId,
                        branchDayId = request.branchDayId,
                        sourceId = requestId,
                        validTo = validTo,
                        priority = GrantPriorities.RELIEF_ACCESS,
                        requestedBy = request.requestedBy,
                    ),
                    auditFn = { updated ->
                        AuditLogRepository.record(
                            tableName = GrantReliefAccessTable.tableName,
                            recordId = updated.id,
                            action = AuditAction.UPDATE,
                            changedBy = callerId,
                            newValue =
                                AuditLogRepository.jsonFields(
                                    "requestId" to updated.id.toString(),
                                    "branchDayId" to updated.branchDayId.toString(),
                                    "grantedBy" to callerId.toString(),
                                    "requestedBy" to updated.requestedBy.toString(),
                                ),
                        )
                    },
                ),
            ) { "Grant failed: relief access request not found in transaction" }

        if (result.id == requestId) {
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
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (request.requestStatus == ReliefStatus.DENIED) {
            return request
        }

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can deny this request")
        }

        if (request.requestStatus == ReliefStatus.GRANTED) {
            throw ValidationException("Cannot deny a request that has already been granted")
        }

        ReliefAccessRepository.deny(
            requestId,
            auditFn = { updated ->
                AuditLogRepository.record(
                    tableName = GrantReliefAccessTable.tableName,
                    recordId = updated.id,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "requestId" to updated.id.toString(),
                            "status" to ReliefStatus.DENIED.name,
                        ),
                )
            },
        )

        logger.info { "[RELIEF-ACCESS-DENY] Request $requestId denied by $callerId" }

        return ReliefAccessRepository.findById(requestId)!!
    }

    @Suppress("ThrowsCount")
    fun requestReliefAccess(
        requestId: UUID,
        branchDayId: UUID,
        targetUserId: UUID,
        callerId: UUID,
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
                        fields = GrantReliefAccessTable.auditFields(created),
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
