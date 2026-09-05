package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.repository.model.NotificationTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.BatchInsertBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** Keyset cursor for notification history: strictly-before position on `(created_at, id) DESC`. */
data class NotificationHistoryCursor(
    val createdAt: OffsetDateTime,
    val id: UUID,
)

/** Opaque URL-safe cursor for [NotificationHistoryCursor]: `createdAt|id`, base64url. */
fun encodeNotificationCursor(cursor: NotificationHistoryCursor): String =
    encodeOpaqueCursor(cursor.createdAt.toString(), cursor.id.toString())

fun decodeNotificationCursor(raw: String): NotificationHistoryCursor {
    val parts =
        runCatching { decodeOpaqueCursor(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid notification cursor") }
    require(parts.size == 2) { "Invalid notification cursor" }
    return NotificationHistoryCursor(
        createdAt = OffsetDateTime.parse(parts[0]),
        id = UUID.fromString(parts[1]),
    )
}

object NotificationRepository {
    // #508 — the write path no longer pre-reads: every occurrence carries a stable key and
    // UNIQUE (dedup_key, user_id) is the dedup guarantee, so concurrent batches and job
    // re-runs collapse to one delivery per recipient atomically (insertIgnore = ON CONFLICT
    // DO NOTHING). Appointment identity is session + target appointment date — a same-sweep
    // retry reuses the key while a genuinely later appointment gets a new one (#356: distinct
    // repeat events survive). Relief identity is event + source. Returns rows actually created.
    fun insertBatch(params: List<NotificationCreateParams>): Int {
        if (params.isEmpty()) return 0

        return transaction {
            // In-batch event identity: one command's broadcast writes one message per
            // (occurrence, recipient) even when several events share the batch.
            val seen = HashSet<Pair<String, UUID>>()
            val fresh =
                params.filter { candidate ->
                    seen.add(dedupKeyFor(candidate) to candidate.userId)
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
                fresh.forEach { candidate ->
                    statement.addBatch()
                    statement[NotificationTable.sessionId] = candidate.sessionId
                    statement[NotificationTable.userId] = candidate.userId
                    statement[NotificationTable.branchId] = candidate.branchId
                    statement[NotificationTable.message] = candidate.message
                    statement[NotificationTable.createdAt] = CurrentTimestampWithTimeZone
                    statement[NotificationTable.eventType] = candidate.eventType
                    statement[NotificationTable.sourceId] = candidate.sourceId
                    statement[NotificationTable.targetDate] = candidate.targetDate
                    statement[NotificationTable.dedupKey] = dedupKeyFor(candidate)
                }
                BatchInsertBlockingExecutable(statement).execute(this) ?: 0
            }
        }.also {
            logger.info { "[INSERT-NOTIFICATIONS] created=$it candidates=${params.size}" }
        }
    }

    /**
     * Stable occurrence identity for one delivery. Explicit [NotificationCreateParams.dedupKey]
     * wins (the revocation direct notice); otherwise appointment rows key on session + target
     * appointment date and event rows on event + source. The legacy fallback (no session, no
     * event identity) keys on branch + message hash — reachable only by writers that predate
     * occurrence identity, never by production broadcasts.
     */
    fun dedupKeyFor(params: NotificationCreateParams): String {
        val sessionId = params.sessionId
        val eventType = params.eventType
        val sourceId = params.sourceId
        return params.dedupKey
            ?: if (sessionId != null) {
                "$APPOINTMENT_KEY_PREFIX$sessionId:${params.targetDate}"
            } else if (eventType != null && sourceId != null) {
                "$eventType:$sourceId"
            } else {
                "$LEGACY_KEY_PREFIX${params.branchId}:${params.message.hashCode()}"
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
    // Kept for server-side/test reads; the HTTP history response is keyset-paged below (#508).
    fun findHistoryByUserId(userId: UUID): List<Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.userId eq userId }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .orderBy(NotificationTable.id, SortOrder.DESC)
                .map { it.toNotification() }
        }

    /**
     * #508 — bounded permanent-history page over `(created_at DESC, id DESC)`. [cursor] is the
     * strictly-before position (exclusive, from [encodeNotificationCursor] on the previous
     * page's last row); at most [limit] rows are returned. Equal timestamps order by id, so
     * concurrent inserts shift only newer rows ahead of the cursor — fetched pages never skip
     * or duplicate. Served by idx_notification_history.
     */
    fun findHistoryPage(
        userId: UUID,
        cursor: NotificationHistoryCursor?,
        limit: Int,
    ): List<Notification> =
        transaction {
            var condition = (NotificationTable.userId eq userId)
            if (cursor != null) {
                val keyset =
                    (NotificationTable.createdAt less cursor.createdAt) or
                        (
                            (NotificationTable.createdAt eq cursor.createdAt) and
                                (NotificationTable.id less cursor.id)
                        )
                condition = condition and keyset
            }
            NotificationTable
                .selectAll()
                .where { condition }
                .orderBy(NotificationTable.createdAt, SortOrder.DESC)
                .orderBy(NotificationTable.id, SortOrder.DESC)
                .limit(limit)
                .map { it.toNotification() }
        }.also {
            logger.info { "[HISTORY-PAGE] user=${userId.toString().maskUUID()} returned=${it.size}" }
        }

    // #508 — badge count without row hydration: the 60s poller reads one integer, never the
    // mailbox. Served by idx_notification_unread.
    fun countUnreadByUserId(userId: UUID): Int =
        transaction {
            NotificationTable
                .selectAll()
                .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
                .count()
                .toInt()
        }

    // #152 bearer check for the session-detail read (#151 Q1): the notification row IS the
    // authorization — any read state. Served by idx_notification_session_user (#508), so the
    // lookup is one indexed hit.
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

    private const val APPOINTMENT_KEY_PREFIX = "APPT:"
    private const val LEGACY_KEY_PREFIX = "MISC:"
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
