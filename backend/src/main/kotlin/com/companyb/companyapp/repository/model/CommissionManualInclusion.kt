package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class CommissionManualInclusion(
    val id: UUID,
    val productSaleId: UUID,
    val userId: UUID,
    val isIncluded: Boolean,
    val reason: String?,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
)

object CommissionManualInclusionTable : Table("commission_manual_inclusion") {
    val id = uuid("id").autoGenerate()
    val productSaleId = uuid("product_sale_id")
    val userId = uuid("user_id")
    val isIncluded = bool("is_included")
    val reason = text("reason").nullable()
    val assignedBy = uuid("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
