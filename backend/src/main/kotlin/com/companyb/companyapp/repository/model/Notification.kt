package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Notification(
    val id: UUID,
    val sessionId: UUID,
    val userId: UUID,
    val branchId: UUID,
    val isRead: Boolean,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

object NotificationTable : Table("notification") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(SessionTable.id)
    val userId = uuid("user_id").references(AppUserTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val isRead = bool("is_read").default(false)
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
