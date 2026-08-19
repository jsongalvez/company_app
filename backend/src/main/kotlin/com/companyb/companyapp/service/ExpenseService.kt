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
import java.math.BigDecimal
import java.util.UUID

object ExpenseService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
        reason: String? = null,
    ): Expense {
        val existing = ExpenseRepository.findById(id)
        if (existing != null) {
            return existing
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

        return ExpenseRepository.create(
            ExpenseCreateParams(
                id = id,
                branchDayId = branchDayId,
                amount = amount,
                category = category,
                createdBy = callerId,
                notes = notes,
            ),
        ) { expense ->
            AuditLogRepository.recordInsert(
                tableName = ExpenseTable.tableName,
                recordId = expense.id,
                changedBy = callerId,
                branchId = branchDay.branchId,
                fields = ExpenseTable.auditFields(expense),
                isFlagged = isRemitted,
                reason = reason,
            )
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
    ): Expense {
        val before =
            ExpenseRepository.findById(expenseId)
                ?: throw NotFoundException("Expense not found")

        if (before.deletedAt != null) {
            throw ValidationException("Cannot update a deleted expense")
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

        return ExpenseRepository.update(expenseId, amount, category, notes, expectedVersion) { after ->
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
        }
    }

    @Suppress("ThrowsCount")
    fun softDelete(
        callerId: UUID,
        expenseId: UUID,
        reason: String,
    ): Expense {
        val before =
            ExpenseRepository.findById(expenseId)
                ?: throw NotFoundException("Expense not found")

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

        return ExpenseRepository.softDelete(expenseId, callerId, reason) { after ->
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
        }
            ?: throw NotFoundException("Expense not found")
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
    ): Expense {
        val before =
            ExpenseRepository.findById(expenseId)
                ?: throw NotFoundException("Expense not found")

        if (before.deletedAt == null) {
            throw ValidationException("Expense is not deleted")
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

        return ExpenseRepository.restore(expenseId) { after ->
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
        }
            ?: throw ValidationException("Expense is not deleted")
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
