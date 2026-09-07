package com.companyb.companyapp.session

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.VersionMismatchException
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

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
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

        if (created) {
            val sessionVersion = readSessionVersion(sessionId)
            incrementSessionVersion(sessionId, sessionVersion)
        }

        val practitioner =
            findBySessionAndPractitionerInTransaction(sessionId, practitionerId)
                ?: throw ConflictException("Session practitioner request ID already exists")

        return AddPractitionerResult(practitioner, created)
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — updates remarks and bumps the parent
     * session version on the caller's command transaction; a lost update race throws before any
     * audit can be written.
     */
    fun updateRemarksInTransaction(
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
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

        val sessionVersion = readSessionVersion(sessionId)
        incrementSessionVersion(sessionId, sessionVersion)

        return SessionPractitionerTable
            .selectAll()
            .where {
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }.single()
            .toSessionPractitioner()
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — returns the removed row for the
     * command's delete audit, or null when nothing was deleted.
     */
    fun removeInTransaction(
        sessionId: UUID,
        practitionerId: UUID,
    ): SessionPractitioner? {
        val existing = findBySessionAndPractitionerInTransaction(sessionId, practitionerId)

        val deleted =
            SessionPractitionerTable.deleteWhere {
                (SessionPractitionerTable.sessionId eq sessionId) and
                    (SessionPractitionerTable.practitionerId eq practitionerId)
            }

        if (deleted > 0) {
            val sessionVersion = readSessionVersion(sessionId)
            incrementSessionVersion(sessionId, sessionVersion)
            return existing
        }
        if (existing != null) {
            throw VersionMismatchException(SessionPractitionerTable.tableName, existing.id)
        }
        return null
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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionPractitioner(): SessionPractitioner =
        SessionPractitioner(
            id = this[SessionPractitionerTable.id],
            sessionId = this[SessionPractitionerTable.sessionId],
            practitionerId = this[SessionPractitionerTable.practitionerId],
            remarks = this[SessionPractitionerTable.remarks],
            slotAtTime = this[SessionPractitionerTable.slotAtTime],
        )

    internal fun incrementSessionVersion(
        sessionId: UUID,
        expectedVersion: Int,
    ) {
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
