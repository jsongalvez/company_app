package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.ExpenseRepository
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
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

        return ExpenseRepository.create(id, branchDayId, amount, category, callerId, notes)
    }

    @Suppress("ThrowsCount")
    fun softDelete(
        callerId: UUID,
        expenseId: UUID,
        reason: String,
    ): Expense {
        val expense =
            ExpenseRepository.findById(expenseId)
                ?: throw NotFoundException("Expense not found")

        BranchDayService.checkBranchDayEditable(callerId, expense.branchDayId, reason)

        return ExpenseRepository.softDelete(expenseId, callerId, reason)
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
