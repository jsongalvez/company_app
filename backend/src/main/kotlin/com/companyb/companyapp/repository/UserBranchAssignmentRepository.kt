package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

object UserBranchAssignmentRepository {
    private val INSERT_SQL =
        """
        WITH inserted AS (
            INSERT INTO user_branch_assignment (id, user_id, branch_id, slot, assigned_by)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?::uuid)
            ON CONFLICT (id) DO NOTHING
            RETURNING 1
        )
        SELECT EXISTS (SELECT 1 FROM inserted) AS inserted
        """.trimIndent()

    private val FIND_ACTIVE_BY_BRANCH_SQL =
        """
        SELECT id, user_id, branch_id, slot, assigned_by, assigned_at, ended_at
        FROM user_branch_assignment
        WHERE branch_id = ?::uuid
          AND ended_at IS NULL
        ORDER BY slot ASC, user_id ASC
        """.trimIndent()

    private val FIND_ACTIVE_BY_BRANCH_AND_USER_SQL =
        """
        SELECT id, user_id, branch_id, slot, assigned_by, assigned_at, ended_at
        FROM user_branch_assignment
        WHERE branch_id = ?::uuid
          AND user_id = ?::uuid
          AND ended_at IS NULL
        """.trimIndent()

    private val FIND_BY_ID_SQL =
        """
        SELECT id, user_id, branch_id, slot, assigned_by, assigned_at, ended_at
        FROM user_branch_assignment
        WHERE id = ?::uuid
        """.trimIndent()

    private val SET_ENDED_AT_SQL =
        """
        UPDATE user_branch_assignment
        SET ended_at = ?::timestamptz
        WHERE id = ?::uuid
        """.trimIndent()

    private val UPDATE_SLOT_SQL =
        """
        UPDATE user_branch_assignment
        SET slot = ?
        WHERE id = ?::uuid
          AND ended_at IS NULL
        """.trimIndent()

    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        userId: UUID,
        branchId: UUID,
        slot: Short,
        assignedBy: UUID,
    ): Boolean =
        transaction {
            exec(
                INSERT_SQL,
                args =
                    listOf(
                        TextColumnType() to id.toString(),
                        TextColumnType() to userId.toString(),
                        TextColumnType() to branchId.toString(),
                        TextColumnType() to slot.toString(),
                        TextColumnType() to assignedBy.toString(),
                    ),
                explicitStatementType = StatementType.SELECT,
            ) { rs ->
                check(rs.next()) { "Expected insert status for assignment $id" }
                rs.getBoolean("inserted")
            } ?: false
        }.also { created ->
            logger.info { "[CREATE-ASSIGNMENT] Assignment $id created=$created" }
        }

    fun findActiveByBranch(branchId: UUID): List<UserBranchAssignment> =
        transaction {
            exec(FIND_ACTIVE_BY_BRANCH_SQL, args = listOf(TextColumnType() to branchId.toString())) { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toAssignment())
                    }
                }
            } ?: emptyList()
        }.also { logger.info { "[FIND-ASSIGNMENTS-BY-BRANCH] Fetched ${it.size} active assignments" } }

    fun findActiveByBranchAndUser(
        branchId: UUID,
        userId: UUID,
    ): UserBranchAssignment? =
        transaction {
            exec(
                FIND_ACTIVE_BY_BRANCH_AND_USER_SQL,
                args =
                    listOf(
                        TextColumnType() to branchId.toString(),
                        TextColumnType() to userId.toString(),
                    ),
            ) { rs ->
                if (rs.next()) rs.toAssignment() else null
            }
        }

    fun findById(id: UUID): UserBranchAssignment? =
        transaction {
            exec(FIND_BY_ID_SQL, args = listOf(TextColumnType() to id.toString())) { rs ->
                if (rs.next()) rs.toAssignment() else null
            }
        }.also { logger.info { "[FIND-ASSIGNMENT-BY-ID] id=${id.toString().maskUUID()} found=${it != null}" } }

    fun setEndedAt(
        id: UUID,
        endedAt: OffsetDateTime,
    ) {
        transaction {
            exec(
                SET_ENDED_AT_SQL,
                args =
                    listOf(
                        TextColumnType() to endedAt.toString(),
                        TextColumnType() to id.toString(),
                    ),
            )
        }
        logger.info { "[SET-ENDED-AT] Assignment ${id.toString().maskUUID()}" }
    }

    fun updateSlot(
        id: UUID,
        slot: Short,
    ) {
        transaction {
            exec(
                UPDATE_SLOT_SQL,
                args =
                    listOf(
                        TextColumnType() to slot.toString(),
                        TextColumnType() to id.toString(),
                    ),
            )
        }
        logger.info { "[UPDATE-SLOT] Assignment ${id.toString().maskUUID()} slot=$slot" }
    }

    fun swapSlotsInTransaction(
        idA: UUID,
        slotA: Short,
        idB: UUID,
        slotB: Short,
    ) {
        val swapSql =
            """
            UPDATE user_branch_assignment
            SET slot = CASE id
                WHEN ?::uuid THEN ?
                WHEN ?::uuid THEN ?
            END
            WHERE id IN (?::uuid, ?::uuid) AND ended_at IS NULL
            """.trimIndent()
        TransactionManager.current().exec(
            swapSql,
            args =
                listOf(
                    TextColumnType() to idA.toString(),
                    TextColumnType() to slotB.toString(),
                    TextColumnType() to idB.toString(),
                    TextColumnType() to slotA.toString(),
                    TextColumnType() to idA.toString(),
                    TextColumnType() to idB.toString(),
                ),
        )
    }

    private fun java.sql.ResultSet.toAssignment(): UserBranchAssignment =
        UserBranchAssignment(
            id = UUID.fromString(getString("id")),
            userId = UUID.fromString(getString("user_id")),
            branchId = UUID.fromString(getString("branch_id")),
            slot = getShort("slot"),
            assignedBy = UUID.fromString(getString("assigned_by")),
            assignedAt = getObject("assigned_at", OffsetDateTime::class.java),
            endedAt = getObject("ended_at", OffsetDateTime::class.java),
        )
}
