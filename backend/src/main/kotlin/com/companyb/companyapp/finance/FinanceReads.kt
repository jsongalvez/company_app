package com.companyb.companyapp.finance

import java.util.UUID

/**
 * Named finance-read seams for cross-owner consumers (map #533 #546): authorization expense
 * resolution and compensation route gating. Thin delegations to the internal finance stores —
 * not a facade over every method. Service-to-service calls need no architecture allowlist
 * entry; direct store imports from other owners stay banned.
 */
object FinanceReads {
    /** Existence read for the expense day-scoped gate — runs on its own transaction. */
    fun findExpenseById(expenseId: UUID): Expense? = ExpenseRepository.findById(expenseId)

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findExpenseByIdInTransaction(expenseId: UUID): Expense? = ExpenseRepository.findByIdInTransaction(expenseId)

    /** Existence read for the compensation route gate — runs on its own transaction. */
    fun findCompensationById(compensationId: UUID): Compensation? = CompensationRepository.findById(compensationId)

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findCompensationByIdInTransaction(compensationId: UUID): Compensation? =
        CompensationRepository.findByIdInTransaction(compensationId)
}
