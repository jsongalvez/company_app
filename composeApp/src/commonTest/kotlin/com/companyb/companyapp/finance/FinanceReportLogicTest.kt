package com.companyb.companyapp.finance

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinanceReportLogicTest {
    private val today = LocalDate(2026, 8, 14)

    @Test
    fun dailyMode_pinsWindowToToday() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.DAILY,
                    today = today,
                    month = null,
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = null, to = "2026-08-14"), window)
    }

    @Test
    fun monthlyMode_boundsTheMonth() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.MONTHLY,
                    today = today,
                    month = YearMonth(2026, 8),
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = "2026-08-01", to = "2026-08-31"), window)
    }

    @Test
    fun monthlyMode_yearBoundaryMonthEndsInJanuary() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.MONTHLY,
                    today = today,
                    month = YearMonth(2026, 12),
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = "2026-12-01", to = "2026-12-31"), window)
    }

    @Test
    fun monthlyMode_leapFebruaryEndsOnThe29th() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.MONTHLY,
                    today = today,
                    month = YearMonth(2028, 2),
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = "2028-02-01", to = "2028-02-29"), window)
    }

    @Test
    fun monthlyMode_nullMonthDefaultsToTodayMonth() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.MONTHLY,
                    today = today,
                    month = null,
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = "2026-08-01", to = "2026-08-31"), window)
    }

    @Test
    fun allTimeMode_isUnboundedWithoutJump() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.ALL_TIME,
                    today = today,
                    month = null,
                    rangeFrom = "ignored",
                    rangeTo = "ignored",
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = null, to = null), window)
    }

    @Test
    fun allTimeMode_jumpMonthScopesTheWindow() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.ALL_TIME,
                    today = today,
                    month = null,
                    rangeFrom = null,
                    rangeTo = null,
                    jumpMonth = YearMonth(2025, 3),
                ),
            )
        assertEquals(FeedWindow(from = "2025-03-01", to = "2025-03-31"), window)
    }

    @Test
    fun dateRangeMode_passesTheRangeThrough() {
        val window =
            feedWindowFor(
                FeedWindowRequest(
                    mode = ReportMode.DATE_RANGE,
                    today = today,
                    month = null,
                    rangeFrom = "2026-08-01",
                    rangeTo = "2026-08-10",
                    jumpMonth = null,
                ),
            )
        assertEquals(FeedWindow(from = "2026-08-01", to = "2026-08-10"), window)
    }

    @Test
    fun derivedDayState_todayIsOpen_pastIsPast() {
        assertEquals(DerivedDayState.OPEN, derivedDayState(today, today))
        assertEquals(DerivedDayState.OPEN, derivedDayState(today.plus(1, DateTimeUnit.DAY), today))
        assertEquals(DerivedDayState.PAST, derivedDayState(today.minus(1, DateTimeUnit.DAY), today))
    }

    @Test
    fun parseYearMonthInput_acceptsValidAndRejectsGarbage() {
        assertEquals(YearMonth(2026, 8), parseYearMonthInput("2026-08"))
        assertEquals(YearMonth(2026, 8), parseYearMonthInput(" 2026-08 "))
        assertNull(parseYearMonthInput("2026-13"))
        assertNull(parseYearMonthInput("2026"))
        assertNull(parseYearMonthInput("aug-2026"))
        assertNull(parseYearMonthInput(""))
    }

    @Test
    fun parseDateInput_acceptsIsoAndRejectsGarbage() {
        assertEquals(LocalDate(2026, 8, 14), parseDateInput("2026-08-14"))
        assertEquals(LocalDate(2026, 8, 14), parseDateInput(" 2026-08-14 "))
        assertNull(parseDateInput("2026-8-14"))
        assertNull(parseDateInput("14/08/2026"))
        assertNull(parseDateInput(""))
    }

    @Test
    fun expenseAmountError_requiresStrictlyPositiveAmount() {
        assertNull(expenseAmountError("100.00"))
        assertNull(expenseAmountError("0.01"))
        assertEquals("Amount is required", expenseAmountError(""))
        assertEquals("Amount is required", expenseAmountError("   "))
        assertEquals("Amount must be positive", expenseAmountError("0"))
        assertEquals("Amount must be positive", expenseAmountError("0.00"))
        assertEquals("Amount must be positive", expenseAmountError("-5.00"))
        assertEquals("Enter a valid amount", expenseAmountError("abc"))
        // #654 — strict shapes route through the single invalid leg.
        assertEquals("Enter a valid amount", expenseAmountError("12.34.56"))
        assertEquals("Enter a valid amount", expenseAmountError(".50"))
        assertEquals("Enter a valid amount", expenseAmountError("5."))
        assertEquals("Amount must be positive", expenseAmountError("-0.50"))
    }

    @Test
    fun compensationAmountError_permitsZero() {
        assertNull(compensationAmountError("100.00"))
        assertNull(compensationAmountError("0"))
        assertNull(compensationAmountError("0.00"))
        assertEquals("Amount is required", compensationAmountError(""))
        assertEquals("Amount must be non-negative", compensationAmountError("-1.00"))
        assertEquals("Enter a valid amount", compensationAmountError("abc"))
        // #654 — strict shapes route through the single invalid leg.
        assertEquals("Enter a valid amount", compensationAmountError("12.34.56"))
        assertEquals("Amount must be non-negative", compensationAmountError("-0.50"))
    }

    @Test
    fun derivedDayStateFromIso_mapsValidAndNullsGarbage() {
        assertEquals(DerivedDayState.OPEN, derivedDayStateFromIso("2026-08-14", today))
        assertEquals(DerivedDayState.OPEN, derivedDayStateFromIso("2026-08-15", today))
        assertEquals(DerivedDayState.PAST, derivedDayStateFromIso("2026-08-13", today))
        // #654 — malformed server dates never throw: null fails closed upstream to PAST.
        assertNull(derivedDayStateFromIso("not-a-date", today))
        assertNull(derivedDayStateFromIso("", today))
        assertNull(derivedDayStateFromIso("2026-13-40", today))
        assertNull(derivedDayStateFromIso("14/08/2026", today))
    }

    @Test
    fun dayStateBannerTextFromIso_showsInvalidDatePlaceholder() {
        assertEquals(
            "Today's data — edits apply immediately",
            dayStateBannerTextFromIso("2026-08-14", today),
        )
        assertEquals(
            "Warning: past day — writes require the EDIT_PAST_DAY capability",
            dayStateBannerTextFromIso("2026-08-13", today),
        )
        assertEquals(
            "Invalid date — showing read-only",
            dayStateBannerTextFromIso("garbage", today),
        )
        assertEquals(
            "Invalid date — showing read-only",
            dayStateBannerTextFromIso("", today),
        )
    }
}
