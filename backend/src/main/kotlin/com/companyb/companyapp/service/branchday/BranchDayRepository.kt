package com.companyb.companyapp.service.branchday

import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.BranchDayTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

internal object BranchDayRepository {
    private val logger = KotlinLogging.logger {}

    fun resolveOrCreate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay =
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
            }
            BranchDayTable
                .selectAll()
                .where { (BranchDayTable.branchId eq branchId) and (BranchDayTable.date eq date) }
                .single()
                .toBranchDay()
        }.also { logger.info { "[RESOLVE-OR-CREATE] Resolved branch_day ${it.id} (status=${it.status})" } }

    fun findById(branchDayId: UUID): BranchDay? =
        transaction {
            BranchDayTable
                .selectAll()
                .where { BranchDayTable.id eq branchDayId }
                .singleOrNull()
                ?.toBranchDay()
        }

    /**
     * Find-only branch-day lookup by (branch, date) — never creates. Used by gates that must
     * resolve a day without mutating (the #157 session-create day-scoped gate: a 403'd
     * attempt must not leave a day row behind).
     */
    fun findByBranchAndDate(
        branchId: UUID,
        date: LocalDate,
    ): BranchDay? =
        transaction {
            BranchDayTable
                .selectAll()
                .where { (BranchDayTable.branchId eq branchId) and (BranchDayTable.date eq date) }
                .singleOrNull()
                ?.toBranchDay()
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBranchDay(): BranchDay =
        BranchDay(
            id = this[BranchDayTable.id],
            branchId = this[BranchDayTable.branchId],
            date = this[BranchDayTable.date],
            status = this[BranchDayTable.status],
        )
}
