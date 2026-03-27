package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table

data class AppUser(
    val id: String,
    val username: String,
    val passwordHash: String,
)

enum class UserStatus { ACTIVE, INACTIVE }

object AppUserTable : Table("app_user") {
    val id = uuid("id").autoGenerate()
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 60)
    val status = enumerationByName<UserStatus>("status", 50).default(UserStatus.ACTIVE)

    override val primaryKey = PrimaryKey(id)
}
