package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchCreateResult
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.BranchCreateParams
import com.companyb.companyapp.repository.model.BranchTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
            }
            result
        }.also {
            logger.info { "[CREATE-BRANCH] Branch ${it.branch.id.toString().maskUUID()} created=${it.created}" }
        }

    fun findAll(): List<Branch> = BranchRepository.findAll()

    fun findById(branchId: UUID): Branch =
        BranchRepository.findById(branchId) ?: throw NotFoundException("Branch not found")
}

/**
 * Branch audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object BranchAudit {
    fun inserted(
        changedBy: UUID,
        branch: Branch,
    ) = AuditLogRepository.recordInsert(
        tableName = BranchTable.tableName,
        recordId = branch.id,
        changedBy = changedBy,
        fields = BranchTable.auditFields(branch),
    )
}
