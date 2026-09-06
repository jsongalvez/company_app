package com.companyb.companyapp.service

import com.companyb.companyapp.authorization.ActiveUserCapabilitiesView
import com.companyb.companyapp.authorization.CapabilityTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * Read store for the next-appointment notification sweep (#324): every `*Table`/view join
 * lives behind this internal repository; NextAppointmentScheduler keeps scheduling policy and
 * the notification write (via NotificationRepository). Each read owns its own transaction.
 */
internal object NextAppointmentRepository {
    data class UpcomingSession(
        val sessionId: UUID,
        val branchId: UUID,
    )

    fun findUpcomingSessions(targetDate: LocalDate): List<UpcomingSession> =
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

    fun findActiveCoordinatorsForBranches(branchIds: Collection<UUID>): Map<UUID, List<UUID>> =
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
                        (ActiveUserCapabilitiesView.contextId eq UserBranchAssignmentTable.branchId) and
                        (CapabilityTable.code eq CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS)
                }.withDistinct()
                .map { row ->
                    row[UserBranchAssignmentTable.branchId] to row[UserBranchAssignmentTable.userId]
                }.groupBy({ it.first }, { it.second })
        }
}
