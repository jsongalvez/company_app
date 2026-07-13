package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Concern(
    val id: UUID,
    val label: String,
    val createdBy: UUID?,
    val createdAt: OffsetDateTime?,
)

object ConcernTable : Table("concern") {
    val id = uuid("id").autoGenerate()
    val label = text("label")
    val createdBy = uuid("created_by").references(AppUserTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object SessionConcernTable : Table("session_concern") {
    val sessionId = uuid("session_id").references(SessionTable.id)
    val concernId = uuid("concern_id").references(ConcernTable.id)

    override val primaryKey = PrimaryKey(sessionId, concernId)
}
