package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.service.finance.remittance.Remittance
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.util.UUID

object RemittanceTable : Table("remittance") {
    val id = javaUUID("id").autoGenerate()
    val type =
        customEnumeration<RemittanceType>(
            name = "type",
            sql = "remittance_type",
            // SAFETY: PG enum column binds as String via customEnumeration #467
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
            // SAFETY: PG enum column binds as String via customEnumeration #467
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
            // SAFETY: PG enum column binds as String via customEnumeration #467
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
