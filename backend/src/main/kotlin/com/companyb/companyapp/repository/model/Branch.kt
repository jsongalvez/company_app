package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.BranchType
import org.jetbrains.exposed.sql.Table
import java.util.UUID

data class Branch(
    val id: UUID,
    val name: String,
    val branchType: BranchType,
)

object BranchTable : Table("branch") {
    private const val ENUM_LENGTH = 50

    val id = uuid("id").autoGenerate()
    val branchType = enumerationByName<BranchType>("branch_type", ENUM_LENGTH).default(BranchType.CLINIC)
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}
