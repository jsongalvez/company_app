package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject

data class AppUser(
    val id: String,
    val username: String,
    val passwordHash: String,
)

enum class UserStatus { ACTIVE, INACTIVE }

object AppUserTable : Table("app_user") {
    private const val USERNAME_LENGTH = 255
    private const val PASSWORD_HASH_LENGTH = 60
    private const val EMAIL_LENGTH = 50
    private const val DISPLAY_NAME_LENGTH = 50

    val id = uuid("id").autoGenerate()
    val username = varchar("username", USERNAME_LENGTH).uniqueIndex()
    val passwordHash = varchar("password_hash", PASSWORD_HASH_LENGTH)
    val status =
        customEnumeration<UserStatus>(
            name = "status",
            sql = "user_status",
            fromDb = { value -> UserStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "user_status"
                obj.value = it.name
                obj
            },
        ).default(UserStatus.ACTIVE)
    val email = varchar("email", EMAIL_LENGTH).uniqueIndex()
    val displayName = varchar("display_name", DISPLAY_NAME_LENGTH).default("User")
    val createdAt =
        timestampWithTimeZone("created_at")
            .defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
