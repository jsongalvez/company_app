package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object NextAppointmentScheduler {
    private val logger = KotlinLogging.logger {}
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private const val RECEIVE_NEXT_APPOINTMENT_ALERTS = "RECEIVE_NEXT_APPOINTMENT_ALERTS"
    private const val RUN_HOUR = 7
    private const val RUN_MINUTE = 0
    private const val DAYS_AHEAD = 2L

    data class UpcomingSession(
        val sessionId: UUID,
        val branchId: UUID,
    )

    fun targetDate(now: LocalDate): LocalDate = now.plusDays(DAYS_AHEAD)

    fun nextRunDelayMs(now: ZonedDateTime): Long {
        var nextRun = now.with(LocalTime.of(RUN_HOUR, RUN_MINUTE))
        if (nextRun.isBefore(now) || nextRun == now) {
            nextRun = nextRun.plusDays(1)
        }
        return Duration.between(now, nextRun).toMillis()
    }

    fun run(clock: Clock = Clock.system(manilaZone)): Int {
        val now = ZonedDateTime.now(clock)
        val target = targetDate(now.toLocalDate())
        val sessions = findUpcomingSessions(target)

        if (sessions.isEmpty()) {
            logger.info { "[SCHEDULER] No upcoming appointment sessions for $target" }
            return 0
        }

        val branchIds = sessions.map { it.branchId }.distinct()
        val coordinatorsByBranch = findActiveCoordinatorsForBranches(branchIds)

        val message = "You have an upcoming appointment on $target"

        var created = 0
        for (session in sessions) {
            val coordinatorIds = coordinatorsByBranch[session.branchId].orEmpty()
            for (userId in coordinatorIds) {
                val exists = notificationExists(session.sessionId, userId)
                if (!exists) {
                    NotificationRepository.insert(
                        NotificationCreateParams(
                            sessionId = session.sessionId,
                            userId = userId,
                            branchId = session.branchId,
                            message = message,
                        ),
                    )
                    created++
                }
            }
        }

        logger.info {
            "[SCHEDULER] Created $created notifications for $target " +
                "(${sessions.size} sessions, ${branchIds.size} branches)"
        }
        return created
    }

    internal fun findUpcomingSessions(targetDate: LocalDate): List<UpcomingSession> =
        transaction {
            SessionTable
                .leftJoin(
                    ActiveSessionVoidsView,
                    { SessionTable.id },
                    { ActiveSessionVoidsView.sessionId },
                ).join(
                    BranchDayTable,
                    JoinType.INNER,
                    SessionTable.branchDayId,
                    BranchDayTable.id,
                ).selectAll()
                .where {
                    (SessionTable.sessionStatus eq SessionStatus.COMPLETED) and
                        (SessionTable.nextAppointmentDate eq targetDate) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.map { row ->
                    UpcomingSession(
                        sessionId = row[SessionTable.id],
                        branchId = row[BranchDayTable.branchId],
                    )
                }
        }

    internal fun findActiveCoordinatorsForBranches(branchIds: Collection<UUID>): Map<UUID, List<UUID>> =
        transaction {
            UserBranchAssignmentTable
                .innerJoin(
                    ActiveUserCapabilitiesView,
                    { UserBranchAssignmentTable.userId },
                    { ActiveUserCapabilitiesView.userId },
                ).innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).select(
                    UserBranchAssignmentTable.userId,
                    UserBranchAssignmentTable.branchId,
                ).where {
                    (UserBranchAssignmentTable.branchId inList branchIds) and
                        (UserBranchAssignmentTable.endedAt.isNull()) and
                        (ActiveUserCapabilitiesView.contextType eq CapabilityContextType.BRANCH) and
                        (ActiveUserCapabilitiesView.contextId inList branchIds) and
                        (CapabilityTable.code eq RECEIVE_NEXT_APPOINTMENT_ALERTS)
                }.withDistinct()
                .map { row ->
                    row[UserBranchAssignmentTable.branchId] to row[UserBranchAssignmentTable.userId]
                }.groupBy({ it.first }, { it.second })
        }

    private fun notificationExists(
        sessionId: UUID,
        userId: UUID,
    ): Boolean =
        transaction {
            NotificationTable
                .selectAll()
                .where {
                    (NotificationTable.sessionId eq sessionId) and
                        (NotificationTable.userId eq userId)
                }.empty()
                .not()
        }
}
