package com.companyb.companyapp.finance

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Finance-owned HTTP authorization composition.
 *
 * Finds the place a route's `Gate.At` checks from the record it names; the gate asks [Access]
 * itself. How the record resolves that place lives here, next to the routes that need it.
 */
object ExpenseAuthz {
    /** The expense's day. Throws NotFoundResponse (404) when the expense does not exist. */
    fun placeOf(expenseId: UUID): Place.Day {
        val expense =
            FinanceReads.findExpenseById(expenseId)
                ?: throw NotFoundResponse("Expense not found")
        return Place.day(expense.branchDayId)
    }
}
