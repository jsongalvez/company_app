package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.postgresql.util.PGobject
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
    val id = javaUUID("id").autoGenerate()
    val branchId = javaUUID("branch_id")
    val date = date("date")
    val status =
        customEnumeration<DayStatus>(
            name = "status",
            sql = "day_status",
            fromDb = { value -> DayStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "day_status"
                obj.value = it.name
                obj
            },
        ).default(DayStatus.OPEN)

    override val primaryKey = PrimaryKey(id)
}
