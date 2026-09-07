package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The current operational business date in Asia/Manila (rolls over at [DAY_BOUNDARY_HOUR]
 * local).
 *
 * #160 owns pure relief presentation logic; #399 extends it: the client's "today" is the
 * operational date, not the calendar one — mirroring the backend 04:00 Asia/Manila roll
 * (BranchDayService.currentOperationalDate, the sole authority for that boundary).
 * Calendar-midnight comparisons marked 00:00–03:59 rows expired while their branch day was
 * still OPEN.
 */
fun currentOperationalDate(at: Instant): LocalDate {
    val manilaNow = at.toLocalDateTime(TimeZone.of("Asia/Manila"))
    return if (manilaNow.hour < DAY_BOUNDARY_HOUR) {
        LocalDate.fromEpochDays(manilaNow.date.toEpochDays() - ONE_DAY)
    } else {
        manilaNow.date
    }
}

/** Convenience overload resolving [currentOperationalDate] at the current instant. */
fun currentOperationalDate(): LocalDate = currentOperationalDate(Clock.System.now())

private const val DAY_BOUNDARY_HOUR = 4
private const val ONE_DAY = 1

fun parseInviteDate(date: String?): LocalDate? = runCatching { LocalDate.parse(date ?: return null) }.getOrNull()

/** PENDING + day past [today] — stale, renders "expired". */
fun isInviteExpired(
    invite: ReliefInviteResponse,
    today: LocalDate,
): Boolean {
    if (invite.status != ReliefInviteStatus.PENDING) return false
    val date = parseInviteDate(invite.date) ?: return false
    return date < today
}

/** PENDING + not expired — the badge count and the Accept/Decline affordances. */
fun isInviteActionable(
    invite: ReliefInviteResponse,
    today: LocalDate,
): Boolean {
    if (invite.status != ReliefInviteStatus.PENDING) return false
    val date = parseInviteDate(invite.date) ?: return false
    return date >= today
}

/**
 * PENDING request whose duty day is past [today] — stale, renders "expired", no Withdraw.
 * A null/unparseable date means "today" (the mine list omits it for same-day asks), so it
 * is never expired — fail-safe against faking a live row stale.
 */
fun isRequestExpired(
    request: ReliefAccessResponse,
    today: LocalDate,
): Boolean {
    if (request.requestStatus != ReliefAccessStatus.PENDING) return false
    val date = parseInviteDate(request.date) ?: return false
    return date < today
}

/** One rendered row of the deep-link panel's invites section (#401). */
data class ReliefDayInviteRow(
    val title: String,
    val statusText: String,
)

/**
 * #401 — the branch+date panel's invite rows: who was invited and the invite's true
 * current status. PENDING on a past day renders "Expired" (the #399 rule, via
 * [isInviteExpired]); resolved statuses keep their raw enum text exactly like the panel's
 * request rows. The panel carries no actions — a revoked/declined tap can never render a
 * stale actionable row.
 */
fun toReliefDayInviteRows(
    invites: List<ReliefInviteResponse>,
    today: LocalDate,
): List<ReliefDayInviteRow> =
    invites.map { invite ->
        ReliefDayInviteRow(
            title = invite.inviteeName.ifBlank { "A user" },
            statusText = if (isInviteExpired(invite, today)) "Expired" else invite.status.name,
        )
    }
