package com.companyb.companyapp.branchday

import com.companyb.companyapp.domain.OperationalDay
import io.javalin.http.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * When the current request's change happened, as the phone that made it says.
 *
 * A save made with no signal is sent later, so the server would otherwise stamp it with the
 * send time: a sale at 10:00 sent at 18:00 would split its commission among whoever was on duty
 * at 18:00. The phone sends [HEADER] with each queued save; the server trusts it only within
 * bounds (never after the server's own clock, never outside the day the save belongs to) and
 * keeps its own receive time beside it.
 *
 * Set per request by the auth filter, like [com.companyb.companyapp.audit.ViewedAsBy]; a request
 * without the header reads as happening now.
 */
object OpTime {
    const val HEADER = "X-Occurred-At"

    /** How long after it happened a save may still land on a day that has closed since, by itself. */
    val LATE_WINDOW: Duration = Duration.ofHours(48)

    private class State(
        val received: Instant,
        val claimed: Instant?,
    ) {
        var late: Boolean = false
    }

    private val current = ThreadLocal<State?>()

    /** Auth-filter step: reads the header. A value that isn't a time is ignored, never refused. */
    fun resolve(context: Context) {
        set(parse(context.header(HEADER)), Instant.now())
    }

    fun set(
        claimed: Instant?,
        received: Instant = Instant.now(),
    ) = current.set(State(received, claimed?.let { minOf(it, received) }))

    fun clear() = current.remove()

    fun parse(raw: String?): Instant? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** When the server got the request. */
    fun received(): Instant = current.get()?.received ?: Instant.now()

    /** When the change happened: the phone's time, but never later than the server got it. */
    fun occurred(): Instant = current.get()?.let { it.claimed ?: it.received } ?: Instant.now()

    /** The operational date the change happened on. */
    fun occurredDate(): LocalDate = BranchDayService.currentOperationalDate(occurred())

    /**
     * [occurred] for a change to [date]. A phone's time is kept inside that operational day, so a
     * wrong phone clock can't move it to another day; a change sent live happens now, as always.
     */
    fun occurredWithin(date: LocalDate): OffsetDateTime {
        val at = occurred().atOffset(ZoneOffset.UTC)
        if (current.get()?.claimed == null) return at
        val start = dayStart(date)
        val end = dayStart(date.plusDays(1)).minusNanos(1)
        return if (at.isBefore(start)) {
            start
        } else if (at.isAfter(end)) {
            end
        } else {
            at
        }
    }

    /** [occurred], but never before [floor] (a clock-out before its clock-in). */
    fun occurredAfter(floor: OffsetDateTime): OffsetDateTime {
        val at = occurred().atOffset(ZoneOffset.UTC)
        return if (at.isBefore(floor)) floor else at
    }

    /**
     * May a change to [date] made while it was open land now, after the day closed? True when the
     * phone says it happened on that day and less than [LATE_WINDOW] ago. Marks the request late,
     * so every history row it writes is flagged for review.
     */
    fun admitsLate(date: LocalDate): Boolean {
        val state = current.get() ?: return false
        val claimed = state.claimed ?: return false
        val fits =
            BranchDayService.currentOperationalDate(claimed) == date &&
                Duration.between(claimed, state.received) <= LATE_WINDOW
        if (fits) state.late = true
        return fits
    }

    /** Did this request land on a closed day by [admitsLate]? */
    fun isLate(): Boolean = current.get()?.late == true

    /** The history reason for a late save that gave none. */
    fun lateReason(): String? =
        current.get()?.takeIf { it.late }?.let {
            "Saved offline on ${BranchDayService.currentOperationalDate(
                it.claimed ?: it.received,
            )}, sent on ${BranchDayService.currentOperationalDate(it.received)}"
        }

    private fun dayStart(date: LocalDate): OffsetDateTime =
        date
            .atTime(LocalTime.of(OperationalDay.DAY_BOUNDARY_HOUR, 0))
            .atZone(BranchDayService.manilaZone)
            .toOffsetDateTime()
            .withOffsetSameInstant(ZoneOffset.UTC)
}
