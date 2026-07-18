package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcern
import com.companyb.companyapp.repository.model.SessionConcernTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        auditFn: (Concern) -> Unit = {},
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
                    auditFn(concern)
                }

                concern
            }
        return inserted
    }

    fun addToSession(
        sessionId: UUID,
        concernId: UUID,
        auditFn: (SessionConcern) -> Unit = {},
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
                auditFn(SessionConcern(sessionId, concernId))
            }

            created
        }

    fun removeFromSession(
        sessionId: UUID,
        concernId: UUID,
        auditFn: (SessionConcern) -> Unit = {},
    ): Boolean =
        transaction {
            val deleted =
                SessionConcernTable
                    .deleteWhere {
                        (SessionConcernTable.sessionId eq sessionId) and
                            (SessionConcernTable.concernId eq concernId)
                    } > 0

            if (deleted) {
                auditFn(SessionConcern(sessionId, concernId))
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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toConcern(): Concern =
        Concern(
            id = this[ConcernTable.id],
            label = this[ConcernTable.label],
            createdBy = this[ConcernTable.createdBy],
            createdAt = this[ConcernTable.createdAt],
        )
}
