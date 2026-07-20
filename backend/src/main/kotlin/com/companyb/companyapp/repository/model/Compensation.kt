package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Compensation(
    val id: UUID,
    val workBranchDayId: UUID,
    val payingBranchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
    val note: String?,
    val version: Int,
)

object CompensationTable : Table("compensation") {
    private const val AMOUNT_PRECISION = 10
    private const val AMOUNT_SCALE = 2

    val id = javaUUID("id").autoGenerate()
    val workBranchDayId = javaUUID("work_branch_day_id")
    val payingBranchDayId = javaUUID("paying_branch_day_id")
    val userId = javaUUID("user_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)
    val assignedBy = javaUUID("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val note = text("note").nullable()
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Compensation): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "workBranchDayId" to entity.workBranchDayId.toString(),
            "payingBranchDayId" to entity.payingBranchDayId.toString(),
            "userId" to entity.userId.toString(),
            "amount" to entity.amount.toPlainString(),
            "assignedBy" to entity.assignedBy.toString(),
            "assignedAt" to entity.assignedAt.toString(),
            "note" to (entity.note ?: "null"),
            "version" to entity.version.toString(),
        )
}
