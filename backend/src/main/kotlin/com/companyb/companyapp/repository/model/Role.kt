package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import java.util.UUID

data class Role(
    val id: UUID,
    val name: String,
)

object RoleTable : Table("role") {
    val id = uuid("id").autoGenerate()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}

object UserRoleTable : Table("user_role") {
    val userId = uuid("user_id").references(AppUserTable.id)
    val roleId = uuid("role_id").references(RoleTable.id)

    override val primaryKey = PrimaryKey(userId, roleId)
}
