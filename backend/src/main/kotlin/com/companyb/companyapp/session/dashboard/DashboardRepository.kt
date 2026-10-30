package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.Concern
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.Session
import com.companyb.companyapp.session.SessionConcernTable
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Slice
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

data class ClientNames(
    val firstName: String?,
    val lastName: String?,
)

data class SessionPractitionerWithName(
    val sessionId: UUID,
    val practitionerId: UUID,
    val displayName: String,
    val remarks: String?,
    val slotAtTime: Int,
)

data class ConcernWithSessionId(
    val sessionId: UUID,
    val concern: Concern,
)

internal object DashboardRepository {
    /**
     * Earlier unremitted days at [branchId] that still hold a Waiting session (feedback d6cc83cf):
     * one entered late or never marked. Today lists them so the branch marks them.
     */
    fun findEarlierWaitingDays(
        branchId: UUID,
        before: LocalDate,
    ): List<LocalDate> =
        transaction {
            SessionTable
                .innerJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
                .select(BranchDayTable.date)
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date less before) and
                        (BranchDayTable.status neq DayStatus.REMITTED) and
                        (SessionTable.sessionStatus eq SessionStatus.PENDING) and
                        (SessionTable.isVoided eq false)
                }.withDistinct()
                .orderBy(BranchDayTable.date to SortOrder.ASC)
                .map { it[BranchDayTable.date] }
        }

    fun findSessionsByBranchDay(branchDayId: UUID): List<Session> =
        transaction {
            SessionTable
                .selectAll()
                .where { SessionTable.branchDayId eq branchDayId }
                .orderBy(
                    SessionTable.bookedAt to SortOrder.ASC,
                    SessionTable.createdAt to SortOrder.ASC,
                ).map { it.toDashboardSession() }
        }

    fun findClientNames(clientIds: List<UUID>): Map<UUID, ClientNames> =
        if (clientIds.isEmpty()) {
            emptyMap()
        } else {
            transaction {
                Slice(ClientTable, listOf(ClientTable.id, ClientTable.firstName, ClientTable.lastName))
                    .selectAll()
                    .where { ClientTable.id inList clientIds }
                    .associate {
                        it[ClientTable.id] to
                            ClientNames(
                                firstName = it[ClientTable.firstName],
                                lastName = it[ClientTable.lastName],
                            )
                    }
            }
        }

    /** display names for requested-practitioner ids (dashboard + session detail enrichment). */
    fun findUserDisplayNames(userIds: List<UUID>): Map<UUID, String> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            transaction {
                Slice(AppUserTable, listOf(AppUserTable.id, AppUserTable.displayName))
                    .selectAll()
                    .where { AppUserTable.id inList userIds }
                    .associate { it[AppUserTable.id] to it[AppUserTable.displayName] }
            }
        }

    fun findVoidedSessionIds(sessionIds: List<UUID>): Set<UUID> =
        if (sessionIds.isEmpty()) {
            emptySet()
        } else {
            transaction {
                ActiveSessionVoidsView
                    .selectAll()
                    .where { ActiveSessionVoidsView.sessionId inList sessionIds }
                    .map { it[ActiveSessionVoidsView.sessionId] }
                    .toSet()
            }
        }

    fun findPractitioners(sessionIds: List<UUID>): List<SessionPractitionerWithName> =
        if (sessionIds.isEmpty()) {
            emptyList()
        } else {
            transaction {
                val join =
                    SessionPractitionerTable.innerJoin(
                        AppUserTable,
                        { SessionPractitionerTable.practitionerId },
                        { AppUserTable.id },
                    )
                Slice(
                    join,
                    listOf(
                        SessionPractitionerTable.sessionId,
                        SessionPractitionerTable.practitionerId,
                        AppUserTable.displayName,
                        SessionPractitionerTable.remarks,
                        SessionPractitionerTable.slotAtTime,
                    ),
                ).selectAll()
                    .where { SessionPractitionerTable.sessionId inList sessionIds }
                    .orderBy(SessionPractitionerTable.slotAtTime to SortOrder.ASC)
                    .map { row ->
                        SessionPractitionerWithName(
                            sessionId = row[SessionPractitionerTable.sessionId],
                            practitionerId = row[SessionPractitionerTable.practitionerId],
                            displayName = row[AppUserTable.displayName],
                            remarks = row[SessionPractitionerTable.remarks],
                            slotAtTime = row[SessionPractitionerTable.slotAtTime].toInt(),
                        )
                    }
            }
        }

    fun findConcernsForSessionIds(sessionIds: List<UUID>): List<ConcernWithSessionId> =
        if (sessionIds.isEmpty()) {
            emptyList()
        } else {
            transaction {
                ConcernTable
                    .join(
                        SessionConcernTable,
                        JoinType.INNER,
                        ConcernTable.id,
                        SessionConcernTable.concernId,
                    ).selectAll()
                    .where { SessionConcernTable.sessionId inList sessionIds }
                    .map { row ->
                        ConcernWithSessionId(
                            sessionId = row[SessionConcernTable.sessionId],
                            concern =
                                Concern(
                                    id = row[ConcernTable.id],
                                    label = row[ConcernTable.label],
                                    createdBy = row[ConcernTable.createdBy],
                                    createdAt = row[ConcernTable.createdAt],
                                ),
                        )
                    }
            }
        }

    // Read-projection row mapping for the dashboard list: the session store's
    // row mapper stays private to the session owner, so this projection owns its mapping.
    private fun org.jetbrains.exposed.v1.core.ResultRow.toDashboardSession(): Session =
        Session(
            id = this[SessionTable.id],
            clientId = this[SessionTable.clientId],
            branchDayId = this[SessionTable.branchDayId],
            requestedPractitionerId = this[SessionTable.requestedPractitionerId],
            sessionType = this[SessionTable.sessionType],
            isWalkIn = this[SessionTable.isWalkIn],
            sessionStatus = this[SessionTable.sessionStatus],
            basePrice = this[SessionTable.basePrice],
            finalPrice = this[SessionTable.finalPrice],
            remarks = this[SessionTable.remarks],
            otherConcerns = this[SessionTable.otherConcerns],
            bookedAt = this[SessionTable.bookedAt],
            nextAppointmentDate = this[SessionTable.nextAppointmentDate],
            createdBy = this[SessionTable.createdBy],
            createdAt = this[SessionTable.createdAt],
            version = this[SessionTable.version],
        )
}
