package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.BranchCreateParams
import com.companyb.companyapp.repository.model.BranchTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class BranchCreateResult(
    val branch: Branch,
    val created: Boolean,
)

object BranchRepository {
    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
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
                ?: error("branch row not found after idempotent insert for ${params.id}")
        return BranchCreateResult(branch, created = insertedCount > 0)
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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBranch(): Branch =
        Branch(
            id = this[BranchTable.id],
            branchType = this[BranchTable.branchType],
            name = this[BranchTable.name],
        )
}
