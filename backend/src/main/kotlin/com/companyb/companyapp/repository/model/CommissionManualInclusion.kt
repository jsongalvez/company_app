package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class CommissionManualInclusion(
    override val id: UUID,
    val productSaleId: UUID,
    val userId: UUID,
    val isIncluded: Boolean,
    val reason: String?,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "productSaleId" to productSaleId.toString(),
            "userId" to userId.toString(),
            "isIncluded" to isIncluded.toString(),
            "reason" to (reason ?: "null"),
            "assignedBy" to assignedBy.toString(),
            "assignedAt" to assignedAt.toString(),
        )
}

object CommissionManualInclusionTable : Table("commission_manual_inclusion") {
    val id = javaUUID("id").autoGenerate()
    val productSaleId = javaUUID("product_sale_id")
    val userId = javaUUID("user_id")
    val isIncluded = bool("is_included")
    val reason = text("reason").nullable()
    val assignedBy = javaUUID("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
