package com.companyb.companyapp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #423 — the create-flow booking gate: walk-in sends no booking fields; booked stamps
 * leaves bookedAt to the server and carries the optional ISO next-appointment date; an
 * unparseable date draft shapes to null so submit stays disabled.
 */
class SessionCreateBookingFieldsTest {
    @Test
    fun `walk-in sends no booking fields`() {
        val fields = bookingFields(isBooked = false, nextAppointmentDraft = "2026-09-01")

        assertEquals(
            BookingFields(isWalkIn = true, nextAppointmentDate = null),
            fields,
        )
    }

    @Test
    fun `booked with blank draft omits the date`() {
        val fields = bookingFields(isBooked = true, nextAppointmentDraft = "   ")

        assertEquals(
            BookingFields(isWalkIn = false, nextAppointmentDate = null),
            fields,
        )
    }

    @Test
    fun `booked with a valid date carries it trimmed`() {
        val fields = bookingFields(isBooked = true, nextAppointmentDraft = " 2026-09-01 ")

        assertEquals(
            BookingFields(isWalkIn = false, nextAppointmentDate = "2026-09-01"),
            fields,
        )
    }

    @Test
    fun `booked with an unparseable date rejects submit`() {
        assertNull(bookingFields(isBooked = true, nextAppointmentDraft = "09/01/2026"))
        assertNull(bookingFields(isBooked = true, nextAppointmentDraft = "not-a-date"))
    }

    @Test
    fun `submission lock holds on loading and success only`() {
        assertFalse(isSessionCreateLocked(UiState.Idle))
        assertTrue(isSessionCreateLocked(UiState.Loading))
        assertTrue(isSessionCreateLocked(UiState.Success(Unit)))
        assertFalse(isSessionCreateLocked(UiState.Error("boom")))
    }
}
