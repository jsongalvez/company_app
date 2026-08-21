package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.MedicalMissionDelegateRepository
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Medical-mission-delegate feature commands (#323, ADR-0024). Each mutating command owns
 * exactly one transaction: persistence runs via `MedicalMissionDelegateRepository.*InTransaction`
 * store operations and the audit row is inserted into the same transaction.
 */
object MedicalMissionDelegateService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun assignDelegate(
        delegateId: UUID,
        targetUserId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate {
        val branch = BranchRepository.findById(branchId) ?: throw NotFoundException("Branch not found")
        if (branch.branchType != BranchType.MEDICAL_MISSION) {
            throw ValidationException("Medical mission delegate requires a medical mission Branch")
        }

        val capabilityId =
            checkNotNull(
                CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
            ) { "EDIT_BRANCH_DATA capability not found" }

        val delegate =
            transaction {
                val result =
                    MedicalMissionDelegateRepository.assignInTransaction(
                        delegateId = delegateId,
                        targetUserId = targetUserId,
                        assignedBy = callerId,
                        branchId = branchId,
                        capabilityId = capabilityId,
                    )
                if (result.inserted) {
                    MedicalMissionDelegateAudit.inserted(AuditContext(callerId, branchId), result.delegate)
                }
                result.delegate
            }

        logger.info { "[DELEGATE-ASSIGN] Delegate $delegateId: user=$targetUserId, branch=$branchId" }

        return delegate
    }

    fun revokeDelegate(
        delegateId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate =
        transaction {
            val before =
                MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
                    ?: throw NotFoundException("Medical mission delegate not found")

            val revoked = MedicalMissionDelegateRepository.revokeInTransaction(delegateId)

            MedicalMissionDelegateAudit.updated(AuditContext(callerId, before.branchId), before, revoked)
            revoked
        }.also { logger.info { "[DELEGATE-REVOKE] Delegate $delegateId revoked by $callerId" } }
}

/**
 * Medical-mission-delegate audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside
 * its own transaction so the audit row commits atomically with the mutation. Owns the
 * persistence-table imports so the public command surface does not.
 */
internal object MedicalMissionDelegateAudit {
    fun inserted(
        context: AuditContext,
        delegate: MedicalMissionDelegate,
    ) = AuditLogRepository.recordInsert(
        tableName = MedicalMissionDelegateTable.tableName,
        recordId = delegate.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = MedicalMissionDelegateTable.auditFields(delegate),
    )

    fun updated(
        context: AuditContext,
        before: MedicalMissionDelegate,
        after: MedicalMissionDelegate,
    ) = AuditLogRepository.recordUpdate(
        tableName = MedicalMissionDelegateTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = MedicalMissionDelegateTable::auditFields,
    )
}
