package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.MedicalMissionDelegateRepository
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import io.github.oshai.kotlinlogging.KotlinLogging
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
        val capabilityId =
            checkNotNull(
                CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
            ) { "EDIT_BRANCH_DATA capability not found" }

        MedicalMissionDelegateRepository.assignWithCapability(
            delegateId = delegateId,
            targetUserId = targetUserId,
            assignedBy = callerId,
            branchId = branchId,
            capabilityId = capabilityId,
        )

        logger.info { "[DELEGATE-ASSIGN] Delegate $delegateId: user=$targetUserId, branch=$branchId" }

        return checkNotNull(
            MedicalMissionDelegateRepository.findById(delegateId),
        ) { "Failed to read back delegate" }
    }

    @Suppress("ThrowsCount")
    fun revokeDelegate(
        delegateId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate {
        if (MedicalMissionDelegateRepository.findById(delegateId) == null) {
            throw NotFoundException("Medical mission delegate not found")
        }

        MedicalMissionDelegateRepository.revokeWithCapability(delegateId, callerId)

        logger.info { "[DELEGATE-REVOKE] Delegate $delegateId revoked by $callerId" }

        return checkNotNull(
            MedicalMissionDelegateRepository.findById(delegateId),
        ) { "Failed to read back delegate after revoke" }
    }
}
