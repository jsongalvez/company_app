package com.companyb.companyapp.notification
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// #356 — session_id is nullable: appointment reminders carry it (session access rides the
// row, #151), while non-session events (relief, #358) have none. Uniqueness is per
// (occurrence, recipient) via UNIQUE (dedup_key, user_id) (#508) — one person holds
// many rows across occurrences, never two deliveries of the same occurrence.
//
// #358 — relief rows carry the event family (event_type), the causing record (source_id —
// polymorphic: grant_relief_access or relief_invite id, no FK), and the branch day the tap
// destination points at (target_date). Appointment rows carry the sweep identity instead:
// event_type APPOINTMENT_REMINDER, source_id = the session, target_date = the upcoming
// appointment date the sweep announced (#508 — the occurrence dimension that lets a
// genuinely later appointment survive while a same-sweep retry dedups).
//
// #508 — every row carries the stable occurrence key (dedup_key): appointment identity is
// session + target date, relief identity event + source. UNIQUE (dedup_key, user_id) is the
// durable idempotency guarantee — concurrent batches and job re-runs collapse atomically.
data class Notification(
    val id: UUID,
    val sessionId: UUID?,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
    val isRead: Boolean,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val eventType: String? = null,
    val sourceId: UUID? = null,
    val targetDate: LocalDate? = null,
)

data class NotificationCreateParams(
    val sessionId: UUID?,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
    // #358 — event rows (sessionId == null). Defaults keep the appointment-reminder
    // call sites byte-identical.
    val eventType: String? = null,
    val sourceId: UUID? = null,
    val targetDate: LocalDate? = null,
    // #508 — explicit occurrence-key override. Only the revocation direct notice uses
    // it (same event + source as the branch broadcast, distinct audience message);
    // every other writer derives the key from the identity columns above.
    val dedupKey: String? = null,
)

internal object NotificationTable : Table("notification") {
    val id = javaUUID("id").autoGenerate()
    val sessionId = javaUUID("session_id").references(SessionTable.id).nullable()
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val message = text("message")
    val isRead = bool("is_read").default(false)
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val eventType = varchar("event_type", EVENT_TYPE_LENGTH).nullable()
    val sourceId = javaUUID("source_id").nullable()
    val targetDate = date("target_date").nullable()
    val dedupKey = varchar("dedup_key", DEDUP_KEY_LENGTH)

    override val primaryKey = PrimaryKey(id)

    private const val EVENT_TYPE_LENGTH = 50
    private const val DEDUP_KEY_LENGTH = 120
}

/**
 * Row mapping lives in its own internal seam (map #533 #550) so the mailbox store stays
 * within the raw function-count budget without an artificial split (#535 lane): table
 * knowledge appears only inside `internal` bodies and public surfaces stay table-free.
 */
internal object NotificationMapper {
    fun org.jetbrains.exposed.v1.core.ResultRow.toNotification(): Notification =
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
