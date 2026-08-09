package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class RemittanceType {
    SESSION,
    PRODUCT,
}

enum class RemittanceMethod {
    BANK_TRANSFER,
    HANDED_TO_ACCOUNTANT,
}

enum class RemittanceStatus {
    DRAFT,
    SUBMITTED,
}

data class Remittance(
    val id: UUID,
    val type: RemittanceType,
    val status: RemittanceStatus,
    val branchId: UUID,
    val method: RemittanceMethod,
    val submittedDate: LocalDate,
    val submittedAt: OffsetDateTime?,
    val submittedBy: UUID,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val createdAt: OffsetDateTime,
    val version: Int,
)

object RemittanceTable : Table("remittance") {
    val id = javaUUID("id").autoGenerate()
    val type =
        customEnumeration<RemittanceType>(
            name = "type",
            sql = "remittance_type",
            fromDb = { value -> RemittanceType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "remittance_type"
                obj.value = it.name
                obj
            },
        )
    val status =
        customEnumeration<RemittanceStatus>(
            name = "status",
            sql = "remittance_status",
            fromDb = { value -> RemittanceStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "remittance_status"
                obj.value = it.name
                obj
            },
        ).default(RemittanceStatus.DRAFT)
    val branchId = javaUUID("branch_id")
    val method =
        customEnumeration<RemittanceMethod>(
            name = "method",
            sql = "remittance_method",
            fromDb = { value -> RemittanceMethod.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "remittance_method"
                obj.value = it.name
                obj
            },
        )
    val submittedDate = date("submitted_date")
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val submittedBy = javaUUID("submitted_by")
    val dateRangeStart = date("date_range_start")
    val dateRangeEnd = date("date_range_end")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Remittance): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "type" to entity.type.name,
            "status" to entity.status.name,
            "branchId" to entity.branchId.toString(),
            "method" to entity.method.name,
            "dateRangeStart" to entity.dateRangeStart.toString(),
            "dateRangeEnd" to entity.dateRangeEnd.toString(),
        )
}
