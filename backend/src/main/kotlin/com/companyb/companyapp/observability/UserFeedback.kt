package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackKind
import com.companyb.companyapp.contracts.incident.FeedbackStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

/** A problem or idea someone sent from the app ("Send feedback"), kept for triage. */
data class UserFeedback(
    val id: UUID,
    val userId: UUID,
    val userDisplayName: String,
    val kind: FeedbackKind,
    val message: String,
    val page: String?,
    val details: String?,
    val appVersion: String?,
    val status: FeedbackStatus,
    val createdAt: OffsetDateTime,
)

data class NewUserFeedback(
    val id: UUID,
    val userId: UUID,
    val kind: FeedbackKind,
    val message: String,
    val page: String?,
    val details: String?,
    val appVersion: String?,
)

internal object UserFeedbackTable : Table("user_feedback") {
    private const val KIND_LENGTH = 20
    private const val PAGE_LENGTH = 300
    private const val VERSION_LENGTH = 64

    val id = javaUUID("id")
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val kind = varchar("kind", KIND_LENGTH)
    val message = text("message")
    val page = varchar("page", PAGE_LENGTH).nullable()
    val details = text("details").nullable()
    val appVersion = varchar("app_version", VERSION_LENGTH).nullable()
    val status = varchar("status", KIND_LENGTH).default(FeedbackStatus.NEW.name)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
