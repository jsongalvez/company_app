package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.ReliefInviteResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Pure relief-invite presentation logic (#160) — date parsing, expiry and actionability.
 * Day-state is the invite's expiry (#159 Q6): a PENDING invite whose day is past renders
 * "expired" (no actions, no cron); the badge counts only actionable (PENDING, non-expired)
 * invites.
 */
fun manilaToday(): LocalDate =
    Clock.System
        .now()
        .toLocalDateTime(TimeZone.of("Asia/Manila"))
        .date

fun parseInviteDate(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

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
