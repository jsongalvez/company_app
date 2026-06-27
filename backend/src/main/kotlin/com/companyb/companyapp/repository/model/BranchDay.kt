package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDate
import java.util.UUID

data class BranchDay(
    val id: UUID,
    val branchId: UUID,
    val date: LocalDate,
    val status: DayStatus,
)

enum class DayStatus { OPEN, PAST, REMITTED }

object BranchDayTable : Table("branch_day") {
    private const val ENUM_LENGTH = 50

    val id = uuid("id").autoGenerate()
    val branchId = uuid("branch_id")
    val date = date("date")
    val status = enumerationByName<DayStatus>("status", ENUM_LENGTH).default(DayStatus.OPEN)

    override val primaryKey = PrimaryKey(id)
}
