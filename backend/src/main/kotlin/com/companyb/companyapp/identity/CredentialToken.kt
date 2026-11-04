package com.companyb.companyapp.identity

import com.companyb.companyapp.database.pgEnum
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

/** A stored credential token projected without the hash — everything the accept flow classifies on. */
data class CredentialTokenRow(
    val id: UUID,
    val userId: UUID,
    val expiresAt: OffsetDateTime,
    val consumedAt: OffsetDateTime?,
)

internal object CredentialTokenTable : Table("credential_token") {
    /** SHA-256 hex digest length — the stored form of a raw token (the raw never lands here). */
    private const val TOKEN_HASH_LENGTH = 64

    val id = javaUUID("id").autoGenerate()
    val tokenHash = varchar("token_hash", TOKEN_HASH_LENGTH)
    val purpose =
        pgEnum("purpose", "credential_purpose", CredentialTokenPurpose.entries)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumedAt = timestampWithTimeZone("consumed_at").nullable()
    val createdBy = javaUUID("created_by").references(AppUserTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}
