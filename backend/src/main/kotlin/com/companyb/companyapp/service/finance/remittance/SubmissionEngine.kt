package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.RemittanceStatus
import java.util.UUID

internal object SubmissionEngine {
    @Suppress("ThrowsCount")
    fun submit(
        remittanceId: UUID,
        expectedVersion: Int,
        callerId: UUID,
        auditFn: (SubmitAuditContext) -> Unit = {},
    ): RemittanceSubmissionResult {
        val existing =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        if (existing.status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only submit DRAFT remittances")
        }

        if (existing.version != expectedVersion) {
            throw ConflictException("Remittance version mismatch")
        }

        return RemittanceRepository.submit(
            remittanceId = remittanceId,
            expectedVersion = expectedVersion,
            callerId = callerId,
            auditFn = auditFn,
        ) ?: throw NotFoundException("Remittance not found")
    }
}
