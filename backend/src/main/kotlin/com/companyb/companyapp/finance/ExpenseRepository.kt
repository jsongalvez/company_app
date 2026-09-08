package com.companyb.companyapp.finance

import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class ExpenseCreateResult(
    val expense: Expense,
    val created: Boolean,
)

/**
 * Expense persistence store (#319, ADR-0024).
 *
 * Mutating functions (`*InTransaction`) are **in-transaction store operations**: they open no
 * transaction of their own and take no audit callback — they execute on the caller's
 * (command-owned) transaction, which the feature command also uses to insert the audit row
 * atomically. Calling them outside a transaction fails loudly (Exposed: no transaction in
 * context). Read helpers may still open their own convenient transaction wrappers.
 */
internal object ExpenseRepository {
    fun createInTransaction(params: ExpenseCreateParams): ExpenseCreateResult {
        val insertedCount =
            ExpenseTable
                .insertIgnore {
                    it[ExpenseTable.id] = params.id
                    it[ExpenseTable.branchDayId] = params.branchDayId
                    it[ExpenseTable.amount] = params.amount
                    it[ExpenseTable.category] = params.category
                    it[ExpenseTable.createdBy] = params.createdBy
                    if (params.notes != null) it[ExpenseTable.notes] = params.notes
                }.insertedCount

        val expense =
            findByIdInTransaction(params.id) ?: error("expense not found after insert for ${params.id}")
        if (expense.branchDayId != params.branchDayId) {
            throw NotFoundException("Expense not found for this branch day")
        }
        if (expense.createdBy != params.createdBy) {
            throw ConflictException("Expense id already belongs to another create request")
        }

        val created = insertedCount > 0
        val result = ExpenseCreateResult(expense, created)
        logger.info { "[CREATE-EXPENSE] Expense ${params.id.toString().maskUUID()} created=${result.created}" }
        return result
    }

    fun softDeleteInTransaction(
        expenseId: UUID,
        deletedBy: UUID,
        deletedReason: String,
    ): Expense {
        ExpenseTable.update({ ExpenseTable.id eq expenseId }) {
            it[ExpenseTable.deletedBy] = deletedBy
            it[ExpenseTable.deletedAt] =
                CurrentTimestampWithTimeZone
            it[ExpenseTable.deletedReason] = deletedReason
        }

        val result =
            findByIdInTransaction(expenseId)
                ?: error("expense not found after soft delete for $expenseId")
        logger.info { "[SOFT-DELETE-EXPENSE] Expense ${expenseId.toString().maskUUID()} deleted=true" }
        return result
    }

    /**
     * Restores a soft-deleted expense: clears `deleted_at`/`deleted_by`/`deleted_reason` in one
     * atomic statement scoped to `deletedAt IS NOT NULL` — a concurrent double-restore's second
     * UPDATE matches zero rows and the service maps the null to 400 (the #141 ownership-in-WHERE
     * class; #153 Q5). `version` is deliberately untouched (#153 Q6: restore changes no content).
     */
    fun restoreInTransaction(expenseId: UUID): Expense? {
        val updatedCount =
            ExpenseTable.update({ (ExpenseTable.id eq expenseId) and ExpenseTable.deletedAt.isNotNull() }) {
                it[ExpenseTable.deletedBy] = null
                it[ExpenseTable.deletedAt] = null
                it[ExpenseTable.deletedReason] = null
            }

        if (updatedCount == 0) {
            return null
        }

        val result =
            findByIdInTransaction(expenseId)
                ?: error("expense not found after restore for $expenseId")
        logger.info { "[RESTORE-EXPENSE] Expense ${expenseId.toString().maskUUID()} restored=true" }
        return result
    }

    // #153 Q1 — the GET includes soft-deleted rows (the Finance build's dimmed + reason rendering
    // needs the payload; the summary view already excludes them from totals). Deterministic
    // insertion order (createdAt ASC, id ASC tiebreak — #115 lesson).
    fun findByBranchDayId(branchDayId: UUID): List<Expense> =
        transaction {
            ExpenseTable
                .selectAll()
                .where { ExpenseTable.branchDayId eq branchDayId }
                .orderBy(
                    ExpenseTable.createdAt to SortOrder.ASC,
                    ExpenseTable.id to SortOrder.ASC,
                ).map { it.toExpense() }
        }.also { logger.info { "[FIND-EXPENSES] Found ${it.size} expenses for branch_day $branchDayId" } }

    fun updateInTransaction(
        expenseId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        expectedVersion: Int,
    ): Expense {
        val updatedCount =
            ExpenseTable.update({
                (ExpenseTable.id eq expenseId) and
                    (ExpenseTable.version eq expectedVersion) and
                    ExpenseTable.deletedAt.isNull()
            }) {
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = category
                if (notes != null) {
                    it[ExpenseTable.notes] = notes
                } else {
                    it[ExpenseTable.notes] = null
                }
                it[ExpenseTable.version] = expectedVersion + 1
            }

        if (updatedCount == 0) {
            throw VersionMismatchException(ExpenseTable.tableName, expenseId)
        }

        val result =
            findByIdInTransaction(expenseId)
                ?: error("expense not found after update for $expenseId")
        logger.info { "[UPDATE-EXPENSE] Expense ${expenseId.toString().maskUUID()} updated" }
        return result
    }

    fun findById(id: UUID): Expense? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-EXPENSE] Expense ${id.toString().maskUUID()} found=${it != null}" } }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Expense? =
        ExpenseTable
            .selectAll()
            .where { ExpenseTable.id eq id }
            .singleOrNull()
            ?.toExpense()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toExpense(): Expense =
        Expense(
            id = this[ExpenseTable.id],
            branchDayId = this[ExpenseTable.branchDayId],
            amount = this[ExpenseTable.amount],
            category = this[ExpenseTable.category],
            notes = this[ExpenseTable.notes],
            createdBy = this[ExpenseTable.createdBy],
            createdAt = this[ExpenseTable.createdAt],
            deletedBy = this[ExpenseTable.deletedBy],
            deletedAt = this[ExpenseTable.deletedAt],
            deletedReason = this[ExpenseTable.deletedReason],
            version = this[ExpenseTable.version],
        )
}
