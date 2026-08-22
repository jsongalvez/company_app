package com.companyb.companyapp.service

import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.ZonedDateTime

/**
 * #358 — unanswered relief requests announce themselves when their Branch Day ends
 * (#352 Q3, owner: "let carla know she had a relief duty request that day that expired").
 * Runs daily just after the 04:00 Manila day boundary.
 *
 * Request status deliberately stays PENDING — the invite precedent (#159 Q6): the
 * day-state IS the expiry, no status migration. Idempotency comes from the stored
 * notice itself: a request with an existing EXPIRED row is never announced twice,
 * so job re-runs and restarts are safe.
 */
object ReliefRequestExpiryJob {
    private val logger = KotlinLogging.logger {}

    fun run(clock: Clock = Clock.system(BranchDayService.manilaZone)): Int {
        val today = BranchDayService.currentOperationalDate(clock.instant())
        var announced = 0
        for (request in ReliefAccessRepository.findPendingWithPastDay(today)) {
            if (NotificationRepository.existsForSource(ReliefNotifications.EXPIRED, request.access.id)) continue
            announced +=
                ReliefNotifications.requestExpired(
                    requestId = request.access.id,
                    requesterId = request.access.requestedBy,
                    context =
                        ReliefEventContext(
                            branchId = request.branchId,
                            branchName = request.branchName,
                            date = request.date,
                        ),
                )
        }
        logger.info { "[RELIEF-EXPIRY] operationalDate=$today requests-announced=$announced" }
        return announced
    }

    fun nextRunDelayMs(now: ZonedDateTime): Long {
        // 04:05 Manila: five minutes past the boundary so the operational date has flipped.
        val next =
            now
                .withHour(EXPIRY_HOUR)
                .withMinute(EXPIRY_MINUTE)
                .withSecond(0)
                .withNano(0)
                .let { if (it.isBefore(now)) it.plusDays(1) else it }
        return java.time.Duration
            .between(now, next)
            .toMillis()
            .coerceAtLeast(0)
    }

    private const val EXPIRY_HOUR = 4
    private const val EXPIRY_MINUTE = 5
}
