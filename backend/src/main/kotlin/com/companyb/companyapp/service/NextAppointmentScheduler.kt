package com.companyb.companyapp.service

import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Daily next-appointment notification sweep (#322 time ownership, #324 boundaries). The run
 * date is the current **operational** date from the Branch Day authority — never a locally
 * derived calendar date; queries live behind [NextAppointmentRepository], this scheduler owns
 * no persistence or time policy of its own.
 */
object NextAppointmentScheduler {
    private val logger = KotlinLogging.logger {}
    private const val RUN_HOUR = 7
    private const val RUN_MINUTE = 0
    private const val DAYS_AHEAD = 2L

    fun targetDate(now: LocalDate): LocalDate = now.plusDays(DAYS_AHEAD)

    fun nextRunDelayMs(now: ZonedDateTime): Long {
        var nextRun = now.with(LocalTime.of(RUN_HOUR, RUN_MINUTE))
        if (nextRun.isBefore(now) || nextRun == now) {
            nextRun = nextRun.plusDays(1)
        }
        return Duration.between(now, nextRun).toMillis()
    }

    fun run(clock: Clock = Clock.system(BranchDayService.manilaZone)): Int {
        val now = ZonedDateTime.now(clock)
        val target = targetDate(BranchDayService.currentOperationalDate(now.toInstant()))
        val sessions = NextAppointmentRepository.findUpcomingSessions(target)

        if (sessions.isEmpty()) {
            logger.info { "[SCHEDULER] No upcoming appointment sessions for $target" }
            return 0
        }

        val branchIds = sessions.map { it.branchId }.distinct()
        val coordinatorsByBranch =
            NextAppointmentRepository.findActiveCoordinatorsForBranches(branchIds)

        val message = "You have an upcoming appointment on $target"

        val candidates =
            sessions.flatMap { session ->
                coordinatorsByBranch[session.branchId].orEmpty().map { userId ->
                    NotificationCreateParams(
                        sessionId = session.sessionId,
                        userId = userId,
                        branchId = session.branchId,
                        message = message,
                    )
                }
            }
        val created = NotificationRepository.insertBatch(candidates)

        logger.info {
            "[SCHEDULER] Created $created notifications for $target " +
                "(${sessions.size} sessions, ${branchIds.size} branches)"
        }
        return created
    }
}
