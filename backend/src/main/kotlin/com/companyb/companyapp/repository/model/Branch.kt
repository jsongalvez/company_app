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

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Branch): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "name" to entity.name,
            "branchType" to entity.branchType.name,
        )
}
