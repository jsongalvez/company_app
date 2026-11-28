package com.companyb.companyapp.branchday

import com.companyb.companyapp.observability.traced
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal object SiteHubLaptopTable : Table("site_hub_laptop") {
    private const val NAME_MAX = 80
    private const val HASH_LEN = 64

    val id = javaUUID("id")
    val name = varchar("name", NAME_MAX)
    val tokenHash = char("token_hash", HASH_LEN)
    val pairedBy = javaUUID("paired_by")
    val pairedAt = timestampWithTimeZone("paired_at")
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val revokedBy = javaUUID("revoked_by").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object SiteHubCertTable : Table("site_hub_cert") {
    val id = short("id")
    val accountKeyEnc = binary("account_key_enc")
    val certPem = text("cert_pem").nullable()
    val keyPemEnc = binary("key_pem_enc").nullable()
    val notBefore = timestampWithTimeZone("not_before").nullable()
    val notAfter = timestampWithTimeZone("not_after").nullable()
    val issuedAt = timestampWithTimeZone("issued_at").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

internal data class SiteHubLaptopRecord(
    val id: UUID,
    val name: String,
    val pairedBy: UUID,
    val pairedAt: Instant,
    val lastSeenAt: Instant?,
    val revokedAt: Instant?,
    val revokedBy: UUID?,
)

internal class SiteHubCertRecord(
    val accountKeyEnc: ByteArray,
    val certPem: String?,
    val keyPemEnc: ByteArray?,
    val notBefore: Instant?,
    val notAfter: Instant?,
    val issuedAt: Instant?,
)

internal object SiteHubStore {
    private const val CERT_SINGLETON_ID: Short = 1

    fun listLaptops(): List<SiteHubLaptopRecord> =
        traced("SiteHubStore.listLaptops") {
            transaction {
                SiteHubLaptopTable
                    .selectAll()
                    .orderBy(SiteHubLaptopTable.pairedAt, SortOrder.DESC)
                    .map(::toLaptop)
            }
        }

    fun findActiveLaptopByHashInTransaction(tokenHash: String): SiteHubLaptopRecord? =
        SiteHubLaptopTable
            .selectAll()
            .where { (SiteHubLaptopTable.tokenHash eq tokenHash) and SiteHubLaptopTable.revokedAt.isNull() }
            .singleOrNull()
            ?.let(::toLaptop)

    fun insertLaptopInTransaction(
        id: UUID,
        name: String,
        tokenHash: String,
        pairedBy: UUID,
        now: Instant,
    ) {
        SiteHubLaptopTable.insert {
            it[SiteHubLaptopTable.id] = id
            it[SiteHubLaptopTable.name] = name
            it[SiteHubLaptopTable.tokenHash] = tokenHash
            it[SiteHubLaptopTable.pairedBy] = pairedBy
            it[SiteHubLaptopTable.pairedAt] = now.atOffset(ZoneOffset.UTC)
        }
    }

    fun touchLastSeenInTransaction(
        laptopId: UUID,
        now: Instant,
    ) {
        SiteHubLaptopTable.update({ SiteHubLaptopTable.id eq laptopId }) {
            it[lastSeenAt] = now.atOffset(ZoneOffset.UTC)
        }
    }

    fun revokeLaptopInTransaction(
        laptopId: UUID,
        revokedBy: UUID,
        now: Instant,
    ): Boolean {
        val exists =
            SiteHubLaptopTable
                .selectAll()
                .where { SiteHubLaptopTable.id eq laptopId }
                .any()
        if (!exists) return false
        SiteHubLaptopTable.update({ (SiteHubLaptopTable.id eq laptopId) and SiteHubLaptopTable.revokedAt.isNull() }) {
            it[revokedAt] = now.atOffset(ZoneOffset.UTC)
            it[SiteHubLaptopTable.revokedBy] = revokedBy
        }
        return true
    }

    fun findCert(): SiteHubCertRecord? =
        traced("SiteHubStore.findCert") {
            transaction { findCertInTransaction() }
        }

    fun findCertInTransaction(): SiteHubCertRecord? =
        SiteHubCertTable
            .selectAll()
            .where { SiteHubCertTable.id eq CERT_SINGLETON_ID }
            .singleOrNull()
            ?.let { row ->
                SiteHubCertRecord(
                    accountKeyEnc = row[SiteHubCertTable.accountKeyEnc],
                    certPem = row[SiteHubCertTable.certPem],
                    keyPemEnc = row[SiteHubCertTable.keyPemEnc],
                    notBefore = row[SiteHubCertTable.notBefore]?.toInstant(),
                    notAfter = row[SiteHubCertTable.notAfter]?.toInstant(),
                    issuedAt = row[SiteHubCertTable.issuedAt]?.toInstant(),
                )
            }

    fun saveAccountKeyInTransaction(
        accountKeyEnc: ByteArray,
        now: Instant,
    ) {
        val existing = findCertInTransaction()
        if (existing == null) {
            SiteHubCertTable.insert {
                it[id] = CERT_SINGLETON_ID
                it[SiteHubCertTable.accountKeyEnc] = accountKeyEnc
                it[updatedAt] = now.atOffset(ZoneOffset.UTC)
            }
        }
    }

    fun saveCertInTransaction(
        accountKeyEnc: ByteArray,
        issued: SiteHubCerts.IssuedCert,
        keyPemEnc: ByteArray,
        now: Instant,
    ) {
        val utcNow = now.atOffset(ZoneOffset.UTC)
        val existing = findCertInTransaction()
        if (existing == null) {
            SiteHubCertTable.insert {
                it[id] = CERT_SINGLETON_ID
                it[SiteHubCertTable.accountKeyEnc] = accountKeyEnc
                it[SiteHubCertTable.certPem] = issued.certPem
                it[SiteHubCertTable.keyPemEnc] = keyPemEnc
                it[SiteHubCertTable.notBefore] = issued.notBefore.atOffset(ZoneOffset.UTC)
                it[SiteHubCertTable.notAfter] = issued.notAfter.atOffset(ZoneOffset.UTC)
                it[issuedAt] = utcNow
                it[updatedAt] = utcNow
            }
        } else {
            SiteHubCertTable.update({ SiteHubCertTable.id eq CERT_SINGLETON_ID }) {
                it[SiteHubCertTable.certPem] = issued.certPem
                it[SiteHubCertTable.keyPemEnc] = keyPemEnc
                it[SiteHubCertTable.notBefore] = issued.notBefore.atOffset(ZoneOffset.UTC)
                it[SiteHubCertTable.notAfter] = issued.notAfter.atOffset(ZoneOffset.UTC)
                it[issuedAt] = utcNow
                it[updatedAt] = utcNow
            }
        }
    }

    private fun toLaptop(row: ResultRow) =
        SiteHubLaptopRecord(
            id = row[SiteHubLaptopTable.id],
            name = row[SiteHubLaptopTable.name],
            pairedBy = row[SiteHubLaptopTable.pairedBy],
            pairedAt = row[SiteHubLaptopTable.pairedAt].toInstant(),
            lastSeenAt = row[SiteHubLaptopTable.lastSeenAt]?.toInstant(),
            revokedAt = row[SiteHubLaptopTable.revokedAt]?.toInstant(),
            revokedBy = row[SiteHubLaptopTable.revokedBy],
        )
}
