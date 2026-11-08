package com.companyb.companyapp.notification

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * Read store for the next-appointment notification sweep: every `*Table`/view join
 * lives behind this internal repository; NextAppointmentScheduler keeps scheduling policy and
 * the notification write (via NotificationRepository). Reads are `*InTransaction` store
 * operations on the caller's transaction; [findActiveCoordinatorsForBranches] keeps a
 * self-contained wrapper for direct reads.
 */
internal object NextAppointmentRepository {
    data class UpcomingSession(
        val sessionId: UUID,
        val branchId: UUID,
    )

    fun findUpcomingSessionsInTransaction(targetDate: LocalDate): List<UpcomingSession> =
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

    fun findActiveCoordinatorsForBranches(branchIds: Collection<UUID>): Map<UUID, List<UUID>> =
        traced("NextAppointmentRepository.findActiveCoordinatorsForBranches") {
            transaction {
                findActiveCoordinatorsForBranchesInTransaction(branchIds)
            }
        }

    fun findActiveCoordinatorsForBranchesInTransaction(branchIds: Collection<UUID>): Map<UUID, List<UUID>> =
        CapabilityService.alertedByBranchInTransaction(branchIds)
}
