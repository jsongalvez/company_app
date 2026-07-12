package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import java.util.UUID

object ReliefAccessService {
    private val logger = KotlinLogging.logger {}

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
