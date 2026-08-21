package com.companyb.companyapp.auth

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.between
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory deny list of user IDs whose access must be revoked immediately,
 * independent of (and ahead of) any database lookup.
 *
 * A user is added here when deactivated (see user deactivation flow) and at
 * startup from each user's persisted revocation boundary. Revocation is
 * issuance-time-scoped: a token is denied iff it was issued at or before the
 * deny time, so tokens issued after the deny (fresh logins) verify normally
 * while pre-deny tokens stay dead even after reactivation or re-login.
 *
 * Precision caveat: JWT `iat` is second-precision (NumericDate), so a token
 * issued in the same second as the deny is indistinguishable from a pre-deny
 * token and is treated as denied — denial wins the ambiguity (a same-second
 * pre-deny token must stay dead; a same-second fresh login self-heals on the
 * next attempt).
 *
 * Entries are evicted once they are older than [TOKEN_MAX_AGE] — the 24h JWT
 * max expiry plus the 60s `acceptLeeway` JwtService applies to `exp` — because
 * any JWT that could belong to them is guaranteed to have expired by then.
 *
 * The list is a process-local cache; persisted boundaries make revocation survive
 * restart and Reactivate.
 */
@Suppress("TooManyFunctions")
object DenyList {
    private val logger = KotlinLogging.logger {}

    private const val TOKEN_MAX_AGE_HOURS = 24L
    private const val VERIFIER_LEEWAY_SECONDS = 60L
    private val TOKEN_MAX_AGE: Duration =
        Duration.ofHours(TOKEN_MAX_AGE_HOURS).plusSeconds(VERIFIER_LEEWAY_SECONDS)

    private val denied = ConcurrentHashMap<UUID, Instant>()

    /** Add a user to the deny list, blocking tokens issued at or before now. */
    fun deny(userId: UUID) = denyAt(userId, Instant.now())

    /**
     * Returns true if a token for [userId] issued at [tokenIssuedAt] is currently denied.
     * Expired entries are evicted lazily.
     */
    fun isDenied(
        userId: UUID,
        tokenIssuedAt: Instant,
    ): Boolean = isDeniedAt(userId, tokenIssuedAt, Instant.now())

    /** Populate the deny list from persisted JWT revocation boundaries. */
    fun loadPersistedRevocations() {
        val boundaries = UserRepository.findJwtRevocationBoundaries()
        boundaries.forEach { (userId, boundary) -> updateBoundary(userId, boundary) }
        logger.info { "[DENY-LIST] Loaded ${boundaries.size} persisted revocation boundary(ies)" }
    }

    /** Remove all entries older than [TOKEN_MAX_AGE]. */
    fun evictExpired() = evictExpiredAt(Instant.now())

    internal fun denyAt(
        userId: UUID,
        at: Instant,
    ) {
        updateBoundary(userId, at)
        logger.info { "[DENY-LIST] User ${userId.toString().maskUUID()} added to deny list" }
    }

    internal fun isDeniedAt(
        userId: UUID,
        tokenIssuedAt: Instant,
        now: Instant,
    ): Boolean {
        val deniedAt = denied[userId]
        if (deniedAt == null || isExpired(deniedAt, now)) {
            // Conditional remove: never delete a fresher entry written between
            // the read above and this removal.
            denied.remove(userId, deniedAt)
            return false
        }
        return !tokenIssuedAt.isAfter(deniedAt)
    }

    internal fun evictExpiredAt(now: Instant) {
        denied.entries.removeIf { isExpired(it.value, now) }
    }

    internal fun size(): Int = denied.size

    internal fun clear() = denied.clear()

    private fun isExpired(
        deniedAt: Instant,
        now: Instant,
    ): Boolean = Duration.between(deniedAt, now) >= TOKEN_MAX_AGE

    private fun updateBoundary(
        userId: UUID,
        boundary: Instant,
    ) {
        denied.compute(userId) { _, existing ->
            if (existing == null || boundary.isAfter(existing)) boundary else existing
        }
    }
}
