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
 * startup for every user currently flagged INACTIVE. Revocation is
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
 * Entries are evicted once they are older than [TOKEN_MAX_AGE] because any
 * JWT that could belong to them is guaranteed to have expired by then (JWT
 * max expiry is 24h).
 */
@Suppress("TooManyFunctions")
object DenyList {
    private val logger = KotlinLogging.logger {}

    private const val TOKEN_MAX_AGE_HOURS = 24L
    private val TOKEN_MAX_AGE: Duration = Duration.ofHours(TOKEN_MAX_AGE_HOURS)

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

    /** Populate the deny list from all users currently INACTIVE in the database. */
    fun loadInactiveUsers() {
        val now = Instant.now()
        val ids = UserRepository.findInactiveUserIds()
        ids.forEach { denied[it] = now }
        logger.info { "[DENY-LIST] Loaded ${ids.size} inactive user(s) into deny list" }
    }

    /** Remove all entries older than [TOKEN_MAX_AGE]. */
    fun evictExpired() = evictExpiredAt(Instant.now())

    internal fun denyAt(
        userId: UUID,
        at: Instant,
    ) {
        denied[userId] = at
        logger.info { "[DENY-LIST] User ${userId.toString().maskUUID()} added to deny list" }
    }

    internal fun isDeniedAt(
        userId: UUID,
        tokenIssuedAt: Instant,
        now: Instant,
    ): Boolean {
        val deniedAt = denied[userId]
        if (deniedAt == null || isExpired(deniedAt, now)) {
            if (deniedAt != null) denied.remove(userId)
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
}
