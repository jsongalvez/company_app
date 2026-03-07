package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table

data class AppUser(
    val id: String,
    val username: String,
    val password: String,
)

object AppUserTable : Table("app_user") {
    val id = uuid("id").autoGenerate()
    val username = varchar("username", 255)
    val passwordHash = char("password_hash", 60)

    override val primaryKey = PrimaryKey(id)
}
