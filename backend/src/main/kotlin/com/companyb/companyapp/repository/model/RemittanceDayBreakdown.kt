package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

data class RemittanceDayBreakdown(
    val id: UUID,
    val remittanceId: UUID,
    val branchDayId: UUID,
)

object RemittanceDayBreakdownTable : Table("remittance_day_breakdown") {
    val id = javaUUID("id")
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)

    override val primaryKey = PrimaryKey(id)
}
