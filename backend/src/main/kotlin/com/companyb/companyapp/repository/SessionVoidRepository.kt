package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

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
            SessionVoidTable
                .selectAll()
                .where { SessionVoidTable.sessionId eq sessionId }
                .singleOrNull()
                ?.toSessionVoid()
        }

    fun void(
        id: UUID,
        sessionId: UUID,
        voidReason: String,
        voidedBy: UUID,
        auditFn: (SessionVoid) -> Unit = {},
    ): VoidResult =
        transaction {
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
                findByIdInTransaction(id) ?: findBySessionId(sessionId)
                    ?: error("session_void row not found after idempotent insert for $id")

            if (created) {
                auditFn(sessionVoid)
            }
            VoidResult(sessionVoid, created)
        }.also {
            logger.info {
                "[VOID-SESSION] SessionVoid ${it.sessionVoid.id} for session $sessionId created=${it.created}"
            }
        }

    fun unvoid(
        sessionVoidId: UUID,
        unvoidedBy: UUID,
        unvoidedReason: String,
        auditFn: (SessionVoid) -> Unit = {},
    ): SessionVoid? =
        transaction {
            SessionVoidTable.update({
                (SessionVoidTable.id eq sessionVoidId) and (SessionVoidTable.unvoidedAt.isNull())
            }) {
                it[SessionVoidTable.unvoidedAt] = CurrentTimestampWithTimeZone
                it[SessionVoidTable.unvoidedBy] = unvoidedBy
                it[SessionVoidTable.unvoidedReason] = unvoidedReason
            }

            val sessionVoid = findByIdInTransaction(sessionVoidId)

            if (sessionVoid != null) {
                auditFn(sessionVoid)
            }

            sessionVoid
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
