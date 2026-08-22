package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

// #356 — session_id is nullable: appointment reminders carry it (session access rides the
// row, #151), while non-session events (relief, #358) have none. Uniqueness is no longer a
// schema constraint (V25 dropped idx_notification_unique) — one person can hold many rows.
data class Notification(
    val id: UUID,
    val sessionId: UUID?,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
    val isRead: Boolean,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

data class NotificationCreateParams(
    val sessionId: UUID?,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
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

    override val primaryKey = PrimaryKey(id)
}
