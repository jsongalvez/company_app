package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.domain.OperationalDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The current operational business date in Asia/Manila (rolls over at 04:00 local).
 *
 * #160 owns pure relief presentation logic; #399 extends it: the client's "today" is the
 * operational date, not the calendar one — delegating to the shared [OperationalDay] rule
 * (#875) so client and server can never fork "today" in the 00:00–04:00 window.
 * Calendar-midnight comparisons marked 00:00–03:59 rows expired while their branch day was
 * still OPEN.
 */
fun currentOperationalDate(at: Instant): LocalDate {
    val manilaNow = at.toLocalDateTime(TimeZone.of(OperationalDay.MANILA_ZONE_ID))
    val epochDay = OperationalDay.operationalEpochDay(manilaNow.hour, manilaNow.date.toEpochDays())
    return LocalDate.fromEpochDays(epochDay.toInt())
}

/** Convenience overload resolving [currentOperationalDate] at the current instant. */
fun currentOperationalDate(): LocalDate = currentOperationalDate(Clock.System.now())

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

/**
 * #666 — the deep-link panel's request-row label: the requester display name when the
 * day read populated it, falling back to the raw id (mine list / legacy rows carry null).
 */
fun reliefRequestRowLabel(row: ReliefAccessResponse): String = row.requesterName ?: row.requestedBy

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
