package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class SessionVoid(
    val id: UUID,
    val sessionId: UUID,
    val voidedAt: OffsetDateTime,
    val voidedBy: UUID,
    val voidReason: String,
    val unvoidedAt: OffsetDateTime?,
    val unvoidedBy: UUID?,
    val unvoidedReason: String?,
)

object SessionVoidTable : Table("session_void") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(SessionTable.id)
    val voidedAt = timestampWithTimeZone("voided_at").defaultExpression(CurrentTimestampWithTimeZone)
    val voidedBy = uuid("voided_by").references(AppUserTable.id)
    val voidReason = text("void_reason")
    val unvoidedAt = timestampWithTimeZone("unvoided_at").nullable()
    val unvoidedBy = uuid("unvoided_by").references(AppUserTable.id).nullable()
    val unvoidedReason = text("unvoided_reason").nullable()

    override val primaryKey = PrimaryKey(id)
}
