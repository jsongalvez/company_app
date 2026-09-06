package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * #359 — accepted-invite reminders, daily 07:00 Manila sweep (same slot as the appointment
 * scheduler; the owner left times unspecified and the existing pattern is the default).
 * An ACCEPTED invite is reminded at T-3 days, T-1 day, and the duty day itself; only the
 * invitee hears it (#352 rulings — branch quiet until the day).
 *
 * Re-run safety mirrors the #358 expiry job: each reminder slot is its own occurrence key,
 * so the UNIQUE (dedup_key, user_id) constraint (#508) — not a pre-write marker read —
 * keeps re-runs and restarts from duplicating. Suppression still reads current state:
 * non-ACCEPTED invites never match the scan, and on the duty day an invitee who already
 * clocked in gets nothing.
 */
object ReliefInviteReminderJob {
    private val logger = KotlinLogging.logger {}

    fun run(clock: Clock = Clock.system(BranchDayService.manilaZone)): Int {
        val today = BranchDayService.currentOperationalDate(clock.instant())
        var sent = 0
        for ((dutyDate, eventType) in reminderSlots(today)) {
            for (invite in ReliefInviteRepository.findAcceptedForDutyDate(dutyDate)) {
                val showedUp =
                    eventType == ReliefNotifications.REMINDER_DAY_OF &&
                        ReliefAccessRepository.hasActiveClockIn(invite.invitee, invite.branchDayId)
                if (showedUp) continue
                sent +=
                    ReliefNotifications.inviteReminder(
                        eventType = eventType,
                        inviteId = invite.inviteId,
                        inviteeId = invite.invitee,
                        context =
                            ReliefEventContext(
                                branchId = invite.branchId,
                                branchName = invite.branchName,
                                date = invite.date,
                            ),
                    )
            }
        }
        logger.info { "[RELIEF-REMINDER] operationalDate=$today reminders-sent=$sent" }
        return sent
    }

    fun nextRunDelayMs(now: ZonedDateTime): Long {
        val nextRun = now.with(LocalTime.of(RUN_HOUR, RUN_MINUTE))
        val adjusted = if (nextRun.isBefore(now) || nextRun == now) nextRun.plusDays(1) else nextRun
        return Duration.between(now, adjusted).toMillis()
    }

    /** Duty date → reminder event: +3 days, +1 day, today. */
    private fun reminderSlots(today: LocalDate): List<Pair<LocalDate, String>> =
        listOf(
            Pair(today.plusDays(LEAD_DAYS_3), ReliefNotifications.REMINDER_3_DAYS),
            Pair(today.plusDays(LEAD_DAYS_1), ReliefNotifications.REMINDER_1_DAY),
            Pair(today, ReliefNotifications.REMINDER_DAY_OF),
        )

    private const val RUN_HOUR = 7
    private const val RUN_MINUTE = 0
    private const val LEAD_DAYS_3 = 3L
    private const val LEAD_DAYS_1 = 1L
}
