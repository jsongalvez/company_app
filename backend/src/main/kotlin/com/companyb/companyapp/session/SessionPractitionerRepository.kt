package com.companyb.companyapp.session

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.observability.traced
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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

internal object SessionPractitionerRepository {
    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findBySessionAndPractitionerInTransaction(
        sessionId: UUID,
        practitionerId: UUID,
    ): SessionPractitioner? =
        SessionPractitionerTable
            .selectAll()
            .where {
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }.singleOrNull()
            ?.toSessionPractitioner()

    fun addInTransaction(
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        slotAtTime: Short,
        remarks: String?,
    ): AddPractitionerResult {
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

        val practitioner =
            findBySessionAndPractitionerInTransaction(sessionId, practitionerId)
                ?: throw ConflictException("Session practitioner request ID already exists")

        return AddPractitionerResult(practitioner, created)
    }

    /**
     * In-transaction store operation (ADR-0024) — updates remarks and bumps the parent
     * session version on the caller's command transaction; a lost update race throws before any
     * audit can be written.
     */
    fun updateRemarksInTransaction(
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        version: Int? = null,
    ): SessionPractitioner {
        val updated =
            SessionPractitionerTable.update({
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }) {
                it[SessionPractitionerTable.remarks] = remarks
            }

        if (updated != 1) {
            throw VersionMismatchException(SessionPractitionerTable.tableName, practitionerId)
        }

        incrementSessionVersion(sessionId, version)

        return SessionPractitionerTable
            .selectAll()
            .where {
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }.single()
            .toSessionPractitioner()
    }

    /**
     * In-transaction store operation (ADR-0024) — returns the removed row for the
     * command's delete audit, or null when nothing was deleted.
     */
    fun removeInTransaction(
        sessionId: UUID,
        practitionerId: UUID,
        version: Int? = null,
    ): SessionPractitioner? {
        val existing = findBySessionAndPractitionerInTransaction(sessionId, practitionerId)

        val deleted =
            SessionPractitionerTable.deleteWhere {
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }

        if (deleted > 0) {
            incrementSessionVersion(sessionId, version)
            return existing
        }
        if (existing != null) {
            throw VersionMismatchException(SessionPractitionerTable.tableName, existing.id)
        }
        return null
    }

    fun findBySessionId(sessionId: UUID): List<SessionPractitioner> =
        traced("SessionPractitionerRepository.findBySessionId") {
            transaction {
                SessionPractitionerTable
                    .selectAll()
                    .where { SessionPractitionerTable.sessionId eq sessionId }
                    .orderBy(SessionPractitionerTable.slotAtTime)
                    .map { it.toSessionPractitioner() }
            }.also {
                logger.info { "[FIND-PRACTITIONERS-BY-SESSION] Found ${it.size} practitioners for session $sessionId" }
            }
        }

    /**
     * In-transaction store read — practitioner-row ids attached to the
     * given sessions, for the anonymize audit redaction.
     */
    fun findIdsForSessionsInTransaction(sessionIds: Collection<UUID>): List<UUID> =
        if (sessionIds.isEmpty()) {
            emptyList()
        } else {
            SessionPractitionerTable
                .select(SessionPractitionerTable.id)
                .where { SessionPractitionerTable.sessionId inList sessionIds }
                .map { it[SessionPractitionerTable.id] }
        }

    /**
     * In-transaction store operation — nulls practitioner remarks on
     * the given sessions. No new events:
     * the client's anonymization event is the command's audit.
     */
    fun scrubRemarksForSessionsInTransaction(sessionIds: Collection<UUID>): Int =
        if (sessionIds.isEmpty()) {
            0
        } else {
            SessionPractitionerTable.update({ SessionPractitionerTable.sessionId inList sessionIds }) {
                it[SessionPractitionerTable.remarks] = null
            }
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionPractitioner(): SessionPractitioner =
        SessionPractitioner(
            id = this[SessionPractitionerTable.id],
            sessionId = this[SessionPractitionerTable.sessionId],
            practitionerId = this[SessionPractitionerTable.practitionerId],
            remarks = this[SessionPractitionerTable.remarks],
            slotAtTime = this[SessionPractitionerTable.slotAtTime],
        )

    /**
     * Moves the session one version on from [madeFrom], the version the save was made from;
     * a session changed since is refused. Null moves it on from whatever it holds.
     */
    internal fun incrementSessionVersion(
        sessionId: UUID,
        madeFrom: Int?,
    ) {
        val expectedVersion = madeFrom ?: readSessionVersion(sessionId)
        val updated =
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.version] = expectedVersion + 1
            }
        if (updated != 1) {
            throw VersionMismatchException(SessionTable.tableName, sessionId)
        }
    }

    private fun readSessionVersion(sessionId: UUID): Int =
        SessionTable
            .select(SessionTable.version)
            .where { SessionTable.id eq sessionId }
            .single()[SessionTable.version]
}
