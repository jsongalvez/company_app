package com.companyb.companyapp.session

import com.companyb.companyapp.exception.ConflictException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

data class VoidResult(
    val sessionVoid: SessionVoid,
    val created: Boolean,
)

internal object SessionVoidRepository {
    fun findBySessionId(sessionId: UUID): SessionVoid? =
        transaction {
            findBySessionIdInTransaction(sessionId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findBySessionIdInTransaction(sessionId: UUID): SessionVoid? =
        SessionVoidTable
            .selectAll()
            .where { SessionVoidTable.sessionId eq sessionId }
            .singleOrNull()
            ?.toSessionVoid()

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction.
     *
     * Ownership-validated replay with re-arm (#514): the insert spans two unique keys (PK
     * `id`, UNIQUE `session_id`), so a swallowed insert is disambiguated — a row owned by
     * another session fails closed (the #511/#512 `validateReplayOwnership` precedent), a
     * historical unvoided row for this session is re-armed in place (the single
     * per-session row invariant), and an active row for this session is an idempotent ack.
     */
    fun voidInTransaction(
        id: UUID,
        sessionId: UUID,
        voidReason: String,
        voidedBy: UUID,
    ): VoidResult {
        val insertedCount =
            SessionVoidTable
                .insertIgnore {
                    it[SessionVoidTable.id] = id
                    it[SessionVoidTable.sessionId] = sessionId
                    it[SessionVoidTable.voidReason] = voidReason
                    it[SessionVoidTable.voidedBy] = voidedBy
                }.insertedCount
        if (insertedCount > 0) {
            return VoidResult(
                findByIdInTransaction(id) ?: error("session_void row not found after idempotent insert for $id"),
                created = true,
            )
        }

        val byId = findByIdInTransaction(id)
        if (byId != null && byId.sessionId != sessionId) {
            throw ConflictException("Void id already belongs to another void request")
        }
        val existing =
            findBySessionIdInTransaction(sessionId)
                ?: byId
                ?: error("session_void row not found after idempotent insert for $id")
        if (existing.unvoidedAt == null) {
            return VoidResult(existing, created = false)
        }

        // Re-void after unvoid: re-arm the single per-session row (fresh caller id is
        // discarded — the row id is stable). The conditional update joins the losers of a
        // concurrent re-void race into an idempotent ack instead of a second audit row.
        val rearmed =
            SessionVoidTable.update({
                (SessionVoidTable.id eq existing.id) and (SessionVoidTable.unvoidedAt.isNotNull())
            }) {
                it[SessionVoidTable.voidedAt] = CurrentTimestampWithTimeZone
                it[SessionVoidTable.voidedBy] = voidedBy
                it[SessionVoidTable.voidReason] = voidReason
                it[SessionVoidTable.unvoidedAt] = null
                it[SessionVoidTable.unvoidedBy] = null
                it[SessionVoidTable.unvoidedReason] = null
            }
        val row =
            findBySessionIdInTransaction(sessionId)
                ?: error("session_void row not found after re-void for $sessionId")
        return VoidResult(row, created = rearmed > 0)
    }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun unvoidInTransaction(
        sessionVoidId: UUID,
        unvoidedBy: UUID,
        unvoidedReason: String,
    ): SessionVoid? {
        SessionVoidTable.update({
            (SessionVoidTable.id eq sessionVoidId) and (SessionVoidTable.unvoidedAt.isNull())
        }) {
            it[SessionVoidTable.unvoidedAt] = CurrentTimestampWithTimeZone
            it[SessionVoidTable.unvoidedBy] = unvoidedBy
            it[SessionVoidTable.unvoidedReason] = unvoidedReason
        }

        return findByIdInTransaction(sessionVoidId)
    }

    private fun findByIdInTransaction(id: UUID): SessionVoid? =
        SessionVoidTable
            .selectAll()
            .where { SessionVoidTable.id eq id }
            .singleOrNull()
            ?.toSessionVoid()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionVoid(): SessionVoid {
        val unvoidedAt = this[SessionVoidTable.unvoidedAt]
        val unvoidedBy = this[SessionVoidTable.unvoidedBy]
        val unvoidedReason = this[SessionVoidTable.unvoidedReason]
        return SessionVoid(
            id = this[SessionVoidTable.id],
            sessionId = this[SessionVoidTable.sessionId],
            voidedAt = this[SessionVoidTable.voidedAt],
            voidedBy = this[SessionVoidTable.voidedBy],
            voidReason = this[SessionVoidTable.voidReason],
            unvoidedAt = unvoidedAt,
            unvoidedBy = unvoidedBy,
            unvoidedReason = unvoidedReason,
        )
    }
}
