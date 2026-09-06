package com.companyb.companyapp.repository

import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

object DashboardRepository {
    fun findSessionsByBranchDay(branchDayId: UUID): List<Session> =
        transaction {
            SessionTable
                .selectAll()
                .where { SessionTable.branchDayId eq branchDayId }
                .orderBy(
                    SessionTable.bookedAt to SortOrder.ASC,
                    SessionTable.createdAt to SortOrder.ASC,
                ).map { it.toSession() }
        }

    fun findClientNames(clientIds: List<UUID>): Map<UUID, ClientNames> =
        if (clientIds.isEmpty()) {
            emptyMap()
        } else {
            transaction {
                ClientTable
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

    /** #366 — display names for requested-practitioner ids (dashboard + session detail enrichment). */
    fun findUserDisplayNames(userIds: List<UUID>): Map<UUID, String> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            transaction {
                AppUserTable
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
                join
                    .selectAll()
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
}
