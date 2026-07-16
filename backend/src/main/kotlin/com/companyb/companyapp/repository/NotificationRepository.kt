package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.repository.model.NotificationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationRepository {
    fun insert(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): Notification =
        transaction {
            NotificationTable.insert {
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
            }

            NotificationTable
                .selectAll()
                .where { (NotificationTable.sessionId eq sessionId) and (NotificationTable.userId eq userId) }
                .single()
                .toNotification()
        }.also {
            logger.info {
                "[INSERT-NOTIFICATION] session=${sessionId.toString().maskUUID()} user=${userId.toString().maskUUID()}"
            }
        }

    fun findUnreadByUserId(userId: UUID): List<Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .map { it.toNotification() }
        }.also { logger.info { "[FIND-UNREAD] $it.size unread notifications for user $userId" } }

    fun markRead(notificationId: UUID): Notification? =
        transaction {
            val found =
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.id eq notificationId }
                    .empty()
                    .not()
            if (!found) return@transaction null

            NotificationTable.update({ NotificationTable.id eq notificationId }) {
                it[isRead] = true
                it[readAt] = CurrentTimestampWithTimeZone
            }

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
            isRead = this[NotificationTable.isRead],
            readAt = this[NotificationTable.readAt],
            createdAt = this[NotificationTable.createdAt],
        )
}
