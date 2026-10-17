package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackKind
import com.companyb.companyapp.contracts.incident.FeedbackStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

internal object UserFeedbackRepository {
    private const val LIST_LIMIT = 500

    /** In-transaction insert, idempotent on id: a retried send never files twice. */
    fun insertInTransaction(note: NewUserFeedback): UserFeedback {
        UserFeedbackTable.insertIgnore {
            it[id] = note.id
            it[userId] = note.userId
            it[kind] = note.kind.name
            it[message] = note.message
            it[page] = note.page
            it[details] = note.details
            it[appVersion] = note.appVersion
        }
        return findInTransaction(note.id) ?: error("feedback row missing after insert")
    }

    fun list(status: FeedbackStatus?): List<UserFeedback> =
        transaction {
            val query = joined().selectAll()
            if (status != null) query.where { UserFeedbackTable.status eq status.name }
            query
                .orderBy(UserFeedbackTable.createdAt to SortOrder.DESC)
                .limit(LIST_LIMIT)
                .map { it.toFeedback() }
        }

    fun setStatusInTransaction(
        id: UUID,
        status: FeedbackStatus,
    ): UserFeedback? {
        UserFeedbackTable.update({ UserFeedbackTable.id eq id }) { it[UserFeedbackTable.status] = status.name }
        return findInTransaction(id)
    }

    private fun findInTransaction(id: UUID): UserFeedback? =
        joined()
            .selectAll()
            .where { UserFeedbackTable.id eq id }
            .singleOrNull()
            ?.toFeedback()

    private fun joined() = UserFeedbackTable.innerJoin(AppUserTable)

    private fun ResultRow.toFeedback(): UserFeedback =
        UserFeedback(
            id = this[UserFeedbackTable.id],
            userId = this[UserFeedbackTable.userId],
            userDisplayName = this[AppUserTable.displayName],
            kind = FeedbackKind.valueOf(this[UserFeedbackTable.kind]),
            message = this[UserFeedbackTable.message],
            page = this[UserFeedbackTable.page],
            details = this[UserFeedbackTable.details],
            appVersion = this[UserFeedbackTable.appVersion],
            status = FeedbackStatus.valueOf(this[UserFeedbackTable.status]),
            createdAt = this[UserFeedbackTable.createdAt],
        )
}
