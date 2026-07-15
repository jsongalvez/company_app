package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.ReliefStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.NotFoundResponse
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
                ?: throw NotFoundResponse("Relief access request not found")

        if (request.requestStatus == ReliefStatus.GRANTED) {
            return request
        }

        if (callerId != request.targetUser) {
            throw ForbiddenResponse("Only the target user can grant this request")
        }

        val existingGrant =
            ReliefAccessRepository.findByRequestedByAndBranchDayId(
                request.requestedBy,
                request.branchDayId,
                ReliefStatus.GRANTED,
            )
        if (existingGrant != null) {
            return existingGrant
        }

        val capabilityId =
            CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA)
                ?: throw InternalServerErrorResponse("EDIT_BRANCH_DATA capability not found")

        val branchDay =
            BranchDayRepository.findById(request.branchDayId)
                ?: throw NotFoundResponse("Branch day not found")
        val validTo = BranchDayService.expirationUtc(branchDay.date)

        ReliefAccessRepository.grantWithCapability(
            requestId = requestId,
            grantedBy = callerId,
            userId = request.requestedBy,
            capabilityId = capabilityId,
            branchDayId = request.branchDayId,
            sourceId = requestId,
            validTo = validTo,
            priority = GrantPriorities.RELIEF_ACCESS,
        )

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "requestId" to requestId.toString(),
                "branchDayId" to request.branchDayId.toString(),
                "grantedBy" to callerId.toString(),
                "requestedBy" to request.requestedBy.toString(),
            )
        AuditLogRepository.record(
            tableName = GrantReliefAccessTable.tableName,
            recordId = requestId,
            action = AuditAction.UPDATE,
            changedBy = callerId,
            newValue = auditNewValue,
        )

        logger.info { "[RELIEF-ACCESS-GRANT] Request $requestId granted by $callerId" }

        return ReliefAccessRepository.findById(requestId)!!
    }

    @Suppress("ThrowsCount")
    fun denyAccess(
        requestId: UUID,
        callerId: UUID,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundResponse("Relief access request not found")

        if (request.requestStatus == ReliefStatus.DENIED) {
            return request
        }

        if (callerId != request.targetUser) {
            throw ForbiddenResponse("Only the target user can deny this request")
        }

        if (request.requestStatus == ReliefStatus.GRANTED) {
            throw BadRequestResponse("Cannot deny a request that has already been granted")
        }

        ReliefAccessRepository.deny(requestId)

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "requestId" to requestId.toString(),
                "status" to "DENIED",
            )
        AuditLogRepository.record(
            tableName = GrantReliefAccessTable.tableName,
            recordId = requestId,
            action = AuditAction.UPDATE,
            changedBy = callerId,
            newValue = auditNewValue,
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
            throw BadRequestResponse("Target user does not have an active clock-in on this branch day")
        }

        val requesterIsRelief = ReliefAccessRepository.isReliefUser(callerId, branchDayId)
        if (!requesterIsRelief) {
            throw ForbiddenResponse("Only relief users can request relief access")
        }

        val (reliefAccess, wasCreated) =
            ReliefAccessRepository.insertRequest(requestId, branchDayId, callerId, targetUserId)

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "requestId" to requestId.toString(),
                "branchDayId" to branchDayId.toString(),
                "targetUserId" to targetUserId.toString(),
            )
        AuditLogRepository.record(
            tableName = GrantReliefAccessTable.tableName,
            recordId = requestId,
            action = AuditAction.INSERT,
            changedBy = callerId,
            newValue = auditNewValue,
        )

        logger.info {
            "[RELIEF-ACCESS-REQUEST] Request $requestId created " +
                "(relief=$callerId, target=$targetUserId, new=$wasCreated)"
        }

        return reliefAccess
    }
}
