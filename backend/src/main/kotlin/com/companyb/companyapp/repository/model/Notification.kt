package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Notification(
    val id: UUID,
    val sessionId: UUID,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
    val isRead: Boolean,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

data class CreateNotification(
    val sessionId: UUID,
    val userId: UUID,
    val branchId: UUID,
    val message: String,
)

object NotificationTable : Table("notification") {
    val id = javaUUID("id").autoGenerate()
    val sessionId = javaUUID("session_id").references(SessionTable.id)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val message = text("message")
    val isRead = bool("is_read").default(false)
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
