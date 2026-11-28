package com.companyb.companyapp.branchday

import com.companyb.companyapp.contracts.branchday.SiteHubLaptopSummary
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.observability.traced
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.IOException
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages paired site hub laptops and the single server-owned Let's Encrypt certificate for
 * `hub.kiel.party`. Laptops never hold Cloudflare DNS credentials or run ACME themselves:
 * any active staff member pairs a laptop, and the laptop pulls the current certificate here.
 */
object SiteHubCerts {
    const val HUB_NAME = "hub.kiel.party"
    const val PAIRS_PER_HOUR = 5

    private const val HOUR_SECONDS = 3600L
    private const val RENEW_NUMERATOR = 2L
    private const val RENEW_DENOMINATOR = 3L
    private const val ACCOUNT_KEY_PURPOSE = "site-hub-acme-account"
    private const val LEAF_KEY_PURPOSE = "site-hub-leaf-key"
    private const val DEFAULT_PROPAGATION_SECONDS = 20L
    private val DEFAULT_PROPAGATION_WAIT: Duration = Duration.ofSeconds(DEFAULT_PROPAGATION_SECONDS)

    /** Writes and deletes TXT records in the kiel.party zone. */
    interface Txt {
        fun put(
            name: String,
            value: String,
        )

        fun remove(
            name: String,
            value: String,
        )
    }

    /** Obtains a public certificate for [domain] via DNS-01 using [txt]. */
    interface CertIssuer {
        fun obtain(
            domain: String,
            accountKey: KeyPair,
            txt: Txt,
            propagationWait: Duration,
        ): IssuedCert
    }

    data class IssuedCert(
        val certPem: String,
        val keyPem: String,
        val notBefore: Instant,
        val notAfter: Instant,
    )

    sealed interface PairResult {
        data class Paired(
            val id: UUID,
            val token: String,
        ) : PairResult

        data object TooMany : PairResult
    }

    sealed interface CertResult {
        data class Ready(
            val cert: IssuedCert,
        ) : CertResult

        data object BadToken : CertResult

        data object NotSetUp : CertResult

        data class Failed(
            val reason: String,
        ) : CertResult
    }

    @Volatile
    private var txt: Txt? = null

    @Volatile
    private var issuer: CertIssuer = AcmeClient()

    @Volatile
    private var propagationWait: Duration = DEFAULT_PROPAGATION_WAIT

    private val pairTimes = ConcurrentHashMap<UUID, MutableList<Long>>()
    private val certLock = Any()

    fun configure(
        value: Txt?,
        certIssuer: CertIssuer = AcmeClient(),
        wait: Duration = DEFAULT_PROPAGATION_WAIT,
    ) {
        txt = value
        issuer = certIssuer
        propagationWait = wait
    }

    /** Clears per-user pairing counts (tests). */
    fun forgetCounts() = pairTimes.clear()

    /** Pairs a new site hub laptop for [pairedBy], returning its id and one-time revocable token. */
    fun pair(
        pairedBy: UUID,
        name: String,
        now: Instant = Instant.now(),
    ): PairResult =
        traced("SiteHubCerts.pair") {
            val times = pairTimes.computeIfAbsent(pairedBy) { mutableListOf() }
            val allowed =
                synchronized(times) {
                    times.removeAll { now.epochSecond - it >= HOUR_SECONDS }
                    if (times.size >= PAIRS_PER_HOUR) {
                        false
                    } else {
                        times += now.epochSecond
                        true
                    }
                }
            if (!allowed) return@traced PairResult.TooMany
            val id = UUID.randomUUID()
            val token = SiteHub.newLaptopToken()
            val hash = SiteHub.hashToken(token)
            transaction {
                SiteHubStore.insertLaptopInTransaction(id, name, hash, pairedBy, now)
            }
            PairResult.Paired(id, token)
        }

    /** Lists all paired site hub laptops (active and revoked), newest first. */
    fun listLaptops(): List<SiteHubLaptopSummary> {
        val records = SiteHubStore.listLaptops()
        val names = AccountReads.findDisplayNamesByIds(records.map { it.pairedBy }.distinct())
        return records.map { r ->
            SiteHubLaptopSummary(
                id = r.id.toString(),
                name = r.name,
                pairedBy = r.pairedBy.toString(),
                pairedByName = names[r.pairedBy] ?: "",
                pairedAt = r.pairedAt.toString(),
                lastSeenAt = r.lastSeenAt?.toString(),
                revokedAt = r.revokedAt?.toString(),
                revokedBy = r.revokedBy?.toString(),
            )
        }
    }

    /** Revokes [laptopId] so its token is immediately rejected; returns false if no such laptop exists. */
    fun revoke(
        laptopId: UUID,
        revokedBy: UUID,
        now: Instant = Instant.now(),
    ): Boolean =
        traced("SiteHubCerts.revoke") {
            transaction {
                SiteHubStore.revokeLaptopInTransaction(laptopId, revokedBy, now)
            }
        }

