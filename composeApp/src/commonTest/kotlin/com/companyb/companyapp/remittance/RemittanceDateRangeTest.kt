package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.ui.screen.sessionLinesGrossCents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

// #447 — date-picker range helpers: Manila-calendar conversions + fail-closed validation.
class RemittanceDateRangeTest {
    @Test
    fun isoRoundTripsThroughPickerMillis() {
        val millis = isoToPickerMillis("2026-09-03")
        assertEquals("2026-09-03", pickerMillisToIso(millis))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun pickerMillisAreUtcCalendarDates() {
        // The picker canonicalizes millis as a UTC date: Manila midnight (+08:00) is the
        // previous UTC date, so seeding must be UTC or confirming shifts the day back.
        val expected = LocalDate(2026, 9, 3).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(expected, isoToPickerMillis("2026-09-03"))
    }

    @Test
    fun isoToPickerMillis_rejectsGarbage() {
        assertNull(isoToPickerMillis("not-a-date"))
        assertNull(isoToPickerMillis("2026-13-40"))
        assertNull(isoToPickerMillis(""))
    }

    @Test
    fun pickerMillisToIso_nullStaysNull() {
        assertNull(pickerMillisToIso(null))
    }

    @Test
    fun rangeError_acceptsOrderedAndEqualRanges() {
        assertNull(remittanceRangeError("2026-09-01", "2026-09-03"))
        assertNull(remittanceRangeError("2026-09-03", "2026-09-03"))
    }

    @Test
    fun rangeError_rejectsStartAfterEnd() {
        assertEquals(
            "Start date must be on or before end date",
            remittanceRangeError("2026-09-04", "2026-09-03"),
        )
    }

    @Test
    fun rangeError_rejectsInvalidInput() {
        assertEquals(
            "Pick a valid start and end date",
            remittanceRangeError("", "2026-09-03"),
        )
        assertEquals(
            "Pick a valid start and end date",
            remittanceRangeError("2026-09-03", "junk"),
        )
    }

    @Test
    fun sessionGross_sumsSessionLinesOnly() {
        val lines =
            listOf(
                line("SESSION", "s1", "100.00"),
                line("PRODUCT_SALE", "p1", "50.00"),
                line("SESSION", "s2", "25.50"),
            )
        assertEquals(12550L, sessionLinesGrossCents(lines))
    }

    @Test
    fun sessionGross_emptyWithoutSessionLines() {
        assertEquals(0L, sessionLinesGrossCents(emptyList()))
        assertEquals(
            0L,
            sessionLinesGrossCents(listOf(line("PRODUCT_SALE", "p1", "50.00"))),
        )
    }

    private fun line(
        type: String,
        sourceId: String,
        amount: String,
    ): RemittanceLineResponse =
        RemittanceLineResponse(
            id = "l-$sourceId",
            remittanceId = "r1",
            type = RemittanceLineType.valueOf(type),
            sessionId = if (type == "SESSION") sourceId else null,
            productSaleId = if (type == "PRODUCT_SALE") sourceId else null,
            createdBy = "u1",
            createdAt = "2026-09-03T00:00:00Z",
            deletedBy = null,
            deletedAt = null,
            amount = amount,
        )
}
