package com.companyb.companyapp.branch

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.session.SessionBaseRateService
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

object BranchService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        id: UUID,
        name: String,
        branchType: BranchType,
    ): BranchCreateResult =
        transaction {
            val result =
                BranchRepository.createInTransaction(
                    BranchCreateParams(
                        id = id,
                        name = name,
                        branchType = branchType,
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                BranchAudit.inserted(callerId, result.branch)
                // provision the five BR-documented default base rates in the same
                // command transaction so a fresh branch never fails session create with
                // "No base rate configured" (explicit session-rate hook, same tx).
                SessionBaseRateService.provisionDefaultsInTransaction(result.branch.id, callerId)
            }
            result
        }.also {
            logger.info { "[CREATE-BRANCH] Branch ${it.branch.id.toString().maskUUID()} created=${it.created}" }
        }

    fun rename(
        callerId: UUID,
        id: UUID,
        name: String,
    ): Branch =
        transaction {
            val old = BranchRepository.renameInTransaction(id, name) ?: throw NotFoundException("Branch not found")
            val renamed = old.copy(name = name)
            if (old.name != name) BranchAudit.renamed(callerId, old, renamed)
            renamed
        }.also { logger.info { "[RENAME-BRANCH] Branch ${id.toString().maskUUID()} renamed" } }

    /** 503bf228 — the branch's commission for all its products; null = each product's shared one. Audited. */
    fun setCommission(
        callerId: UUID,
        id: UUID,
        commissionAmount: BigDecimal?,
    ): Branch =
        transaction {
            commissionAmount?.let { validateMoneyAmount(it, "commissionAmount") }
            val old =
                BranchRepository.setCommissionInTransaction(id, commissionAmount)
                    ?: throw NotFoundException("Branch not found")
            val updated = old.copy(commissionAmount = commissionAmount)
            val same =
                if (old.commissionAmount == null || commissionAmount == null) {
                    old.commissionAmount == commissionAmount
                } else {
                    old.commissionAmount.compareTo(commissionAmount) == 0
                }
            if (!same) BranchAudit.renamed(callerId, old, updated)
            updated
        }.also { logger.info { "[SET-BRANCH-COMMISSION] Branch ${id.toString().maskUUID()}" } }

    fun findAll(): List<Branch> = BranchRepository.findAll()

    fun findById(branchId: UUID): Branch =
        BranchRepository.findById(branchId) ?: throw NotFoundException("Branch not found")

    /** Nullable directory read for callers that fall back (display names, optional links). */
    fun findByIdOrNull(branchId: UUID): Branch? = BranchRepository.findById(branchId)
}

/**
 * Branch audit vocabulary (ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object BranchAudit {
    fun inserted(
        changedBy: UUID,
        branch: Branch,
    ) = AuditLog.recordInsert(
        tableName = BranchTable.tableName,
        recordId = branch.id,
        changedBy = changedBy,
        fields = BranchTable.auditFields(branch),
    )

    /** Any UPDATE of the branch row (rename, commission). */
    fun renamed(
        changedBy: UUID,
        old: Branch,
        new: Branch,
    ) = AuditLog.recordUpdate(
        tableName = BranchTable.tableName,
        recordId = new.id,
        oldFields = BranchTable.auditFields(old),
        newFields = BranchTable.auditFields(new),
        changedBy = changedBy,
    )
}
