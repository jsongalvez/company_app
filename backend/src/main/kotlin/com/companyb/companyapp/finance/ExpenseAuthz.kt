package com.companyb.companyapp.finance

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Finance-owned HTTP authorization composition.
 *
 * Resolves the expense record, then delegates to the common branch/day scope
 * check on [Access]. Authorization keeps capability-context
 * evaluation; how an expense resolves that scope lives here, next to the
 * routes that need it.
 */
object ExpenseAuthz {
    /**
     * [Access.require] for [act] on the expense's day.
     * Throws NotFoundResponse (404) when the expense does not exist.
     */
    fun requireForExpense(
        context: Context,
        expenseId: UUID,
        act: Act,
    ) {
        val expense =
            FinanceReads.findExpenseById(expenseId)
                ?: throw NotFoundResponse("Expense not found")
        Access.require(context, act, Place.day(expense.branchDayId))
    }
}
