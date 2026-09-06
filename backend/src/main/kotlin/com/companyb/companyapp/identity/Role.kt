package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.CapabilityTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

data class Role(
    val id: UUID,
    val name: String,
)

internal object RoleTable : Table("role") {
    val id = javaUUID("id").autoGenerate()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}

internal object UserRoleTable : Table("user_role") {
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val roleId = javaUUID("role_id").references(RoleTable.id)

    override val primaryKey = PrimaryKey(userId, roleId)
}

internal object RoleCapabilityTable : Table("role_capability") {
    val roleId = javaUUID("role_id").references(RoleTable.id)
    val capabilityId = javaUUID("capability_id").references(CapabilityTable.id)

    override val primaryKey = PrimaryKey(roleId, capabilityId)
}
