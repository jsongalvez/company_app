package com.companyb.companyapp.finance

import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Finance-owned HTTP authorization composition (#605).
 *
 * Resolves the expense record, then delegates to the common branch/day scope
 * check on [CapabilityFilter]. Authorization keeps capability-context
 * evaluation; how an expense resolves that scope lives here, next to the
 * routes that need it.
 */
object ExpenseAuthz {
    /**
     * Day-scoped variant of [CapabilityFilter.requireBranchOrBranchDayCapability] for expenses
     * (#157): accepts a BRANCH grant at the expense's branch OR a BRANCH_DAY grant for the
     * expense's branch day.
     *
     * Throws ForbiddenException (403) if the caller holds neither form.
     * Throws NotFoundResponse (404) if the expense or branch day does not exist.
     */
    fun requireBranchOrBranchDayCapabilityForExpense(
        context: Context,
        expenseId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val expense =
            FinanceReads.findExpenseById(expenseId)
                ?: throw NotFoundResponse("Expense not found")
        CapabilityFilter.requireBranchOrBranchDayCapability(context, expense.branchDayId, capabilityCode)
    }
}
