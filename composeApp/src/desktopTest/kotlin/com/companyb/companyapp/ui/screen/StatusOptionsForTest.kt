package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * #425 — dashboard status options mirror the shared transition matrix and Coordinator gate.
 */
class StatusOptionsForTest {
    @Test
    fun `walk-in pending rows only offer pending and completed`() {
        val options =
            statusOptionsFor(
                true,
                SessionStatus.PENDING,
                hasCorrectionAuthority = false,
                dayStatus = DayStatus.OPEN,
            )

        assertEquals(setOf("PENDING", "COMPLETED"), options.toSet())
    }

    @Test
    fun `booked pending rows offer routine marks`() {
        val options =
            statusOptionsFor(
                false,
                SessionStatus.PENDING,
                hasCorrectionAuthority = false,
                dayStatus = DayStatus.OPEN,
            )

        assertEquals(setOf("PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"), options.toSet())
    }

    @Test
    fun `terminal corrections are hidden without Coordinator authority`() {
        val options =
            statusOptionsFor(
                false,
                SessionStatus.NO_SHOW,
                hasCorrectionAuthority = false,
                dayStatus = DayStatus.OPEN,
            )

        assertEquals(listOf("NO_SHOW"), options)
    }

    @Test
    fun `Coordinator sees legal terminal correction targets`() {
        val options = statusOptionsFor(false, SessionStatus.NO_SHOW, true, DayStatus.OPEN)
        val cancelledOptions = statusOptionsFor(false, SessionStatus.CANCELLED, true, DayStatus.OPEN)

        assertEquals(setOf("PENDING", "NO_SHOW", "CANCELLED"), options.toSet())
        assertEquals(setOf("PENDING", "NO_SHOW", "CANCELLED"), cancelledOptions.toSet())
    }

    @Test
    fun `completed status has no edit affordance`() {
        assertEquals(
            listOf("COMPLETED"),
            statusOptionsFor(false, SessionStatus.COMPLETED, true, DayStatus.OPEN),
        )
        assertFalse(statusEditAllowed(false, SessionStatus.COMPLETED, true, DayStatus.OPEN))
    }

    @Test
    fun `past and remitted rows fail closed without Coordinator authority`() {
        assertEquals(
            listOf("PENDING"),
            statusOptionsFor(false, SessionStatus.PENDING, false, DayStatus.PAST),
        )
        assertFalse(statusEditAllowed(false, SessionStatus.PENDING, false, DayStatus.PAST))
        assertEquals(
            listOf("NO_SHOW"),
            statusOptionsFor(false, SessionStatus.NO_SHOW, false, DayStatus.REMITTED),
        )
        assertFalse(statusEditAllowed(false, SessionStatus.NO_SHOW, false, DayStatus.REMITTED))
    }

    @Test
    fun `unknown day state fails closed`() {
        assertEquals(
            listOf("PENDING"),
            statusOptionsFor(false, SessionStatus.PENDING, true, null),
        )
        assertFalse(statusEditAllowed(false, SessionStatus.PENDING, true, null))
    }
}
