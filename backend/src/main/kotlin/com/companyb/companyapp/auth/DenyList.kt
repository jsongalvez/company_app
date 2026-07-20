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
 * startup for every user currently flagged INACTIVE. Entries are evicted once
 * they are older than [TOKEN_MAX_AGE] because any JWT that could belong to them
 * is guaranteed to have expired by then (JWT max expiry is 24h).
 */
@Suppress("TooManyFunctions")
object DenyList {
    private val logger = KotlinLogging.logger {}

    private const val TOKEN_MAX_AGE_HOURS = 24L
    private val TOKEN_MAX_AGE: Duration = Duration.ofHours(TOKEN_MAX_AGE_HOURS)

    private val denied = ConcurrentHashMap<UUID, Instant>()

    /** Add a user to the deny list, blocking it immediately. */
    fun deny(userId: UUID) = denyAt(userId, Instant.now())

    /** Remove a user from the deny list, restoring access immediately. */
    fun allow(userId: UUID) {
        denied.remove(userId)?.let {
            logger.info { "[DENY-LIST] User ${userId.toString().maskUUID()} removed from deny list" }
        }
    }

    /** Returns true if the user is currently denied access. Expired entries are evicted lazily. */
    fun isDenied(userId: UUID): Boolean = isDeniedAt(userId, Instant.now())

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
        now: Instant,
    ): Boolean {
        val deniedAt = denied[userId] ?: return false
        val expired = isExpired(deniedAt, now)
        if (expired) denied.remove(userId)
        return !expired
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
