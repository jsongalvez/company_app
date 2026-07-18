package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

data class RemittanceDayBreakdown(
    override val id: UUID,
    val remittanceId: UUID,
    val branchDayId: UUID,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "remittanceId" to remittanceId.toString(),
            "branchDayId" to branchDayId.toString(),
        )
}

object RemittanceDayBreakdownTable : Table("remittance_day_breakdown") {
    val id = javaUUID("id")
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)

    override val primaryKey = PrimaryKey(id)
}
