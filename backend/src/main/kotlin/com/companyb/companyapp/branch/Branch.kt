package com.companyb.companyapp.branch

import com.companyb.companyapp.contracts.branch.BranchType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.util.UUID

data class Branch(
    val id: UUID,
    val name: String,
    val branchType: BranchType,
    // 503bf228 — the branch's own commission for every product sold there; null = each product's shared one.
    val commissionAmount: BigDecimal? = null,
)

data class BranchCreateParams(
    val id: UUID,
    val name: String,
    val branchType: BranchType,
    val changedBy: UUID,
)

private const val COMMISSION_PRECISION = 10
private const val COMMISSION_SCALE = 2

object BranchTable : Table("branch") {
    val id = javaUUID("id").autoGenerate()
    val branchType =
        customEnumeration<BranchType>(
            name = "branch_type",
            sql = "branch_type",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> BranchType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "branch_type"
                obj.value = it.name
                obj
            },
        ).default(BranchType.CLINIC)
    val name = text("name")
    val commissionAmount = decimal("commission_amount", COMMISSION_PRECISION, COMMISSION_SCALE).nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Branch): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "name" to entity.name,
            "branchType" to entity.branchType.name,
            "branchCommission" to (entity.commissionAmount?.toPlainString() ?: "shared"),
        )
}
