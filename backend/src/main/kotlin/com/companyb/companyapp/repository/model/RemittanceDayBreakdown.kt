package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import java.util.UUID

data class RemittanceDayBreakdown(
    val id: UUID,
    val remittanceId: UUID,
    val branchDayId: UUID,
)

object RemittanceDayBreakdownTable : Table("remittance_day_breakdown") {
    val id = uuid("id")
    val remittanceId = uuid("remittance_id").references(RemittanceTable.id)
    val branchDayId = uuid("branch_day_id").references(BranchDayTable.id)

    override val primaryKey = PrimaryKey(id)
}
