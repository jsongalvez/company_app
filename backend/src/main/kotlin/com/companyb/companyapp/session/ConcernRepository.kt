package com.companyb.companyapp.session

import com.companyb.companyapp.exception.NotFoundException
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

data class PromoteConcernResult(
    val concern: Concern,
    val concernCreated: Boolean,
    val linkCreated: Boolean,
    val sessionBefore: Session,
    val sessionAfter: Session,
)

internal object ConcernRepository {
    fun findAll(): List<Concern> =
        transaction {
            ConcernTable
                .selectAll()
                .map { it.toConcern() }
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): Concern? =
        ConcernTable
            .selectAll()
            .where { ConcernTable.id eq id }
            .singleOrNull()
            ?.toConcern()

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun addToSessionInTransaction(
        sessionId: UUID,
        concernId: UUID,
    ): Boolean =
        SessionConcernTable
            .insertIgnore {
                it[SessionConcernTable.sessionId] = sessionId
                it[SessionConcernTable.concernId] = concernId
            }.insertedCount > 0

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun removeFromSessionInTransaction(
        sessionId: UUID,
        concernId: UUID,
    ): Boolean =
        SessionConcernTable
            .deleteWhere {
                (SessionConcernTable.sessionId eq sessionId) and
                    (SessionConcernTable.concernId eq concernId)
            } > 0

    fun getConcernsForSession(sessionId: UUID): List<Concern> =
        transaction {
            ConcernTable
                .join(
                    SessionConcernTable,
                    JoinType.INNER,
                    ConcernTable.id,
                    SessionConcernTable.concernId,
                ).selectAll()
                .where { SessionConcernTable.sessionId eq sessionId }
                .map { it.toConcern() }
        }

    /**
     * In-transaction store operation (#323, ADR-0024) — the concern insert, link insert, and
     * session other-concerns clear run on the caller's command transaction; the command writes
     * the three audit rows into that same transaction from [PromoteConcernResult].
     */
    fun promoteConcernInTransaction(
        concernId: UUID,
        label: String,
        createdBy: UUID,
        sessionId: UUID,
    ): PromoteConcernResult {
        val sessionBefore =
            SessionRepository.findByIdInTransaction(sessionId)
                ?: throw NotFoundException("Session not found")

        val insertedCount =
            ConcernTable
                .insertIgnore {
                    it[ConcernTable.id] = concernId
                    it[ConcernTable.label] = label
                    it[ConcernTable.createdBy] = createdBy
                }.insertedCount

        val concern =
            findByIdInTransaction(concernId)
                ?: throw NotFoundException("Concern not found after idempotent insert")

        val linkInserted =
            SessionConcernTable
                .insertIgnore {
                    it[SessionConcernTable.sessionId] = sessionId
                    it[SessionConcernTable.concernId] = concernId
                }.insertedCount > 0

        SessionTable.update({ SessionTable.id eq sessionId }) {
            it[SessionTable.otherConcerns] = null
        }

        val sessionAfter =
            SessionRepository.findByIdInTransaction(sessionId)
                ?: throw NotFoundException("Session not found after concern promotion update")

        return PromoteConcernResult(
            concern = concern,
            concernCreated = insertedCount > 0,
            linkCreated = linkInserted,
            sessionBefore = sessionBefore,
            sessionAfter = sessionAfter,
        )
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toConcern(): Concern =
        Concern(
            id = this[ConcernTable.id],
            label = this[ConcernTable.label],
            createdBy = this[ConcernTable.createdBy],
            createdAt = this[ConcernTable.createdAt],
        )
}
