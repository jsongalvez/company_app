package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.repository.model.NotificationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.BatchInsertBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationRepository {
    // #356 — storage widening moved uniqueness out of the schema (V25 dropped
    // idx_notification_unique), so the write path owns event identity instead: appointment
    // reminders stay one-per-(session,user) — a scheduler re-run or an in-batch duplicate
    // updates nothing — while null-session events (relief, #358) bypass pair identity and
    // always insert. Returns the number of rows actually created.
    fun insertBatch(params: List<NotificationCreateParams>): Int {
        if (params.isEmpty()) return 0

        return transaction {
            val sessionIds = params.mapNotNull { it.sessionId }
            val userIds = params.map { it.userId }.toSet()
            val existingPairs =
                if (sessionIds.isEmpty()) {
                    emptySet<Pair<UUID, UUID>>()
                } else {
                    NotificationTable
                        .selectAll()
                        .where {
                            (NotificationTable.sessionId inList sessionIds) and
                                (NotificationTable.userId inList userIds)
                        }.map { it[NotificationTable.sessionId] to it[NotificationTable.userId] }
                        .toSet()
                }

            // In-batch event identity (#358): appointment rows stay keyed by (session,user);
            // null-session relief rows by (event,source,user), so one command's broadcast
            // writes one message per person even when several events share the batch.
            val seen = HashSet<Pair<Any?, UUID>>()
            val fresh =
                params.filter { candidate ->
                    val key: Pair<Any?, UUID> =
                        if (candidate.sessionId != null) {
                            Pair(candidate.sessionId, candidate.userId)
                        } else {
                            Pair("${candidate.eventType}:${candidate.sourceId}", candidate.userId)
                        }
                    (candidate.sessionId == null || key !in existingPairs) && seen.add(key)
                }
            if (fresh.isEmpty()) {
                0
            } else {
                val statement =
                    BatchInsertStatement(
                        table = NotificationTable,
                        ignore = true,
                        shouldReturnGeneratedValues = false,
                    )
                fresh.forEach { params ->
                    statement.addBatch()
                    statement[NotificationTable.sessionId] = params.sessionId
                    statement[NotificationTable.userId] = params.userId
                    statement[NotificationTable.branchId] = params.branchId
                    statement[NotificationTable.message] = params.message
                    statement[NotificationTable.createdAt] = CurrentTimestampWithTimeZone
                    statement[NotificationTable.eventType] = params.eventType
                    statement[NotificationTable.sourceId] = params.sourceId
                    statement[NotificationTable.targetDate] = params.targetDate
                }
                BatchInsertBlockingExecutable(statement).execute(this) ?: 0
            }
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

    // #356 — history: every row the caller owns, read + unread, newest first. Read rows are
    // never deleted (they carry session access), so the list is stable indefinitely.
    fun findHistoryByUserId(userId: UUID): List<Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.userId eq userId }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .map { it.toNotification() }
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

    // #358 — idempotency marker for the expiry job: a request with a stored EXPIRED notice
    // is never announced twice (job re-runs, restarts).
    fun existsForSource(
        eventType: String,
        sourceId: UUID,
    ): Boolean =
        transaction {
            NotificationTable
                .selectAll()
                .where {
                    (NotificationTable.eventType eq eventType) and
                        (NotificationTable.sourceId eq sourceId)
                }.empty()
                .not()
        }

    // #358 — the original ping list of a relief request (#352 Q3): distinct users holding a
    // RELIEF_REQUESTED row for it. The expiry notice goes to exactly these users.
    fun findUsersBySource(
        eventType: String,
        sourceId: UUID,
    ): List<UUID> =
        transaction {
            NotificationTable
                .selectAll()
                .where {
                    (NotificationTable.eventType eq eventType) and
                        (NotificationTable.sourceId eq sourceId)
                }.map { it[NotificationTable.userId] }
                .distinct()
        }

    // Store operation for the owning command (ADR-0024): runs on the caller's transaction and
    // opens none. Notifications are system read-state, not a §12.1 covered table — no audit row.
    fun markAllReadInTransaction(userId: UUID): Int {
        NotificationTable.update({
            (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = CurrentTimestampWithTimeZone
        }

        return NotificationTable
            .selectAll()
            .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
            .count()
            .toInt()
    }

    // Ownership in the WHERE clause (backend AGENTS.md parent-child scoping rule): a caller can
    // never mutate another user's row — an update scoped to caller + id either hits the caller's
    // own row or affects nothing, so the 404-on-foreign-row case leaves the row untouched.
    fun markReadInTransaction(
        callerId: UUID,
        notificationId: UUID,
    ): Notification? {
        val updated =
            NotificationTable.update({
                (NotificationTable.id eq notificationId) and (NotificationTable.userId eq callerId)
            }) {
                it[isRead] = true
                it[readAt] = CurrentTimestampWithTimeZone
            }
        if (updated == 0) return null

        return NotificationTable
            .selectAll()
            .where { NotificationTable.id eq notificationId }
            .single()
            .toNotification()
    }

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
            eventType = this[NotificationTable.eventType],
            sourceId = this[NotificationTable.sourceId],
            targetDate = this[NotificationTable.targetDate],
        )
}
