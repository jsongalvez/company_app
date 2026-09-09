package com.companyb.companyapp.finance

import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinanceContextPolicyTest {
    private fun day(id: String) =
        DailySalesSummaryResponse(
            branchDayId = id,
            branchId = "branch-a",
            date = "2026-08-14",
            grossIncome = "1000.00",
            totalCompensation = "200.00",
            totalExpenses = "50.00",
            netIncome = "750.00",
            totalProductSales = "300.00",
            totalCommission = "10.0000",
        )

    @Test
    fun anchor_resolvesToMatchingIndex() {
        val days = listOf(day("d-1"), day("d-2"), day("d-3"))

        assertEquals(2, financeAnchorIndex(days, "d-3"))
        assertEquals(0, financeAnchorIndex(days, "d-1"))
    }

    @Test
    fun anchor_missingAndBlankFallBackToStart() {
        val days = listOf(day("d-1"), day("d-2"))

        assertEquals(0, financeAnchorIndex(days, "d-9"))
        assertEquals(0, financeAnchorIndex(days, null))
        assertEquals(0, financeAnchorIndex(days, ""))
        assertEquals(0, financeAnchorIndex(emptyList(), "d-1"))
    }

    @Test
    fun restoredBranch_prefersStoredWhenAccessible() {
        assertEquals(
            "branch-b",
            resolveRestoredFinanceBranch("branch-b", listOf("branch-a", "branch-b"), "branch-a"),
        )
    }

    @Test
    fun restoredBranch_fallsBackThroughTheSafeDefaultRule() {
        // Stored branch gone: clocked branch wins when authorized, else first accessible.
        assertEquals(
            "branch-a",
            resolveRestoredFinanceBranch("branch-gone", listOf("branch-a", "branch-b"), "branch-a"),
        )
        assertEquals(
            "branch-a",
            resolveRestoredFinanceBranch("branch-gone", listOf("branch-a", "branch-b"), "branch-gone"),
        )
        assertEquals(
            "branch-a",
            resolveRestoredFinanceBranch("branch-gone", listOf("branch-a", "branch-b"), null),
        )
        assertNull(resolveRestoredFinanceBranch("branch-gone", emptyList(), "branch-a"))
    }

    @Test
    fun restoredDay_returnsOnlyMembersOfTheFeed() {
        val feed = listOf(day("d-1"), day("d-2"))

        assertEquals("d-2", resolveRestoredFinanceDay("d-2", feed)?.branchDayId)
        assertNull(resolveRestoredFinanceDay("d-9", feed))
        assertNull(resolveRestoredFinanceDay(null, feed))
        assertNull(resolveRestoredFinanceDay("", feed))
    }

    @Test
    fun storedScope_parsesValidAndDegradesInvalidToNull() {
        assertEquals(ReportMode.MONTHLY, parseStoredFinanceMode("MONTHLY"))
        assertNull(parseStoredFinanceMode("FORTNIGHTLY"))
        assertNull(parseStoredFinanceMode(null))
        assertEquals(YearMonth(2026, 8), parseStoredFinanceMonth("2026-08"))
        assertNull(parseStoredFinanceMonth(""))
        assertNull(parseStoredFinanceMonth("not-a-month"))
        assertEquals(YearMonth(2026, 8), parseStoredFinanceJumpMonth("2026-08"))
        assertNull(parseStoredFinanceJumpMonth(""))
        assertNull(parseStoredFinanceJumpMonth(null))
        assertEquals(
            "2026-08-01" to "2026-08-10",
            parseStoredFinanceRange("2026-08-01", "2026-08-10"),
        )
        assertNull(parseStoredFinanceRange("", ""))
        assertNull(parseStoredFinanceRange("2026-08-10", "2026-08-01"))
        assertNull(parseStoredFinanceRange("garbage", "2026-08-10"))
    }

    @Test
    fun pickerMillis_roundTripWithoutDayShift() {
        assertEquals("2026-08-14", financePickerMillisToDate(financeDateToPickerMillis("2026-08-14")))
        assertEquals("2026-08", financePickerMillisToMonth(financeMonthToPickerMillis("2026-08")))
        // Picking any day in the month selects that month.
        assertEquals("2026-08", financePickerMillisToMonth(financeDateToPickerMillis("2026-08-20")))
        assertNull(financeDateToPickerMillis("garbage"))
        assertNull(financeMonthToPickerMillis("2026-13"))
        assertNull(financePickerMillisToDate(null))
        assertNull(financePickerMillisToMonth(null))
    }
}
