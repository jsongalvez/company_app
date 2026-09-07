package com.companyb.companyapp.branchday

import com.companyb.companyapp.contracts.branchday.DayStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
            findByIdInTransaction(branchDayId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(branchDayId: UUID): BranchDay? =
        BranchDayTable
            .selectAll()
            .where { BranchDayTable.id eq branchDayId }
            .singleOrNull()
            ?.toBranchDay()

    /** In-transaction status write — runs on the caller's open transaction (ADR-0024). */
    fun updateStatusInTransaction(
        branchDayId: UUID,
        status: DayStatus,
    ) {
        BranchDayTable.update({ BranchDayTable.id eq branchDayId }) {
            it[BranchDayTable.status] = status
        }
    }

    fun acquireLock(branchDayId: UUID) {
        transaction {
            acquireLockInTransaction(branchDayId)
        }
    }

    /** Locks and reads a branch day on the caller's open transaction. */
    fun acquireLockInTransaction(branchDayId: UUID): BranchDay? =
        BranchDayTable
            .selectAll()
            .where { BranchDayTable.id eq branchDayId }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toBranchDay()

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

    /**
     * Branch-scoped find by id — the parent-child convention (#157 session-create guard):
     * a day that exists but belongs to a different branch is indistinguishable from a
     * missing one (null), so callers fail closed with a 404.
     */
    fun findByIdForBranch(
        branchDayId: UUID,
        branchId: UUID,
    ): BranchDay? =
        transaction {
            BranchDayTable
                .selectAll()
                .where { (BranchDayTable.id eq branchDayId) and (BranchDayTable.branchId eq branchId) }
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
