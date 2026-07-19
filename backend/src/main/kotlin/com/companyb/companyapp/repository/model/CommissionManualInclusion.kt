package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
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

data class CommissionManualInclusionUpsertParams(
    val id: UUID,
    val productSaleId: UUID,
    val userId: UUID,
    val isIncluded: Boolean,
    val reason: String?,
    val assignedBy: UUID,
)

object CommissionManualInclusionTable : Table("commission_manual_inclusion") {
    val id = javaUUID("id").autoGenerate()
    val productSaleId = javaUUID("product_sale_id")
    val userId = javaUUID("user_id")
    val isIncluded = bool("is_included")
    val reason = text("reason").nullable()
    val assignedBy = javaUUID("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: CommissionManualInclusion): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "productSaleId" to entity.productSaleId.toString(),
            "userId" to entity.userId.toString(),
            "isIncluded" to entity.isIncluded.toString(),
            "reason" to (entity.reason ?: "null"),
            "assignedBy" to entity.assignedBy.toString(),
            "assignedAt" to entity.assignedAt.toString(),
        )
}
