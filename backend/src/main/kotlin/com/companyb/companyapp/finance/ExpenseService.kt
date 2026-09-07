package com.companyb.companyapp.finance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
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

    @Suppress("LongParameterList", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        reason: String? = null,
    ): Expense =
        transaction {
            // #510 — in-tx replay classification before the day gate (mirrors #509): a same-id
            // row already committed acks without gating so retries landing after a day transition
            // still ack; ownership matches createInTransaction below.
            ExpenseRepository.findByIdInTransaction(id)?.let { existing ->
                if (existing.branchDayId != branchDayId) {
                    throw NotFoundException("Expense not found for this branch day")
                }
                if (existing.createdBy != callerId) {
                    throw ConflictException("Expense id already belongs to another create request")
                }
                return@transaction existing
            }
            // Locked day read: serializes this create with remittance's REMITTED transition.
            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDayId, reason)

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
                ExpenseAudit.inserted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    result.expense,
                )
            }
            result.expense
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

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.updateInTransaction(expenseId, amount, category, notes, expectedVersion)

            ExpenseAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                before,
                after,
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

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.softDeleteInTransaction(expenseId, callerId, reason)

            ExpenseAudit.deleted(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                before,
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

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, before.branchDayId, reason)

            val after =
                ExpenseRepository.restoreInTransaction(expenseId)
                    ?: throw ValidationException("Expense is not deleted")

            ExpenseAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                before,
                after,
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

internal object ExpenseAudit {
    fun inserted(
        context: AuditContext,
        expense: Expense,
    ) = AuditLog.recordInsert(
        tableName = ExpenseTable.tableName,
        recordId = expense.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = ExpenseTable.auditFields(expense),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: Expense,
        after: Expense,
    ) = AuditLog.recordUpdate(
        tableName = ExpenseTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = ExpenseTable::auditFields,
    )

    fun deleted(
        context: AuditContext,
        before: Expense,
    ) = AuditLog.recordDelete(
        tableName = ExpenseTable.tableName,
        recordId = before.id,
        before = before,
        changedBy = context.changedBy,
        branchId = context.branchId,
        reason = context.reason,
        isFlagged = context.isFlagged,
        auditFields = ExpenseTable::auditFields,
    )
}
