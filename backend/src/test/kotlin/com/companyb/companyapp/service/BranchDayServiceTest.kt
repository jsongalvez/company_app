package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.service.branchday.BranchDayService
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BranchDayServiceTest {
    private val today = LocalDate.of(2026, 6, 27)

    // ---- evaluateStatus ----

    @Test
    fun `open day in the past is treated as PAST`() {
        val result = BranchDayService.evaluateStatus(DayStatus.OPEN, today.minusDays(1), today)
        assertEquals(DayStatus.PAST, result)
    }

    @Test
    fun `open day today stays OPEN`() {
        val result = BranchDayService.evaluateStatus(DayStatus.OPEN, today, today)
        assertEquals(DayStatus.OPEN, result)
    }

    @Test
    fun `remitted past day stays REMITTED`() {
        val result = BranchDayService.evaluateStatus(DayStatus.REMITTED, today.minusDays(5), today)
        assertEquals(DayStatus.REMITTED, result)
    }

    // ---- assertEditableState ----

    @Test
    fun `open day is always editable without capability or reason`() {
        BranchDayService.assertEditableState(DayStatus.OPEN, hasEditPastDay = false, reason = null)
    }

    @Test
    fun `past day write without EDIT_PAST_DAY is forbidden`() {
        assertFailsWith<ForbiddenException> {
            BranchDayService.assertEditableState(DayStatus.PAST, hasEditPastDay = false, reason = null)
        }
    }

    @Test
    fun `past day write with EDIT_PAST_DAY is allowed without reason`() {
        BranchDayService.assertEditableState(DayStatus.PAST, hasEditPastDay = true, reason = null)
    }

    @Test
    fun `remitted day write without reason is bad request`() {
        assertFailsWith<ValidationException> {
            BranchDayService.assertEditableState(DayStatus.REMITTED, hasEditPastDay = true, reason = "  ")
        }
    }

    @Test
    fun `remitted day write without capability is forbidden before reason check`() {
        assertFailsWith<ForbiddenException> {
            BranchDayService.assertEditableState(DayStatus.REMITTED, hasEditPastDay = false, reason = null)
        }
    }

    @Test
    fun `remitted day write with capability and reason is allowed`() {
        BranchDayService.assertEditableState(DayStatus.REMITTED, hasEditPastDay = true, reason = "late correction")
    }

    // ---- expirationUtc ----

    @Test
    fun `expiration is 4 AM Manila next day expressed in UTC`() {
        // Manila is UTC+8, so 04:00 Manila on the next day == 20:00 UTC on the branch date.
        val result = BranchDayService.expirationUtc(LocalDate.of(2026, 6, 27))
        assertEquals(ZoneOffset.UTC, result.offset)
        assertEquals(2026, result.year)
        assertEquals(6, result.monthValue)
        assertEquals(27, result.dayOfMonth)
        assertEquals(20, result.hour)
        assertEquals(0, result.minute)
    }
}
