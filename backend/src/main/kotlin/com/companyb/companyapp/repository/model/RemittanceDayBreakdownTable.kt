package com.companyb.companyapp.repository.model
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.service.finance.remittance.RemittanceDayBreakdown
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

object RemittanceDayBreakdownTable : Table("remittance_day_breakdown") {
    val id = javaUUID("id")
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: RemittanceDayBreakdown): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "remittanceId" to entity.remittanceId.toString(),
            "branchDayId" to entity.branchDayId.toString(),
        )
}
