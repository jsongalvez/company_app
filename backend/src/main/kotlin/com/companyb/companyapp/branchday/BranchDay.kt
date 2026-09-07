package com.companyb.companyapp.branchday

import com.companyb.companyapp.contracts.branchday.DayStatus
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

object BranchDayTable : Table("branch_day") {
    val id = javaUUID("id").autoGenerate()
    val branchId = javaUUID("branch_id")
    val date = date("date")
    val status =
        customEnumeration<DayStatus>(
            name = "status",
            sql = "day_status",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> DayStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "day_status"
                obj.value = it.name
                obj
            },
        ).default(DayStatus.OPEN)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: BranchDay): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "branchId" to entity.branchId.toString(),
            "date" to entity.date.toString(),
            "status" to entity.status.name,
        )
}
