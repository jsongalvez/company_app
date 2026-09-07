package com.companyb.companyapp.identity

import com.companyb.companyapp.identity.CredentialTokenRow
import com.companyb.companyapp.identity.CredentialTokenTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Store operations for single-use credential tokens (#350, ADR-0024 rule 2): every function
 * runs on the caller's command transaction and opens none. The raw token never reaches this
 * layer — callers pass the [com.companyb.companyapp.identity.CredentialTokens.hash] digest.
 */
internal object CredentialTokenRepository {
    fun insertInTransaction(
        tokenHash: String,
        purpose: CredentialTokenPurpose,
        userId: UUID,
        expiresAt: OffsetDateTime,
        createdBy: UUID?,
    ): UUID =
        CredentialTokenTable.insert {
            it[CredentialTokenTable.tokenHash] = tokenHash
            it[CredentialTokenTable.purpose] = purpose
            it[CredentialTokenTable.userId] = userId
            it[CredentialTokenTable.expiresAt] = expiresAt
            it[CredentialTokenTable.consumedAt] = null
            it[CredentialTokenTable.createdBy] = createdBy
        }[CredentialTokenTable.id]

    fun findByHashAndPurposeInTransaction(
        tokenHash: String,
        purpose: CredentialTokenPurpose,
    ): CredentialTokenRow? =
        CredentialTokenTable
            .selectAll()
            .where { (CredentialTokenTable.tokenHash eq tokenHash) and (CredentialTokenTable.purpose eq purpose) }
            .singleOrNull()
            ?.toRow()

    fun findByIdInTransaction(id: UUID): CredentialTokenRow? =
        CredentialTokenTable
            .selectAll()
            .where { CredentialTokenTable.id eq id }
            .singleOrNull()
            ?.toRow()

    /**
     * Atomic single-use + expiry guard: consumes the token only when unconsumed and unexpired
     * (DB clock), so two concurrent accepts cannot both win and an expired code can never be
     * redeemed. False means the token was already used or is expired — the caller classifies.
     */
    fun consumeIfLiveInTransaction(id: UUID): Boolean =
        CredentialTokenTable.update(
            where = {
                (CredentialTokenTable.id eq id) and
                    CredentialTokenTable.consumedAt.isNull() and
                    (CredentialTokenTable.expiresAt greater CurrentTimestampWithTimeZone)
            },
        ) {
            it[consumedAt] = CurrentTimestampWithTimeZone
        } > 0

    /** Unconsumed tokens for [userId] + [purpose], any expiry — the re-invite recovery window (#350). */
    fun hasOutstandingInTransaction(
        userId: UUID,
        purpose: CredentialTokenPurpose,
    ): Boolean =
        !CredentialTokenTable
            .selectAll()
            .where {
                (CredentialTokenTable.userId eq userId) and
                    (CredentialTokenTable.purpose eq purpose) and
                    CredentialTokenTable.consumedAt.isNull()
            }.empty()

    /** Ids of every unconsumed token of [userId] + [purpose] — each gets its own invalidation audit row. */
    fun findUnconsumedIdsInTransaction(
        userId: UUID,
        purpose: CredentialTokenPurpose,
    ): List<UUID> =
        CredentialTokenTable
            .selectAll()
            .where {
                (CredentialTokenTable.userId eq userId) and
                    (CredentialTokenTable.purpose eq purpose) and
                    CredentialTokenTable.consumedAt.isNull()
            }.map { it[CredentialTokenTable.id] }

    /** Unconditional single-token invalidation (supersede path — expiry is not a guard here). */
    fun invalidateInTransaction(id: UUID) {
        CredentialTokenTable.update({ CredentialTokenTable.id eq id }) {
            it[consumedAt] = CurrentTimestampWithTimeZone
        }
    }

    private fun ResultRow.toRow() =
        CredentialTokenRow(
            id = this[CredentialTokenTable.id],
            userId = this[CredentialTokenTable.userId],
            expiresAt = this[CredentialTokenTable.expiresAt],
            consumedAt = this[CredentialTokenTable.consumedAt],
        )
}
