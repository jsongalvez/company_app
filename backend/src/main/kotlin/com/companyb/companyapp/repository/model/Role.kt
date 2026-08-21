package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

data class Role(
    val id: UUID,
    val name: String,
)

object RoleTable : Table("role") {
    val id = javaUUID("id").autoGenerate()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}

object UserRoleTable : Table("user_role") {
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val roleId = javaUUID("role_id").references(RoleTable.id)

    override val primaryKey = PrimaryKey(userId, roleId)
}

object RoleCapabilityTable : Table("role_capability") {
    val roleId = javaUUID("role_id").references(RoleTable.id)
    val capabilityId = javaUUID("capability_id").references(CapabilityTable.id)

    override val primaryKey = PrimaryKey(roleId, capabilityId)
}
