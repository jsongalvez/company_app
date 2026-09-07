package com.companyb.companyapp.remittance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// #447 — date-picker range helpers (owner verdict on #429): the Material3 DatePicker
// speaks epoch millis, the remittance contract speaks yyyy-MM-dd Manila calendar dates.
// Split from RemittanceUi.kt (function-count budget).

// The picker canonicalizes millis as a UTC calendar date: seed and read back in UTC so
// confirming an unchanged date can never shift it a day (Manila midnight is the
// previous UTC date).
@OptIn(ExperimentalTime::class)
internal fun isoToPickerMillis(value: String): Long? {
    if (!isValidIsoDate(value)) return null
    return LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}

@OptIn(ExperimentalTime::class)
internal fun pickerMillisToIso(millis: Long?): String? {
    if (millis == null) return null
    return Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
}

// Single fail-closed range check shared by the create + header-edit dialogs:
// invalid/hollow input and start-after-end are rejected before any draft write.
internal fun remittanceRangeError(
    startIso: String,
    endIso: String,
): String? {
    if (!isValidIsoDate(startIso) || !isValidIsoDate(endIso)) {
        return "Pick a valid start and end date"
    }
    if (startIso > endIso) {
        return "Start date must be on or before end date"
    }
    return null
}
