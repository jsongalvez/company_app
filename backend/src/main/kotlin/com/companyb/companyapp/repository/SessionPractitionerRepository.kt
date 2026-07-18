package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class AddPractitionerResult(
    val practitioner: SessionPractitioner,
    val created: Boolean,
)

object SessionPractitionerRepository {
    @Suppress("LongParameterList")
    fun add(
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        slotAtTime: Short,
        remarks: String?,
        auditFn: (SessionPractitioner) -> Unit = {},
    ): AddPractitionerResult =
        transaction {
            val insertedCount =
                SessionPractitionerTable
                    .insertIgnore {
                        it[SessionPractitionerTable.id] = id
                        it[SessionPractitionerTable.sessionId] = sessionId
                        it[SessionPractitionerTable.practitionerId] = practitionerId
                        it[SessionPractitionerTable.slotAtTime] = slotAtTime
                        if (remarks != null) it[SessionPractitionerTable.remarks] = remarks
                    }.insertedCount
            val created = insertedCount > 0

            if (created) {
                val sessionVersion = readSessionVersion(sessionId)
                incrementSessionVersion(sessionId, sessionVersion)
            }

            val practitioner =
                SessionPractitionerTable
                    .selectAll()
                    .where {
                        (SessionPractitionerTable.sessionId eq sessionId) and
                            (SessionPractitionerTable.practitionerId eq practitionerId)
                    }.single()
                    .toSessionPractitioner()

            if (created) {
                auditFn(practitioner)
                logger.info {
                    "[ADD-PRACTITIONER] Added practitioner $practitionerId to session $sessionId slot=$slotAtTime"
                }
            } else {
                logger.info {
                    "[ADD-PRACTITIONER] Practitioner $practitionerId already in session $sessionId (idempotent)"
                }
            }

            AddPractitionerResult(practitioner, created)
        }

    fun updateRemarks(
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        auditFn: (SessionPractitioner) -> Unit = {},
    ): SessionPractitioner? =
        transaction {
            val updated =
                SessionPractitionerTable.update({
                    (SessionPractitionerTable.sessionId eq sessionId) and
                        (SessionPractitionerTable.practitionerId eq practitionerId)
                }) {
                    it[SessionPractitionerTable.remarks] = remarks
                }

            if (updated > 0) {
                val sessionVersion = readSessionVersion(sessionId)
                incrementSessionVersion(sessionId, sessionVersion)

                val practitioner =
                    SessionPractitionerTable
                        .selectAll()
                        .where {
                            (SessionPractitionerTable.sessionId eq sessionId) and
                                (SessionPractitionerTable.practitionerId eq practitionerId)
                        }.single()
                        .toSessionPractitioner()

                auditFn(practitioner)
                logger.info {
                    "[UPDATE-PRACTITIONER-REMARKS] Updated remarks for " +
                        "practitioner $practitionerId in session $sessionId"
                }
                practitioner
            } else {
                null
            }
        }

    fun remove(
        sessionId: UUID,
        practitionerId: UUID,
        auditFn: (SessionPractitioner) -> Unit = {},
    ): Boolean =
        transaction {
            val existing =
                SessionPractitionerTable
                    .selectAll()
                    .where {
                        (SessionPractitionerTable.sessionId eq sessionId) and
                            (SessionPractitionerTable.practitionerId eq practitionerId)
                    }.singleOrNull()
                    ?.toSessionPractitioner()

            val deleted =
                SessionPractitionerTable.deleteWhere {
                    (SessionPractitionerTable.sessionId eq sessionId) and
                        (SessionPractitionerTable.practitionerId eq practitionerId)
                }

            if (deleted > 0) {
                val sessionVersion = readSessionVersion(sessionId)
                incrementSessionVersion(sessionId, sessionVersion)

                if (existing != null) {
                    auditFn(existing)
                }
                logger.info { "[REMOVE-PRACTITIONER] Removed practitioner $practitionerId from session $sessionId" }
            }

            deleted > 0
        }

    fun findBySessionId(sessionId: UUID): List<SessionPractitioner> =
        transaction {
            SessionPractitionerTable
                .selectAll()
                .where { SessionPractitionerTable.sessionId eq sessionId }
                .orderBy(SessionPractitionerTable.slotAtTime)
                .map { it.toSessionPractitioner() }
        }.also {
            logger.info { "[FIND-PRACTITIONERS-BY-SESSION] Found ${it.size} practitioners for session $sessionId" }
        }

    fun findBySessionAndPractitioner(
        sessionId: UUID,
        practitionerId: UUID,
    ): SessionPractitioner? =
        transaction {
            SessionPractitionerTable
                .selectAll()
                .where {
                    (SessionPractitionerTable.sessionId eq sessionId) and
                        (SessionPractitionerTable.practitionerId eq practitionerId)
                }.singleOrNull()
                ?.toSessionPractitioner()
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionPractitioner(): SessionPractitioner =
        SessionPractitioner(
            id = this[SessionPractitionerTable.id],
            sessionId = this[SessionPractitionerTable.sessionId],
            practitionerId = this[SessionPractitionerTable.practitionerId],
            remarks = this[SessionPractitionerTable.remarks],
            slotAtTime = this[SessionPractitionerTable.slotAtTime],
        )

    private fun incrementSessionVersion(
        sessionId: UUID,
        expectedVersion: Int,
    ) {
        val updated =
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.version] = expectedVersion + 1
            }
        check(updated > 0) { "Session version changed concurrently" }
    }

    private fun readSessionVersion(sessionId: UUID): Int =
        SessionTable
            .select(SessionTable.version)
            .where { SessionTable.id eq sessionId }
            .single()[SessionTable.version]
}
