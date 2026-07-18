package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Concern(
    val id: UUID,
    val label: String,
    val createdBy: UUID?,
    val createdAt: OffsetDateTime?,
)

data class SessionConcern(
    val sessionId: UUID,
    val concernId: UUID,
)

object ConcernTable : Table("concern") {
    val id = javaUUID("id").autoGenerate()
    val label = text("label")
    val createdBy = javaUUID("created_by").references(AppUserTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Concern): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "label" to entity.label,
        )
}

object SessionConcernTable : Table("session_concern") {
    val sessionId = javaUUID("session_id").references(SessionTable.id)
    val concernId = javaUUID("concern_id").references(ConcernTable.id)

    override val primaryKey = PrimaryKey(sessionId, concernId)
}
