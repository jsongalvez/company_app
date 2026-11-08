package com.companyb.companyapp.identity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal const val ACCOUNTANT_ROLE = "ACCOUNTANT"
internal const val PRACTITIONER_ROLE = "PRACTITIONER"

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
