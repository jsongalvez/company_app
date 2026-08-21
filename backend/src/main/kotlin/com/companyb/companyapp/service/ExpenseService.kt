package com.companyb.companyapp.service

import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ExpenseRepository
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCreateParams
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Expense feature commands (#319, ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `ExpenseRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 */
object ExpenseService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        reason: String? = null,
    ): Expense {
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

        return transaction {
            val result =
                ExpenseRepository.createInTransaction(
                    ExpenseCreateParams(
                        id = id,
                        branchDayId = branchDayId,
                        amount = amount,
                        category = category,
                        createdBy = callerId,
                        notes = notes,
                    ),
                )
            if (result.created) {
                AuditLogRepository.recordInsert(
                    tableName = ExpenseTable.tableName,
                    recordId = result.expense.id,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    fields = ExpenseTable.auditFields(result.expense),
                    isFlagged = isRemitted,
                    reason = reason,
                )
            }
            result.expense
        }
    }

    @Suppress("ThrowsCount", "LongParameterList")
    fun update(
        callerId: UUID,
        expenseId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        expectedVersion: Int,
        reason: String? = null,
    ): Expense =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction,
            // never held across a method boundary where a concurrent write could stale it.
            val before =
                ExpenseRepository.findByIdInTransaction(expenseId)
                    ?: throw NotFoundException("Expense not found")

            if (before.deletedAt != null) {
                throw ValidationException("Cannot update a deleted expense")
            }

            val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.updateInTransaction(expenseId, amount, category, notes, expectedVersion)

            AuditLogRepository.recordUpdate(
                tableName = ExpenseTable.tableName,
                recordId = expenseId,
                before = before,
                after = after,
                changedBy = callerId,
                branchId = branchDay.branchId,
                isFlagged = isRemitted,
                reason = reason,
                auditFields = ExpenseTable::auditFields,
            )
            after
        }

    @Suppress("ThrowsCount")
    fun softDelete(
        callerId: UUID,
        expenseId: UUID,
        reason: String,
    ): Expense =
        transaction {
            val before =
                ExpenseRepository.findByIdInTransaction(expenseId)
                    ?: throw NotFoundException("Expense not found")

            val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.softDeleteInTransaction(expenseId, callerId, reason)
                    ?: throw NotFoundException("Expense not found")

            AuditLogRepository.recordDelete(
                tableName = ExpenseTable.tableName,
                recordId = expenseId,
                before = before,
                changedBy = callerId,
                branchId = branchDay.branchId,
                reason = reason,
                isFlagged = isRemitted,
                auditFields = ExpenseTable::auditFields,
            )
            after
        }

    /**
     * #153 Q5 — restores a soft-deleted expense. Record-scoped EDIT_BRANCH_DATA via the route's
     * before-filter; day-state gate mirrors every other expense mutation (reason iff REMITTED).
     * The repository's atomic `deletedAt IS NOT NULL` WHERE closes the double-restore race: a
     * restore that lost the race maps to 400 already-live.
     *
     * @throws NotFoundException if the expense does not exist.
     * @throws ValidationException if the expense is not deleted (or the day is REMITTED without a reason).
     */
    @Suppress("ThrowsCount")
    fun restore(
        callerId: UUID,
        expenseId: UUID,
        reason: String? = null,
    ): Expense =
        transaction {
            val before =
                ExpenseRepository.findByIdInTransaction(expenseId)
                    ?: throw NotFoundException("Expense not found")

            if (before.deletedAt == null) {
                throw ValidationException("Expense is not deleted")
            }

            val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.restoreInTransaction(expenseId)
                    ?: throw ValidationException("Expense is not deleted")

            AuditLogRepository.recordUpdate(
                tableName = ExpenseTable.tableName,
                recordId = expenseId,
                before = before,
                after = after,
                changedBy = callerId,
                branchId = branchDay.branchId,
                isFlagged = isRemitted,
                reason = reason,
                auditFields = ExpenseTable::auditFields,
            )
            after
        }

    @Suppress("ThrowsCount", "UnusedParameter")
    fun findByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<Expense> {
        BranchDayService.requireBranchDayExists(branchDayId)

        return ExpenseRepository.findByBranchDayId(branchDayId)
    }
}