    /**
     * Authenticates a paired laptop by [token], records its last-seen time, and returns the current
     * `hub.kiel.party` certificate, issuing or renewing it at two thirds of its life under a single-flight lock.
     */
    fun cert(
        token: String,
        now: Instant = Instant.now(),
    ): CertResult {
        if (token.isBlank()) return CertResult.BadToken
        authenticateLaptop(token, now) ?: return CertResult.BadToken
        val cached = loadUsableCert(now)
        if (cached != null && now.isBefore(dueAt(cached.notBefore, cached.notAfter))) {
            return CertResult.Ready(cached)
        }
        return synchronized(certLock) {
            obtainOrReuseLocked(now)
        }
    }

    /** When a certificate issued for `[notBefore, notAfter]` is due for renewal (two thirds into its life). */
    fun dueAt(
        notBefore: Instant,
        notAfter: Instant,
    ): Instant {
        val life = Duration.between(notBefore, notAfter)
        return notBefore.plus(life.multipliedBy(RENEW_NUMERATOR).dividedBy(RENEW_DENOMINATOR))
    }

    private fun obtainOrReuseLocked(now: Instant): CertResult {
        val current = loadUsableCert(now)
        val dns = txt
        return when {
            current != null && now.isBefore(dueAt(current.notBefore, current.notAfter)) -> {
                CertResult.Ready(current)
            }

            dns == null -> {
                if (current != null) CertResult.Ready(current) else CertResult.NotSetUp
            }

            else -> {
                issueOrFallback(dns, current, now)
            }
        }
    }

    private fun issueOrFallback(
        dns: Txt,
        fallback: IssuedCert?,
        now: Instant,
    ): CertResult =
        try {
            val (accountKeyEnc, accountKey) = loadOrCreateAccountKey(now)
            val issued = issuer.obtain(HUB_NAME, accountKey, dns, propagationWait)
            saveIssuedCert(accountKeyEnc, issued, now)
            logger.info { "[SITE-HUB] issued $HUB_NAME certificate valid until ${issued.notAfter}" }
            CertResult.Ready(issued)
        } catch (e: IOException) {
            logger.warn { "[SITE-HUB] certificate issuance failed: ${e.message}" }
            if (fallback != null) {
                CertResult.Ready(fallback)
            } else {
                CertResult.Failed(e.message ?: "certificate request failed")
            }
        }

    private fun authenticateLaptop(
        token: String,
        now: Instant,
    ): SiteHubLaptopRecord? =
        traced("SiteHubCerts.authenticateLaptop") {
            val hash = SiteHub.hashToken(token)
            transaction {
                val laptop = SiteHubStore.findActiveLaptopByHashInTransaction(hash)
                if (laptop != null) SiteHubStore.touchLastSeenInTransaction(laptop.id, now)
                laptop
            }
        }

    private fun loadUsableCert(now: Instant): IssuedCert? {
        val row = SiteHubStore.findCert() ?: return null
        val pem = row.certPem ?: return null
        val encKey = row.keyPemEnc ?: return null
        val nb = row.notBefore ?: return null
        val na = row.notAfter ?: return null
        if (!now.isBefore(na)) return null
        val keyPem = SiteHub.unseal(LEAF_KEY_PURPOSE, encKey).toString(Charsets.UTF_8)
        return IssuedCert(pem, keyPem, nb, na)
    }

    private fun loadOrCreateAccountKey(now: Instant): Pair<ByteArray, KeyPair> =
        traced("SiteHubCerts.loadOrCreateAccountKey") {
            transaction {
                val existing = SiteHubStore.findCertInTransaction()
                if (existing != null) {
                    val decoded = AcmeClient.decodeKeyPair(SiteHub.unseal(ACCOUNT_KEY_PURPOSE, existing.accountKeyEnc))
                    existing.accountKeyEnc to decoded
                } else {
                    val pair = AcmeClient.generateEcKeyPair()
                    val enc = SiteHub.seal(ACCOUNT_KEY_PURPOSE, AcmeClient.encodeKeyPair(pair))
                    SiteHubStore.saveAccountKeyInTransaction(enc, now)
                    enc to pair
                }
            }
        }

    private fun saveIssuedCert(
        accountKeyEnc: ByteArray,
        issued: IssuedCert,
        now: Instant,
    ) {
        traced("SiteHubCerts.saveIssuedCert") {
            val keyEnc = SiteHub.seal(LEAF_KEY_PURPOSE, issued.keyPem.toByteArray())
            transaction {
                SiteHubStore.saveCertInTransaction(accountKeyEnc, issued, keyEnc, now)
            }
        }
    }
}
