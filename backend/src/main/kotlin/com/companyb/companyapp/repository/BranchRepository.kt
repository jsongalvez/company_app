package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.BranchTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class BranchCreateResult(
    val branch: Branch,
    val created: Boolean,
)

object BranchRepository {
    private val INSERT_SQL =
        """
        WITH inserted AS (
            INSERT INTO branch (id, branch_type, name)
            VALUES (?::uuid, ?::branch_type, ?)
            ON CONFLICT (id) DO NOTHING
            RETURNING 1
        )
        SELECT EXISTS (SELECT 1 FROM inserted) AS inserted
        """.trimIndent()

    private val FIND_BY_ID_SQL =
        """
        SELECT id, branch_type::text AS branch_type, name
        FROM branch
        WHERE id = ?::uuid
        """.trimIndent()

    private val FIND_ALL_SQL =
        """
        SELECT id, branch_type::text AS branch_type, name
        FROM branch
        ORDER BY name ASC, id ASC
        """.trimIndent()

    fun create(
        id: UUID,
        name: String,
        branchType: BranchType,
        changedBy: UUID,
    ): BranchCreateResult =
        transaction {
            val inserted =
                exec(
                    INSERT_SQL,
                    args =
                        listOf(
                            TextColumnType() to id.toString(),
                            TextColumnType() to branchType.name,
                            TextColumnType() to name,
                        ),
                    explicitStatementType = StatementType.SELECT,
                ) { rs ->
                    check(rs.next()) { "Expected insert status row for branch $id" }
                    rs.getBoolean("inserted")
                } ?: false
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
            exec(FIND_ALL_SQL) { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toBranch())
                    }
                }
            } ?: emptyList()
        }.also { logger.info { "[FIND-BRANCHES] Fetched ${it.size} branch(es)" } }

    private fun findByIdInTransaction(id: UUID): Branch? =
        TransactionManager.current().exec(
            FIND_BY_ID_SQL,
            args = listOf(TextColumnType() to id.toString()),
        ) { rs -> if (rs.next()) rs.toBranch() else null }

    private fun ResultSet.toBranch(): Branch =
        Branch(
            id = UUID.fromString(getString("id")),
            branchType = BranchType.valueOf(getString("branch_type")),
            name = getString("name"),
        )
}
