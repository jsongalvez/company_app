package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.ExpenseRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Expense
import com.companyb.companyapp.repository.model.ExpenseCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import java.math.BigDecimal
import java.util.UUID

object ExpenseService {
    private val logger = KotlinLogging.logger {}

    private const val EDIT_BRANCH_DATA = "EDIT_BRANCH_DATA"

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        amount: BigDecimal,
        category: ExpenseCategory,
        notes: String?,
    ): Expense {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[CREATE-EXPENSE] User $callerId lacks $EDIT_BRANCH_DATA capability" }
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required")
        }

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
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[DELETE-EXPENSE] User $callerId lacks $EDIT_BRANCH_DATA capability" }
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required")
        }

        if (reason.isBlank()) {
            throw BadRequestResponse("Reason is required for expense deletion")
        }

        val expense =
            ExpenseRepository.findById(expenseId)
                ?: throw io.javalin.http.NotFoundResponse("Expense not found")

        BranchDayService.assertEditable(expense.branchDayId, callerId)

        return ExpenseRepository.softDelete(expenseId, callerId, reason)
            ?: throw io.javalin.http.NotFoundResponse("Expense not found")
    }

    fun findByBranchDayId(branchDayId: UUID): List<Expense> {
        BranchDayRepository.findById(branchDayId)
            ?: throw io.javalin.http.NotFoundResponse("Branch day not found")

        return ExpenseRepository.findByBranchDayId(branchDayId)
    }
}
