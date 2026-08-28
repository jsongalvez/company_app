package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// #356 — session_id is nullable: appointment reminders carry it (session access rides the
// row, #151), while non-session events (relief, #358) have none. Uniqueness is no longer a
// schema constraint (pre-squash V25 dropped idx_notification_unique) — one person can hold many rows.
//
// #358 — relief rows carry the event family (event_type), the causing record (source_id —
// polymorphic: grant_relief_access or relief_invite id, no FK), and the branch day the tap
// destination points at (target_date). Appointment rows leave all three NULL.
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
)

object NotificationTable : Table("notification") {
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

    override val primaryKey = PrimaryKey(id)

    private const val EVENT_TYPE_LENGTH = 50
}
