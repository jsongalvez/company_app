package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object ConcernRepository {
    fun findAll(): List<Concern> =
        transaction {
            ConcernTable
                .selectAll()
                .map { it.toConcern() }
        }

    fun findById(id: UUID): Concern? =
        transaction {
            ConcernTable
                .selectAll()
                .where { ConcernTable.id eq id }
                .singleOrNull()
                ?.toConcern()
        }

    fun create(
        id: UUID,
        label: String,
        createdBy: UUID?,
    ): Concern {
        val inserted =
            transaction {
                val insertedCount =
                    ConcernTable
                        .insertIgnore {
                            it[ConcernTable.id] = id
                            it[ConcernTable.label] = label
                            if (createdBy != null) {
                                it[ConcernTable.createdBy] = createdBy
                            }
                        }.insertedCount

                val concern =
                    findByIdInTransaction(id)
                        ?: error("concern row not found after idempotent insert for $id")

                if (insertedCount > 0 && createdBy != null) {
                    AuditLogRepository.record(
                        tableName = ConcernTable.tableName,
                        recordId = concern.id,
                        action = AuditAction.INSERT,
                        changedBy = createdBy,
                        newValue =
                            AuditLogRepository.jsonFields(
                                "id" to concern.id.toString(),
                                "label" to label,
                            ),
                    )
                }

                concern
            }
        return inserted
    }

    fun addToSession(
        sessionId: UUID,
        concernId: UUID,
        changedBy: UUID,
    ): Boolean =
        transaction {
            val insertedCount =
                SessionConcernTable
                    .insertIgnore {
                        it[SessionConcernTable.sessionId] = sessionId
                        it[SessionConcernTable.concernId] = concernId
                    }.insertedCount

            val created = insertedCount > 0
            if (created) {
                AuditLogRepository.record(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "concernId" to concernId.toString(),
                        ),
                )
            }

            created
        }

    fun removeFromSession(
        sessionId: UUID,
        concernId: UUID,
        changedBy: UUID,
    ): Boolean =
        transaction {
            val deleted =
                SessionConcernTable
                    .deleteWhere {
                        (SessionConcernTable.sessionId eq sessionId) and
                            (SessionConcernTable.concernId eq concernId)
                    } > 0

            if (deleted) {
                AuditLogRepository.record(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    action = AuditAction.DELETE,
                    changedBy = changedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "concernId" to concernId.toString(),
                        ),
                )
            }

            deleted
        }

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

    private fun findByIdInTransaction(id: UUID): Concern? =
        ConcernTable
            .selectAll()
            .where { ConcernTable.id eq id }
            .singleOrNull()
            ?.toConcern()

    private fun org.jetbrains.exposed.sql.ResultRow.toConcern(): Concern =
        Concern(
            id = this[ConcernTable.id],
            label = this[ConcernTable.label],
            createdBy = this[ConcernTable.createdBy],
            createdAt = this[ConcernTable.createdAt],
        )
}
