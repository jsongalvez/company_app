package com.companyb.companyapp.app.navigation

import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun codecRow() =
    DashboardSessionResponse(
        id = "s1",
        clientId = "c1",
        // Route-hostile payload: every delimiter the codec must survive (? & = # % + space)
        // plus quotes, braces, and non-ASCII text.
        clientName = "O'Brien & Sons? #1 = 100% + déjà",
        sessionType = SessionType.REGULAR,
        isWalkIn = false,
        sessionStatus = SessionStatus.COMPLETED,
        basePrice = "2500.00",
        finalPrice = "2500.00",
        remarks = "line1\nline2 \"quoted\" {braces}",
        otherConcerns = null,
        bookedAt = "2026-08-12T08:00:00+08:00",
        nextAppointmentDate = null,
        version = 1,
        isVoided = false,
        practitioners =
            listOf(
                DashboardPractitionerResponse(
                    practitionerId = "p1",
                    displayName = "Ate Vi & Co.",
                    remarks = null,
                    slotAtTime = 1,
                ),
            ),
        concerns = listOf(ConcernResponse(id = "k1", label = "Back pain?")),
    )

/**
 * #574 — Route.SessionDetail.row codec: null follows the StringType "null" convention on both
 * legs, values round-trip byte-identically (incl. route-hostile characters), and the serialized
 * form stays inside the URI-safe hex alphabet so graph-build/navigate/toRoute never throw.
 */
class SessionDetailRowNavTypeTest {
    @Test
    fun nullSerializesToNullLiteral() {
        assertEquals("null", SessionDetailRowNavType.serializeAsValue(null))
    }

    @Test
    fun nullLiteralParsesToNull() {
        assertNull(SessionDetailRowNavType.parseValue("null"))
    }

    @Test
    fun serializedFormIsUriSafeHex() {
        val encoded = SessionDetailRowNavType.serializeAsValue(codecRow())
        assertTrue(encoded.all { it in '0'..'9' || it in 'a'..'f' }, "encoded: $encoded")
    }

    @Test
    fun valueRoundTripsIdentically() {
        val row = codecRow()
        assertEquals(row, SessionDetailRowNavType.parseValue(SessionDetailRowNavType.serializeAsValue(row)))
    }
}
