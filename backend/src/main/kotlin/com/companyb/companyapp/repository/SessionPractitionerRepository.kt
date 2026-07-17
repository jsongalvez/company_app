package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
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
        changedBy: UUID,
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
                incrementSessionVersion(sessionId)
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
                AuditLogRepository.record(
                    tableName = SessionPractitionerTable.tableName,
                    recordId = practitioner.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "practitionerId" to practitionerId.toString(),
                            "slotAtTime" to slotAtTime.toString(),
                        ),
                )
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
        changedBy: UUID,
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
                incrementSessionVersion(sessionId)

                val practitioner =
                    SessionPractitionerTable
                        .selectAll()
                        .where {
                            (SessionPractitionerTable.sessionId eq sessionId) and
                                (SessionPractitionerTable.practitionerId eq practitionerId)
                        }.single()
                        .toSessionPractitioner()

                AuditLogRepository.record(
                    tableName = SessionPractitionerTable.tableName,
                    recordId = practitioner.id,
                    action = AuditAction.UPDATE,
                    changedBy = changedBy,
                    newValue = AuditLogRepository.jsonField("remarks", remarks ?: ""),
                )
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
        changedBy: UUID,
    ): Boolean =
        transaction {
            val deleted =
                SessionPractitionerTable.deleteWhere {
                    (SessionPractitionerTable.sessionId eq sessionId) and
                        (SessionPractitionerTable.practitionerId eq practitionerId)
                }

            if (deleted > 0) {
                incrementSessionVersion(sessionId)

                AuditLogRepository.record(
                    tableName = SessionPractitionerTable.tableName,
                    recordId = practitionerId,
                    action = AuditAction.DELETE,
                    changedBy = changedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "practitionerId" to practitionerId.toString(),
                        ),
                )
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

    private fun incrementSessionVersion(sessionId: UUID) {
        val currentVersion =
            SessionTable
                .select(SessionTable.version)
                .where { SessionTable.id eq sessionId }
                .single()[SessionTable.version]
        SessionTable.update({ SessionTable.id eq sessionId }) {
            it[SessionTable.version] = currentVersion + 1
        }
    }
}
