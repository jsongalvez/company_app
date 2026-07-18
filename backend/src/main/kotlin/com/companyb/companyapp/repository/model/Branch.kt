package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.BranchType
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
        customEnumeration<BranchType>(
            name = "branch_type",
            sql = "branch_type",
            fromDb = { value -> BranchType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "branch_type"
                obj.value = it.name
                obj
            },
        ).default(BranchType.CLINIC)
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}
