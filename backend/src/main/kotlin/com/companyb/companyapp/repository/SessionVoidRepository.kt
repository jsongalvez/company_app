package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
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

    @Suppress("LongParameterList")
    fun void(
        id: UUID,
        sessionId: UUID,
        voidReason: String,
        voidedBy: UUID,
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
                AuditLogRepository.record(
                    tableName = SessionVoidTable.tableName,
                    recordId = sessionVoid.id,
                    action = AuditAction.INSERT,
                    changedBy = voidedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to sessionVoid.id.toString(),
                            "sessionId" to sessionId.toString(),
                            "voidReason" to voidReason,
                        ),
                )
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
    ): SessionVoid? =
        transaction {
            SessionVoidTable.update({
                (SessionVoidTable.id eq sessionVoidId) and (SessionVoidTable.unvoidedAt.isNull())
            }) {
                it[SessionVoidTable.unvoidedAt] = OffsetDateTime.now()
                it[SessionVoidTable.unvoidedBy] = unvoidedBy
                it[SessionVoidTable.unvoidedReason] = unvoidedReason
            }

            val sessionVoid = findByIdInTransaction(sessionVoidId)

            if (sessionVoid != null) {
                AuditLogRepository.record(
                    tableName = SessionVoidTable.tableName,
                    recordId = sessionVoid.id,
                    action = AuditAction.UPDATE,
                    changedBy = unvoidedBy,
                    oldValue = AuditLogRepository.jsonField("unvoidedAt", "null"),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "unvoidedAt" to sessionVoid.unvoidedAt.toString(),
                            "unvoidedBy" to unvoidedBy.toString(),
                            "unvoidedReason" to unvoidedReason,
                        ),
                )
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
    private fun org.jetbrains.exposed.sql.ResultRow.toSessionVoid(): SessionVoid {
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
