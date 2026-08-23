package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.service.finance.remittance.RemittanceLine
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.util.UUID

private const val PRECISION = 10
private const val SCALE = 2

object RemittanceLineTable : Table("remittance_line") {
    val id = javaUUID("id")
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val type =
        customEnumeration<RemittanceLineType>(
            name = "type",
            sql = "remittance_line_type",
            fromDb = { value -> RemittanceLineType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "remittance_line_type"
                obj.value = it.name
                obj
            },
        )
    val sessionId = javaUUID("session_id").references(SessionTable.id).nullable()
    val productSaleId = javaUUID("product_sale_id").references(ProductSaleTable.id).nullable()
    val createdBy = javaUUID("created_by").references(AppUserTable.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedBy = javaUUID("deleted_by").references(AppUserTable.id).nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val amount = decimal("amount", PRECISION, SCALE)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: RemittanceLine): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "remittanceId" to entity.remittanceId.toString(),
            "type" to entity.type.name,
            "amount" to entity.amount.toPlainString(),
            "deletedBy" to (entity.deletedBy?.toString() ?: "null"),
            "deletedAt" to (entity.deletedAt?.toString() ?: "null"),
        )
}
