package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.ExpenseRepository
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseCreateParams
import com.companyb.companyapp.repository.model.ExpenseTable
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
    ): Expense {
        val existing = ExpenseRepository.findById(id)
        if (existing != null) {
            return existing
        }

        BranchDayService.checkBranchDayEditable(callerId, branchDayId)

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
            AuditLogRepository.recordInsert(ExpenseTable.tableName, expense, callerId)
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

        BranchDayService.checkBranchDayEditable(callerId, before.branchDayId, reason)

        return ExpenseRepository.softDelete(expenseId, callerId) { after ->
            AuditLogRepository.recordDelete(
                tableName = ExpenseTable.tableName,
                recordId = expenseId,
                oldFields =
                    mapOf(
                        "amount" to before.amount.toPlainString(),
                        "category" to before.category.name,
                    ),
                newFields =
                    mapOf(
                        "amount" to after.amount.toPlainString(),
                        "category" to after.category.name,
                    ),
                changedBy = callerId,
                reason = reason,
            )
        }
            ?: throw NotFoundException("Expense not found")
    }

    @Suppress("ThrowsCount", "UnusedParameter")
    fun findByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<Expense> {
        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundException("Branch day not found")

        return ExpenseRepository.findByBranchDayId(branchDayId)
    }
}
