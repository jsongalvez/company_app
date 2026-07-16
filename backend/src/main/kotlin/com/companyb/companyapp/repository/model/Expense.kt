package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class ExpenseCategory {
    PANTRY,
    COMMUNICATION,
    WATER,
    TRANSPORTATION,
    ELECTRICITY,
    RENTAL,
    OFFICE_SUPPLIES,
    FURNITURE_FIXTURES,
    MISCELLANEOUS,
}

data class Expense(
    val id: UUID,
    val branchDayId: UUID,
    val amount: BigDecimal,
    val category: ExpenseCategory,
    val notes: String?,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val deletedBy: UUID?,
    val deletedAt: OffsetDateTime?,
)

object ExpenseTable : Table("expense") {
    private const val AMOUNT_PRECISION = 10
    private const val AMOUNT_SCALE = 2

    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)
    val category =
        customEnumeration<ExpenseCategory>(
            name = "category",
            sql = "expense_category",
            fromDb = { value -> ExpenseCategory.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "expense_category"
                obj.value = it.name
                obj
            },
        )
    val notes = text("notes").nullable()
    val createdBy = javaUUID("created_by")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedBy = javaUUID("deleted_by").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
