package com.companyb.companyapp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * #423 — the create-flow booking gate: walk-in sends no booking fields; booked stamps
 * bookedAt = now and carries the optional ISO next-appointment date; an unparseable date
 * draft shapes to null so submit stays disabled.
 */
class SessionCreateBookingFieldsTest {
    private val now = Instant.parse("2026-08-26T04:00:00Z")

    @Test
    fun `walk-in sends no booking fields`() {
        val fields = bookingFields(isBooked = false, nextAppointmentDraft = "2026-09-01", now)

        assertEquals(
            BookingFields(isWalkIn = true, bookedAt = null, nextAppointmentDate = null),
            fields,
        )
    }

    @Test
    fun `booked with blank draft stamps now and omits the date`() {
        val fields = bookingFields(isBooked = true, nextAppointmentDraft = "   ", now)

        assertEquals(
            BookingFields(isWalkIn = false, bookedAt = "2026-08-26T04:00:00Z", nextAppointmentDate = null),
            fields,
        )
    }

    @Test
    fun `booked with a valid date carries it trimmed`() {
        val fields = bookingFields(isBooked = true, nextAppointmentDraft = " 2026-09-01 ", now)

        assertEquals(
            BookingFields(isWalkIn = false, bookedAt = "2026-08-26T04:00:00Z", nextAppointmentDate = "2026-09-01"),
            fields,
        )
    }

    @Test
    fun `booked with an unparseable date rejects submit`() {
        assertNull(bookingFields(isBooked = true, nextAppointmentDraft = "09/01/2026", now))
        assertNull(bookingFields(isBooked = true, nextAppointmentDraft = "not-a-date", now))
    }
}
