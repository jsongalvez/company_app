package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.UserStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime

data class AppUser(
    val id: String,
    val username: String,
    val passwordHash: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val displayName: String = "User",
    val deactivatedAt: OffsetDateTime? = null,
    val jwtRevokedAt: OffsetDateTime? = null,
    val credentialVersion: Long = 0L,
)

object AppUserTable : Table("app_user") {
    private const val USERNAME_LENGTH = 255
    private const val PASSWORD_HASH_LENGTH = 60
    private const val EMAIL_LENGTH = 50
    private const val DISPLAY_NAME_LENGTH = 50

    val id = javaUUID("id").autoGenerate()
    val username = varchar("username", USERNAME_LENGTH).uniqueIndex()
    val passwordHash = varchar("password_hash", PASSWORD_HASH_LENGTH)
    val status =
        customEnumeration<UserStatus>(
            name = "status",
            sql = "user_status",
            // SAFETY: PG enum column binds as String via customEnumeration #467
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
    val deactivatedAt = timestampWithTimeZone("deactivated_at").nullable()
    val jwtRevokedAt = timestampWithTimeZone("jwt_revoked_at").nullable()
    val credentialVersion = long("credential_version").default(0L)
    val createdAt =
        timestampWithTimeZone("created_at")
            .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: AppUser): Map<String, String?> =
        mapOf(
            "id" to entity.id,
            "username" to entity.username,
            "status" to entity.status.name,
            "displayName" to entity.displayName,
            "deactivatedAt" to entity.deactivatedAt?.toString(),
            "jwtRevokedAt" to entity.jwtRevokedAt?.toString(),
        )
}
