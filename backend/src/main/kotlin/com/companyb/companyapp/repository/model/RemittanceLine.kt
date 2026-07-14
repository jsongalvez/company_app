package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class RemittanceLineType {
    SESSION,
    PRODUCT_SALE,
}

data class RemittanceLine(
    val id: UUID,
    val remittanceId: UUID,
    val type: RemittanceLineType,
    val sessionId: UUID?,
    val productSaleId: UUID?,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val deletedBy: UUID?,
    val deletedAt: OffsetDateTime?,
    val amount: BigDecimal,
)

private const val PRECISION = 10
private const val SCALE = 2

object RemittanceLineTable : Table("remittance_line") {
    val id = uuid("id")
    val remittanceId = uuid("remittance_id").references(RemittanceTable.id)
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
    val sessionId = uuid("session_id").references(SessionTable.id).nullable()
    val productSaleId = uuid("product_sale_id").references(ProductSaleTable.id).nullable()
    val createdBy = uuid("created_by").references(AppUserTable.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedBy = uuid("deleted_by").references(AppUserTable.id).nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val amount = decimal("amount", PRECISION, SCALE)

    override val primaryKey = PrimaryKey(id)
}
