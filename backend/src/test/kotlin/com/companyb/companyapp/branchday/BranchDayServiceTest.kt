package com.companyb.companyapp.branchday

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
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

    // ---- currentOperationalDate (04:00 Asia/Manila rollover) ----

    private fun manilaInstant(
        date: LocalDate,
        time: LocalTime,
    ): Instant = ZonedDateTime.of(date, time, BranchDayService.manilaZone).toInstant()

    @Test
    fun `nanosecond before cutoff belongs to previous operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(3, 59, 59, 999_999_999))
        assertEquals(LocalDate.of(2026, 6, 26), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `nanosecond at cutoff rolls to next operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(4, 0, 0, 0))
        assertEquals(LocalDate.of(2026, 6, 27), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `one second before 4am belongs to previous operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(3, 59, 59))
        assertEquals(LocalDate.of(2026, 6, 26), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `exactly 4am rolls to next operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(4, 0, 0))
        assertEquals(LocalDate.of(2026, 6, 27), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `one second after 4am is next operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(4, 0, 1))
        assertEquals(LocalDate.of(2026, 6, 27), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `calendar midnight does not roll the operational date`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.MIDNIGHT)
        assertEquals(LocalDate.of(2026, 6, 26), BranchDayService.currentOperationalDate(at))
    }

    @Test
    fun `midnight to 4am window keeps prior calendar date current`() {
        val at = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(2, 30))
        val opDate = BranchDayService.currentOperationalDate(at)
        // The day dated 06-26 is still the current OPEN day at 02:30 on calendar 06-27.
        assertEquals(DayStatus.OPEN, BranchDayService.evaluateStatus(DayStatus.OPEN, opDate, opDate))
        assertEquals(
            DayStatus.PAST,
            BranchDayService.evaluateStatus(DayStatus.OPEN, LocalDate.of(2026, 6, 25), opDate),
        )
    }

    @Test
    fun `remitted stays remitted regardless of cutoff`() {
        val remittedDay = LocalDate.of(2026, 6, 20)
        val beforeCutoff = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(3, 59, 59))
        val afterCutoff = manilaInstant(LocalDate.of(2026, 6, 27), LocalTime.of(4, 0, 1))
        assertEquals(
            DayStatus.REMITTED,
            BranchDayService.evaluateStatus(
                DayStatus.REMITTED,
                remittedDay,
                BranchDayService.currentOperationalDate(beforeCutoff),
            ),
        )
        assertEquals(
            DayStatus.REMITTED,
            BranchDayService.evaluateStatus(
                DayStatus.REMITTED,
                remittedDay,
                BranchDayService.currentOperationalDate(afterCutoff),
            ),
        )
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

    // ---- assertReadableState ----

    @Test
    fun `open day is always readable without capability`() {
        BranchDayService.assertReadableState(DayStatus.OPEN, hasEditPastDay = false)
    }

    @Test
    fun `open day read with capability is allowed`() {
        BranchDayService.assertReadableState(DayStatus.OPEN, hasEditPastDay = true)
    }

    @Test
    fun `past day read without EDIT_PAST_DAY is forbidden`() {
        assertFailsWith<ForbiddenException> {
            BranchDayService.assertReadableState(DayStatus.PAST, hasEditPastDay = false)
        }
    }

    @Test
    fun `past day read with EDIT_PAST_DAY is allowed`() {
        BranchDayService.assertReadableState(DayStatus.PAST, hasEditPastDay = true)
    }

    @Test
    fun `remitted day read with EDIT_PAST_DAY is allowed without reason`() {
        BranchDayService.assertReadableState(DayStatus.REMITTED, hasEditPastDay = true)
    }

    @Test
    fun `remitted day read without EDIT_PAST_DAY is forbidden`() {
        assertFailsWith<ForbiddenException> {
            BranchDayService.assertReadableState(DayStatus.REMITTED, hasEditPastDay = false)
        }
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
