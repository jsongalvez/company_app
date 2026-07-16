package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.ExpenseRepository
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
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
        BranchDayRepository.findById(branchDayId)
            ?: throw io.javalin.http.NotFoundResponse("Branch day not found")

        val existing = ExpenseRepository.findById(id)
        if (existing != null) {
            return existing
        }

        if (amount <= BigDecimal.ZERO) {
            throw BadRequestResponse("Amount must be positive")
        }

        BranchDayService.assertEditable(branchDayId, callerId)

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
                ?: throw io.javalin.http.NotFoundResponse("Expense not found")

        BranchDayRepository.findById(expense.branchDayId)
            ?: throw io.javalin.http.NotFoundResponse("Branch day not found")

        if (reason.isBlank()) {
            throw BadRequestResponse("Reason is required for expense deletion")
        }

        BranchDayService.assertEditable(expense.branchDayId, callerId, reason)

        return ExpenseRepository.softDelete(expenseId, callerId, reason)
            ?: throw io.javalin.http.NotFoundResponse("Expense not found")
    }

    @Suppress("ThrowsCount", "UnusedParameter")
    fun findByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<Expense> {
        BranchDayRepository.findById(branchDayId)
            ?: throw io.javalin.http.NotFoundResponse("Branch day not found")

        return ExpenseRepository.findByBranchDayId(branchDayId)
    }
}
