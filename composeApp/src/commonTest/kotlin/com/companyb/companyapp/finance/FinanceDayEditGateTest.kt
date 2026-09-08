package com.companyb.companyapp.finance

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val GATE_TODAY = LocalDate(2026, 8, 14)

private fun gateDay(
    date: String,
    branchDayId: String = "day-1",
) = DailySalesSummaryResponse(
    branchDayId = branchDayId,
    branchId = "branch-a",
    date = date,
    grossIncome = "1000.00",
    totalCompensation = "200.00",
    totalExpenses = "50.00",
    netIncome = "750.00",
    totalProductSales = "300.00",
    totalCommission = "10.0000",
)

private fun gateRow(
    code: String,
    context: CapabilityContextType = CapabilityContextType.BRANCH,
    contextId: String = "branch-a",
) = UserCapabilityResponse(code, context, contextId, CapabilitySourceType.MANUAL_OVERRIDE)

/**
 * #678 — the selected-day Edit gate: same branches as the retired toolbar toggle
 * (EDIT_BRANCH_DATA at the branch or a BRANCH_DAY grant for the day, plus the
 * ASSIGN/EDIT_PAST_DAY legs), failing closed on past days without EDIT_PAST_DAY,
 * on malformed server dates, and on a null branch. The backend 403 stays authoritative.
 */
class FinanceDayEditGateTest {
    @Test
    fun openDay_withEditGrant_allows() {
        assertTrue(
            dayEditAllowed(
                listOf(gateRow("EDIT_BRANCH_DATA")),
                "branch-a",
                gateDay("2026-08-14"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun futureDay_withEditGrant_allows() {
        assertTrue(
            dayEditAllowed(
                listOf(gateRow("EDIT_BRANCH_DATA")),
                "branch-a",
                gateDay("2026-08-15"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun pastDay_withoutPastGrant_denies() {
        assertFalse(
            dayEditAllowed(
                listOf(gateRow("EDIT_BRANCH_DATA")),
                "branch-a",
                gateDay("2026-08-13"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun pastDay_withBothGrants_allows() {
        val caps = listOf(gateRow("EDIT_BRANCH_DATA"), gateRow("EDIT_PAST_DAY"))
        assertTrue(dayEditAllowed(caps, "branch-a", gateDay("2026-08-13"), GATE_TODAY))
    }

    @Test
    fun pastDay_withAssignOnly_denies_withoutPastGrant() {
        assertFalse(
            dayEditAllowed(
                listOf(gateRow("ASSIGN_COMPENSATION")),
                "branch-a",
                gateDay("2026-08-13"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun openDay_withAssignOnly_allows() {
        assertTrue(
            dayEditAllowed(
                listOf(gateRow("ASSIGN_COMPENSATION")),
                "branch-a",
                gateDay("2026-08-14"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun malformedDate_failsClosed_withoutPastGrant() {
        assertFalse(
            dayEditAllowed(
                listOf(gateRow("EDIT_BRANCH_DATA")),
                "branch-a",
                gateDay("garbage"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun nullBranch_denies() {
        assertFalse(
            dayEditAllowed(
                listOf(gateRow("EDIT_BRANCH_DATA")),
                null,
                gateDay("2026-08-14"),
                GATE_TODAY,
            ),
        )
    }

    @Test
    fun noCapabilities_denies() {
        assertFalse(dayEditAllowed(emptyList(), "branch-a", gateDay("2026-08-14"), GATE_TODAY))
    }

    @Test
    fun dayGrant_withoutBranchGrant_allows_that_day_only() {
        val dayGrant = gateRow("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "day-1")
        assertTrue(dayEditAllowed(listOf(dayGrant), "branch-a", gateDay("2026-08-14"), GATE_TODAY))
        val otherGrant = gateRow("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "day-9")
        assertFalse(dayEditAllowed(listOf(otherGrant), "branch-a", gateDay("2026-08-14"), GATE_TODAY))
    }
}
