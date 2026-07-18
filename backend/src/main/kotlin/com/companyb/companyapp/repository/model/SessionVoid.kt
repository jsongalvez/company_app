package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class SessionVoid(
    override val id: UUID,
    val sessionId: UUID,
    val voidedAt: OffsetDateTime,
    val voidedBy: UUID,
    val voidReason: String,
    val unvoidedAt: OffsetDateTime?,
    val unvoidedBy: UUID?,
    val unvoidedReason: String?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "sessionId" to sessionId.toString(),
            "voidReason" to voidReason,
        )
}

object SessionVoidTable : Table("session_void") {
    val id = javaUUID("id").autoGenerate()
    val sessionId = javaUUID("session_id").references(SessionTable.id)
    val voidedAt = timestampWithTimeZone("voided_at").defaultExpression(CurrentTimestampWithTimeZone)
    val voidedBy = javaUUID("voided_by").references(AppUserTable.id)
    val voidReason = text("void_reason")
    val unvoidedAt = timestampWithTimeZone("unvoided_at").nullable()
    val unvoidedBy = javaUUID("unvoided_by").references(AppUserTable.id).nullable()
    val unvoidedReason = text("unvoided_reason").nullable()

    override val primaryKey = PrimaryKey(id)
}
