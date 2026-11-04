package com.companyb.companyapp.branch

import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.database.pgEnum
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.postgresql.util.PGobject
import java.util.UUID

data class Branch(
    val id: UUID,
    val name: String,
    val branchType: BranchType,
)

data class BranchCreateParams(
    val id: UUID,
    val name: String,
    val branchType: BranchType,
    val changedBy: UUID,
)

object BranchTable : Table("branch") {
    val id = javaUUID("id").autoGenerate()
    val branchType =
        pgEnum("branch_type", "branch_type", BranchType.entries).default(BranchType.CLINIC)
    val name = text("name")
    val hidden = bool("hidden").default(false)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Branch): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "name" to entity.name,
            "branchType" to entity.branchType.name,
        )
}
