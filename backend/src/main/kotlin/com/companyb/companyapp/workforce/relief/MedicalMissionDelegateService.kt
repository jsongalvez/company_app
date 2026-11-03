package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.authorization.AuthorizationGrants
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Medical-mission-delegate feature commands (ADR-0024). Each mutating command owns
 * exactly one transaction: persistence runs via `MedicalMissionDelegateRepository.*InTransaction`
 * store operations and the audit row is inserted into the same transaction.
 */
object MedicalMissionDelegateService {
    private val logger = KotlinLogging.logger {}

    fun assignDelegate(
        delegateId: UUID,
        targetUserId: UUID,
        branchId: UUID,
        callerId: UUID,
        role: DelegateRole = DelegateRole.PRACTITIONER,
    ): MedicalMissionDelegate {
        requireTakesHelpers(branchId)
        // unknown targets 404 before the eligibility 400 (precedent):
        // isActiveStaffInTransaction alone conflates "unknown user" with "known but ineligible".
        if (!AccountReads.userExists(targetUserId)) {
            throw NotFoundException("User not found")
        }

        val delegate =
            transaction {
                assignDelegateInTransaction(
                    delegateId = delegateId,
                    targetUserId = targetUserId,
                    branchId = branchId,
                    callerId = callerId,
                    role = role,
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
        role: DelegateRole,
    ): MedicalMissionDelegate {
        val existingBeforeLock = MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
        if (existingBeforeLock != null) {
            requireRequestOwnership(existingBeforeLock, targetUserId, branchId, callerId)
            return existingBeforeLock
        }

        val activeStaff = AccountReads.isActiveStaffInTransaction(targetUserId)
        val existingAfterTargetLock = MedicalMissionDelegateRepository.findByIdInTransaction(delegateId)
        return if (existingAfterTargetLock != null) {
            requireRequestOwnership(existingAfterTargetLock, targetUserId, branchId, callerId)
            existingAfterTargetLock
        } else {
            if (!activeStaff) {
                throw ValidationException("A helper must be active staff.")
            }
            val alreadyDelegated =
                MedicalMissionDelegateRepository.findActiveByTargetAndBranchInTransaction(
                    targetUserId,
                    branchId,
                ) != null
            if (alreadyDelegated) {
                throw ConflictException("User already has an active medical mission delegate at this branch")
            }

            val result =
                MedicalMissionDelegateRepository.assignInTransaction(
                    delegateId = delegateId,
                    targetUserId = targetUserId,
                    assignedBy = callerId,
                    branchId = branchId,
                    role = role,
                )
            if (!result.inserted) {
                requireRequestOwnership(result.delegate, targetUserId, branchId, callerId)
            }
            if (result.inserted) {
                AuthorizationGrants.grantDelegateCapabilityInTransaction(
                    targetUserId = targetUserId,
                    branchId = branchId,
                    delegateId = delegateId,
                    role = role,
                )
                MedicalMissionDelegateAudit.inserted(AuditContext(callerId, branchId), result.delegate)
            }
            result.delegate
        }
    }

    private fun requireTakesHelpers(branchId: UUID) {
        if (BranchService.findById(branchId).branchType == BranchType.CLINIC) {
            throw ValidationException("Only a medical mission or provincial tour takes helpers.")
        }
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
        requireTakesHelpers(branchId)
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
 * Medical-mission-delegate audit vocabulary (ADR-0024 rule 3). Called by the command inside
 * its own transaction so the audit row commits atomically with the mutation. Owns the
 * persistence-table imports so the public command surface does not.
 */
internal object MedicalMissionDelegateAudit {
    fun inserted(
        context: AuditContext,
        delegate: MedicalMissionDelegate,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = context.changedBy, branchId = context.branchId),
        row = AuditedRow(MedicalMissionDelegateTable.tableName, delegate.id),
        fields = MedicalMissionDelegateTable.auditFields(delegate),
    )

    fun updated(
        context: AuditContext,
        before: MedicalMissionDelegate,
        after: MedicalMissionDelegate,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = context.changedBy, branchId = context.branchId),
        row = AuditedRow(MedicalMissionDelegateTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = MedicalMissionDelegateTable::auditFields,
    )
}
