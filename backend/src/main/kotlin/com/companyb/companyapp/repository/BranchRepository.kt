package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.BranchTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class BranchCreateResult(
    val branch: Branch,
    val created: Boolean,
)

object BranchRepository {
    fun create(
        id: UUID,
        name: String,
        branchType: BranchType,
        changedBy: UUID,
    ): BranchCreateResult =
        transaction {
            val insertedCount =
                BranchTable
                    .insertIgnore {
                        it[BranchTable.id] = id
                        it[BranchTable.branchType] = branchType
                        it[BranchTable.name] = name
                    }.insertedCount
            val inserted = insertedCount > 0
            val branch = findByIdInTransaction(id) ?: error("branch row not found after idempotent insert for $id")

            if (inserted) {
                AuditLogRepository.record(
                    tableName = BranchTable.tableName,
                    recordId = branch.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to branch.id.toString(),
                            "branchType" to branch.branchType.name,
                            "name" to branch.name,
                        ),
                )
                BranchCreateResult(branch, created = true)
            } else {
                BranchCreateResult(
                    branch = branch,
                    created = false,
                )
            }
        }.also {
            logger.info {
                "[CREATE-BRANCH] Branch ${it.branch.id.toString().maskUUID()} created=${it.created}"
            }
        }

    fun findById(id: UUID): Branch? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-BRANCH] Branch ${id.toString().maskUUID()} found=${it != null}" } }

    fun findAll(): List<Branch> =
        transaction {
            BranchTable
                .selectAll()
                .orderBy(BranchTable.name to SortOrder.ASC, BranchTable.id to SortOrder.ASC)
                .map { it.toBranch() }
        }.also { logger.info { "[FIND-BRANCHES] Fetched ${it.size} branch(es)" } }

    fun findByType(branchType: BranchType): List<Branch> =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.branchType eq branchType }
                .orderBy(BranchTable.name to SortOrder.ASC, BranchTable.id to SortOrder.ASC)
                .map { it.toBranch() }
        }.also { logger.info { "[FIND-BRANCHES-BY-TYPE] Fetched ${it.size} branch(es) of type $branchType" } }

    private fun findByIdInTransaction(id: UUID): Branch? =
        BranchTable
            .selectAll()
            .where { BranchTable.id eq id }
            .singleOrNull()
            ?.let { it.toBranch() }

    private fun org.jetbrains.exposed.sql.ResultRow.toBranch(): Branch =
        Branch(
            id = this[BranchTable.id],
            branchType = this[BranchTable.branchType],
            name = this[BranchTable.name],
        )
}
