package com.companyb.companyapp.repository.model

import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
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
    val id = javaUUID("id").autoGenerate()
    val sessionId = javaUUID("session_id").references(SessionTable.id)
    val voidedAt = timestampWithTimeZone("voided_at").defaultExpression(CurrentTimestampWithTimeZone)
    val voidedBy = javaUUID("voided_by").references(AppUserTable.id)
    val voidReason = text("void_reason")
    val unvoidedAt = timestampWithTimeZone("unvoided_at").nullable()
    val unvoidedBy = javaUUID("unvoided_by").references(AppUserTable.id).nullable()
    val unvoidedReason = text("unvoided_reason").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: SessionVoid): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "sessionId" to entity.sessionId.toString(),
            // #525 field policy — the full void lifecycle is value-diffed: the void
            // event (actor/time/reason) and the unvoid event (actor/time/reason).
            // No manual additions exist in SessionService beyond this mapping.
            "voidedAt" to entity.voidedAt.toString(),
            "voidedBy" to entity.voidedBy.toString(),
            "voidReason" to entity.voidReason,
            "unvoidedAt" to entity.unvoidedAt?.toString(),
            "unvoidedBy" to entity.unvoidedBy?.toString(),
            "unvoidedReason" to entity.unvoidedReason,
        )
}
