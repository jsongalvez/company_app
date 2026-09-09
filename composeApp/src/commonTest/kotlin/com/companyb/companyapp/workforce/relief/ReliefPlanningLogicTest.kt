package com.companyb.companyapp.workforce.relief

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #680 — the shared planning date rules: default, eligibility, visible label. */
class ReliefPlanningLogicTest {
    private val today = LocalDate(2026, 9, 8)

    @Test
    fun `future viewed date is the default`() {
        assertEquals("2026-09-12", defaultPlanningDate("2026-09-12", today))
    }

    @Test
    fun `today viewed date defaults to tomorrow`() {
        assertEquals("2026-09-09", defaultPlanningDate("2026-09-08", today))
    }

    @Test
    fun `past viewed date defaults to tomorrow`() {
        assertEquals("2026-09-09", defaultPlanningDate("2026-09-01", today))
    }

    @Test
    fun `null viewed date defaults to tomorrow`() {
        assertEquals("2026-09-09", defaultPlanningDate(null, today))
    }

    @Test
    fun `unparseable viewed date defaults to tomorrow`() {
        assertEquals("2026-09-09", defaultPlanningDate("next friday", today))
    }

    @Test
    fun `month boundary rolls over`() {
        assertEquals("2026-10-01", defaultPlanningDate(null, LocalDate(2026, 9, 30)))
    }

    @Test
    fun `today and future dates are eligible`() {
        assertTrue(isPlanningDateEligible("2026-09-08", today))
        assertTrue(isPlanningDateEligible("2026-09-20", today))
    }

    @Test
    fun `past and unparseable dates are ineligible`() {
        assertFalse(isPlanningDateEligible("2026-09-07", today))
        assertFalse(isPlanningDateEligible(null, today))
        assertFalse(isPlanningDateEligible("", today))
        assertFalse(isPlanningDateEligible("2026-13-40", today))
    }

    @Test
    fun `label names the actual chosen date`() {
        assertEquals("Invite date: 2026-09-12", planningDateLabel("2026-09-12"))
    }

    @Test
    fun `selection clears only on a real day change`() {
        val first = LocalDate(2026, 9, 12)
        val second = LocalDate(2026, 9, 13)
        assertTrue(shouldClearPlanningSelection(first, second))
        assertFalse(shouldClearPlanningSelection(first, first), "same-day retype keeps the selection")
        assertFalse(shouldClearPlanningSelection(first, null), "mid-typing invalid input keeps the selection")
        assertFalse(shouldClearPlanningSelection(null, second), "first valid parse keeps the selection")
        assertFalse(shouldClearPlanningSelection(null, null))
    }

    @Test
    fun `picker millis round-trip the ISO date`() {
        val millis = reliefIsoToPickerMillis("2026-09-12")
        assertNotNull(millis)
        assertEquals("2026-09-12", reliefPickerMillisToIso(millis))
    }

    @Test
    fun `picker millis reject invalid input`() {
        assertNull(reliefIsoToPickerMillis("next friday"))
        assertNull(reliefIsoToPickerMillis(""))
        assertNull(reliefPickerMillisToIso(null))
    }

    @Test
    fun `requests heading names the active date`() {
        assertEquals("Requests — 2026-09-09", reliefRequestsHeading("2026-09-09"))
    }

    @Test
    fun `requests heading never fabricates a date`() {
        assertEquals("Requests", reliefRequestsHeading(null))
        assertEquals("Requests", reliefRequestsHeading(""))
        assertEquals("Requests", reliefRequestsHeading("   "))
    }

    @Test
    fun `invite heading names the selected date`() {
        assertEquals("Invite staff — 2026-09-10", reliefInviteHeading("2026-09-10"))
        assertEquals("Invite staff", reliefInviteHeading(""))
    }

    @Test
    fun `history heading establishes the branch-wide scope`() {
        assertEquals(
            "Invitation history — all dates for Branch A",
            reliefHistoryHeading("Branch A"),
        )
        assertEquals("Invitation history — all dates for Branch", reliefHistoryHeading(null))
        assertEquals("Invitation history — all dates for Branch", reliefHistoryHeading("  "))
    }

    @Test
    fun `invite date changes cannot mutate requests or history scope`() {
        val activeDate = "2026-09-09"
        assertEquals(reliefRequestsHeading(activeDate), reliefRequestsHeading(activeDate))
        val history = reliefHistoryHeading("Branch A")
        assertFalse(history.contains("2026-09-10"))
        assertFalse(history.contains("2026-09-12"))
        assertTrue(history.contains("all dates"))
    }

    @Test
    fun `read-only destination names its authority`() {
        assertEquals("Read-only relief activity", RELIEF_DAY_READ_ONLY_TITLE)
    }
}
