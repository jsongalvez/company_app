package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.repository.model.NotificationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.BatchInsertBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationRepository {
    fun insertBatch(params: List<NotificationCreateParams>): Int {
        if (params.isEmpty()) return 0

        return transaction {
            val statement =
                BatchInsertStatement(
                    table = NotificationTable,
                    ignore = true,
                    shouldReturnGeneratedValues = false,
                )
            params.forEach { params ->
                statement.addBatch()
                statement[NotificationTable.sessionId] = params.sessionId
                statement[NotificationTable.userId] = params.userId
                statement[NotificationTable.branchId] = params.branchId
                statement[NotificationTable.message] = params.message
                statement[NotificationTable.createdAt] = CurrentTimestampWithTimeZone
            }
            BatchInsertBlockingExecutable(statement).execute(this) ?: 0
        }.also {
            logger.info { "[INSERT-NOTIFICATIONS] created=$it candidates=${params.size}" }
        }
    }

    fun findUnreadByUserId(userId: UUID): List<Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .map { it.toNotification() }
        }.also {
            logger.info { "[FIND-UNREAD] ${it.size} unread notifications for user ${userId.toString().maskUUID()}" }
        }

    // #152 bearer check for the session-detail read (#151 Q1): the notification row IS the
    // authorization — any read state. Served by the UNIQUE idx_notification_unique
    // (session_id, user_id), so the lookup is one indexed hit.
    fun existsForSessionAndUser(
        sessionId: UUID,
        userId: UUID,
    ): Boolean =
        transaction {
            NotificationTable
                .selectAll()
                .where {
                    (NotificationTable.sessionId eq sessionId) and
                        (NotificationTable.userId eq userId)
                }.empty()
                .not()
        }

    fun markAllRead(userId: UUID): Int =
        transaction {
            NotificationTable.update({
                (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false)
            }) {
                it[isRead] = true
                it[readAt] = CurrentTimestampWithTimeZone
            }

            NotificationTable
                .selectAll()
                .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
                .count()
                .toInt()
        }.also { logger.info { "[MARK-ALL-READ] user=${userId.toString().maskUUID()} unreadRemaining=$it" } }

    // Ownership in the WHERE clause (backend AGENTS.md parent-child scoping rule): a caller can
    // never mutate another user's row — an update scoped to caller + id either hits the caller's
    // own row or affects nothing, so the 404-on-foreign-row case leaves the row untouched.
    fun markRead(
        callerId: UUID,
        notificationId: UUID,
    ): Notification? =
        transaction {
            val updated =
                NotificationTable.update({
                    (NotificationTable.id eq notificationId) and (NotificationTable.userId eq callerId)
                }) {
                    it[isRead] = true
                    it[readAt] = CurrentTimestampWithTimeZone
                }
            if (updated == 0) return@transaction null

            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq notificationId }
                .single()
                .toNotification()
        }.also { logger.info { "[MARK-READ] Notification ${notificationId.toString().maskUUID()} read=${it != null}" } }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toNotification(): Notification =
        Notification(
            id = this[NotificationTable.id],
            sessionId = this[NotificationTable.sessionId],
            userId = this[NotificationTable.userId],
            branchId = this[NotificationTable.branchId],
            message = this[NotificationTable.message],
            isRead = this[NotificationTable.isRead],
            readAt = this[NotificationTable.readAt],
            createdAt = this[NotificationTable.createdAt],
        )
}
