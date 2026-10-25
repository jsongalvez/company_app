package com.companyb.companyapp.branch

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class BranchCreateResult(
    val branch: Branch,
    val created: Boolean,
)

internal object BranchRepository {
    /** In-transaction store operation (ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(params: BranchCreateParams): BranchCreateResult {
        val insertedCount =
            BranchTable
                .insertIgnore {
                    it[BranchTable.id] = params.id
                    it[BranchTable.branchType] = params.branchType
                    it[BranchTable.name] = params.name
                }.insertedCount
        val branch =
            findByIdInTransaction(params.id)
                ?: run {
                    val duplicate =
                        BranchTable
                            .selectAll()
                            .where {
                                (BranchTable.branchType eq params.branchType) and
                                    (BranchTable.name eq params.name)
                            }.singleOrNull()
                    if (duplicate != null) {
                        throw ConflictException("A branch with this name and type already exists")
                    }
                    error("branch row not found after idempotent insert for ${params.id}")
                }
        return BranchCreateResult(branch, created = insertedCount > 0)
    }

    /** In-transaction rename; returns the old row, or null when the branch is unknown. */
    fun renameInTransaction(
        id: UUID,
        name: String,
    ): Branch? {
        val old = findByIdInTransaction(id) ?: return null
        val clash =
            BranchTable
                .selectAll()
                .where {
                    (BranchTable.branchType eq old.branchType) and
                        (BranchTable.name eq name) and
                        (BranchTable.id neq id)
                }.any()
        if (clash) throw ConflictException("A branch with this name and type already exists")
        BranchTable.update({ BranchTable.id eq id }) { it[BranchTable.name] = name }
        return old
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

    private fun findByIdInTransaction(id: UUID): Branch? =
        BranchTable
            .selectAll()
            .where { BranchTable.id eq id }
            .singleOrNull()
            ?.let { it.toBranch() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBranch(): Branch =
        Branch(
            id = this[BranchTable.id],
            branchType = this[BranchTable.branchType],
            name = this[BranchTable.name],
        )
}
