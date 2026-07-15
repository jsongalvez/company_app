package com.companyb.companyapp.service

import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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

        var created = 0
        for (session in sessions) {
            val coordinatorIds = findActiveCoordinatorsForBranch(session.branchId)
            for (userId in coordinatorIds) {
                val exists = notificationExists(session.sessionId, userId)
                if (!exists) {
                    NotificationRepository.insert(session.sessionId, userId, session.branchId)
                    created++
                }
            }
        }

        logger.info { "[SCHEDULER] Created $created notifications for $target (${sessions.size} sessions)" }
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

    internal fun findActiveCoordinatorsForBranch(branchId: UUID): List<UUID> =
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
                ).where {
                    (UserBranchAssignmentTable.branchId eq branchId) and
                        (UserBranchAssignmentTable.endedAt.isNull()) and
                        (ActiveUserCapabilitiesView.contextType eq CapabilityContextType.BRANCH) and
                        (ActiveUserCapabilitiesView.contextId eq branchId) and
                        (CapabilityTable.code eq RECEIVE_NEXT_APPOINTMENT_ALERTS)
                }.withDistinct()
                .map { it[UserBranchAssignmentTable.userId] }
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
