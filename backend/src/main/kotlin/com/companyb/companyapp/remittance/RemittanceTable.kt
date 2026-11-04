package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.database.pgEnum
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.util.UUID

internal object RemittanceTable : Table("remittance") {
    val id = javaUUID("id").autoGenerate()
    val type =
        pgEnum("type", "remittance_type", RemittanceType.entries)
    val status =
        pgEnum("status", "remittance_status", RemittanceStatus.entries).default(RemittanceStatus.DRAFT)
    val branchId = javaUUID("branch_id")
    val method =
        pgEnum("method", "remittance_method", RemittanceMethod.entries)
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
