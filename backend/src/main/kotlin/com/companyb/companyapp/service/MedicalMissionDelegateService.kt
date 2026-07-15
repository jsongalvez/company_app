package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.MedicalMissionDelegateRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object MedicalMissionDelegateService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun assignDelegate(
        delegateId: UUID,
        targetUserId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.ASSIGN_DELEGATE,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        val capabilityId =
            CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA)
                ?: throw InternalServerErrorResponse("EDIT_BRANCH_DATA capability not found")

        MedicalMissionDelegateRepository.assignWithCapability(
            delegateId = delegateId,
            targetUserId = targetUserId,
            assignedBy = callerId,
            branchId = branchId,
            capabilityId = capabilityId,
        )

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "delegateId" to delegateId.toString(),
                "targetUserId" to targetUserId.toString(),
                "branchId" to branchId.toString(),
                "assignedBy" to callerId.toString(),
            )
        AuditLogRepository.record(
            tableName = MedicalMissionDelegateTable.tableName,
            recordId = delegateId,
            action = AuditAction.INSERT,
            changedBy = callerId,
            newValue = auditNewValue,
        )

        logger.info { "[DELEGATE-ASSIGN] Delegate $delegateId: user=$targetUserId, branch=$branchId" }

        return MedicalMissionDelegateRepository.findById(delegateId)
            ?: throw InternalServerErrorResponse("Failed to read back delegate")
    }

    @Suppress("ThrowsCount")
    fun revokeDelegate(
        delegateId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate {
        if (MedicalMissionDelegateRepository.findById(delegateId) == null) {
            throw NotFoundResponse("Medical mission delegate not found")
        }

        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.ASSIGN_DELEGATE,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        MedicalMissionDelegateRepository.revokeWithCapability(delegateId)

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "delegateId" to delegateId.toString(),
                "endedAt" to "now",
            )
        AuditLogRepository.record(
            tableName = MedicalMissionDelegateTable.tableName,
            recordId = delegateId,
            action = AuditAction.UPDATE,
            changedBy = callerId,
            newValue = auditNewValue,
        )

        logger.info { "[DELEGATE-REVOKE] Delegate $delegateId revoked by $callerId" }

        return MedicalMissionDelegateRepository.findById(delegateId)
            ?: throw InternalServerErrorResponse("Failed to read back delegate after revoke")
    }
}
