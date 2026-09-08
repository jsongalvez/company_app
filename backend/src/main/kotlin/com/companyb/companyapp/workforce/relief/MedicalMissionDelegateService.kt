package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.authorization.AuthorizationGrants
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
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
        val branch = BranchService.findById(branchId)
        if (branch.branchType != BranchType.MEDICAL_MISSION) {
            throw ValidationException("Medical mission delegate requires a medical mission Branch")
        }

        val delegate =
            transaction {
                assignDelegateInTransaction(
                    delegateId = delegateId,
                    targetUserId = targetUserId,
                    branchId = branchId,
                    callerId = callerId,
                )
            }

        logger.info { "[DELEGATE-ASSIGN] Delegate $delegateId: user=$targetUserId, branch=$branchId" }

        return delegate
    }

    private fun assignDelegateInTransaction(
        delegateId: UUID,
        targetUserId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate {
        val existing = MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
        if (existing != null) {
            requireRequestOwnership(existing, targetUserId, branchId, callerId)
            return existing
        }

        val activeManager = AccountReads.isActiveManagerInTransaction(targetUserId)
        val existingAfterTargetLock = MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
        if (existingAfterTargetLock != null) {
            requireRequestOwnership(existingAfterTargetLock, targetUserId, branchId, callerId)
            return existingAfterTargetLock
        }
        if (!activeManager) {
            throw ValidationException("Delegate target must be an active MANAGER")
        }
        if (MedicalMissionDelegateRepository.findActiveByTargetAndBranchInTransaction(targetUserId, branchId) != null) {
            throw ConflictException("User already has an active medical mission delegate at this branch")
        }

        val result =
            MedicalMissionDelegateRepository.assignInTransaction(
                delegateId = delegateId,
                targetUserId = targetUserId,
                assignedBy = callerId,
                branchId = branchId,
            )
        if (!result.inserted) {
            requireRequestOwnership(result.delegate, targetUserId, branchId, callerId)
        }
        if (result.inserted) {
            AuthorizationGrants.grantDelegateCapabilityInTransaction(
                targetUserId = targetUserId,
                branchId = branchId,
                delegateId = delegateId,
            )
            MedicalMissionDelegateAudit.inserted(AuditContext(callerId, branchId), result.delegate)
        }
        return result.delegate
    }

    private fun requireRequestOwnership(
        delegate: MedicalMissionDelegate,
        targetUserId: UUID,
        branchId: UUID,
        callerId: UUID,
    ) {
        if (delegate.targetUser != targetUserId ||
            delegate.branchId != branchId ||
            delegate.assignedBy != callerId
        ) {
            throw ConflictException("Delegate id already belongs to another request")
        }
    }

    fun listDelegates(branchId: UUID): List<MedicalMissionDelegate> {
        val branch = BranchService.findById(branchId)
        if (branch.branchType != BranchType.MEDICAL_MISSION) {
            throw ValidationException("Medical mission delegate requires a medical mission Branch")
        }
        return MedicalMissionDelegateRepository.findByBranchId(branchId)
    }

    fun revokeDelegate(
        delegateId: UUID,
        callerId: UUID,
    ): MedicalMissionDelegate =
        transaction {
            val before =
                MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
                    ?: throw NotFoundException("Medical mission delegate not found")

            val result = MedicalMissionDelegateRepository.revokeInTransaction(delegateId)

            if (result.changed) {
                AuthorizationGrants.closeDelegateCapabilityInTransaction(delegateId)
                MedicalMissionDelegateAudit.updated(AuditContext(callerId, before.branchId), before, result.delegate)
            }
            result.delegate
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
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordUpdate(
        tableName = MedicalMissionDelegateTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = MedicalMissionDelegateTable::auditFields,
    )
}
