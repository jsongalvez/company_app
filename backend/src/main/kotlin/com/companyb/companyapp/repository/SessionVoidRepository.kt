package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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

object SessionVoidRepository {
    fun findById(id: UUID): SessionVoid? =
        transaction {
            findByIdInTransaction(id)
        }

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

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
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
        val created = insertedCount > 0

        val sessionVoid =
            findByIdInTransaction(id) ?: findBySessionIdInTransaction(sessionId)
                ?: error("session_void row not found after idempotent insert for $id")

        return VoidResult(sessionVoid, created)
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

    @Suppress("ReturnCount")
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
