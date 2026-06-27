package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.DayStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

object BranchDayRepository {
    // Idempotent UPSERT: insert the day if absent, otherwise leave the existing row untouched.
    // ON CONFLICT DO NOTHING returns no row, so the row is always read back with a follow-up
    // SELECT inside the same transaction.
    private val UPSERT_SQL =
        """
        INSERT INTO branch_day (branch_id, date)
        VALUES (?::uuid, ?::date)
        ON CONFLICT (branch_id, date) DO NOTHING
        """.trimIndent()

    private val SELECT_BY_BRANCH_DATE_SQL =
        """
        SELECT id, branch_id, date, status
        FROM branch_day
        WHERE branch_id = ?::uuid AND date = ?::date
        """.trimIndent()

    private val SELECT_BY_ID_SQL =
        """
        SELECT id, branch_id, date, status
        FROM branch_day
        WHERE id = ?::uuid
        """.trimIndent()

    fun resolveOrCreate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay =
        transaction {
            exec(
                UPSERT_SQL,
                args =
                    listOf(
                        TextColumnType() to branchId.toString(),
                        TextColumnType() to date.toString(),
                    ),
            )
            exec(
                SELECT_BY_BRANCH_DATE_SQL,
                args =
                    listOf(
                        TextColumnType() to branchId.toString(),
                        TextColumnType() to date.toString(),
                    ),
            ) { rs -> if (rs.next()) rs.toBranchDay() else null }
                ?: error("branch_day row not found after upsert for branch=$branchId date=$date")
        }.also { logger.info { "[RESOLVE-OR-CREATE] Resolved branch_day ${it.id} (status=${it.status})" } }

    fun findById(branchDayId: UUID): BranchDay? =
        transaction {
            exec(
                SELECT_BY_ID_SQL,
                args = listOf(TextColumnType() to branchDayId.toString()),
            ) { rs -> if (rs.next()) rs.toBranchDay() else null }
        }

    private fun java.sql.ResultSet.toBranchDay(): BranchDay =
        BranchDay(
            id = UUID.fromString(getString("id")),
            branchId = UUID.fromString(getString("branch_id")),
            date = getDate("date").toLocalDate(),
            status = DayStatus.valueOf(getString("status")),
        )
}
