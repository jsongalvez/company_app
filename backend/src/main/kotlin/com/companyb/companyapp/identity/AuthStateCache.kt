package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.identity.UserStatus
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The three `app_user` columns that decide whether a token is still honoured. */
internal data class AuthState(
    val status: UserStatus,
    val jwtRevokedAt: OffsetDateTime?,
    val credentialVersion: Long,
)

/**
 * Short-lived, in-process copy of each user's [AuthState], so an authenticated request
 * does not query `app_user` every time. The token itself is never cached, only the row,
 * so a revoked or rotated credential is refused whatever token presents it.
 *
 * Every writer of those columns calls [invalidateAfterCommit], so revocation takes
 * effect on the next request. The [TTL_NANOS] only bounds a write this process never
 * sees (a second app instance, a manual SQL edit). A load that overlaps an invalidation
 * is not stored, because it may have read the row before the write committed.
 */
internal object AuthStateCache {
    private const val TTL_NANOS = 5_000_000_000L
    internal const val MAX_ENTRIES = 10_000

    private class Entry(
        val state: AuthState,
        val expiresAtNanos: Long,
    )

    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val epoch = AtomicLong()

    internal var nanoTime: () -> Long = System::nanoTime

    fun get(
        userId: UUID,
        load: () -> AuthState?,
    ): AuthState? {
        val cached = entries[userId]?.takeIf { nanoTime() - it.expiresAtNanos < 0 }
        if (cached != null) return cached.state
        val epochBeforeLoad = epoch.get()
        val state = load()
        if (state != null && epoch.get() == epochBeforeLoad) store(userId, state)
        return state
    }

    fun invalidate(userId: UUID) {
        entries.remove(userId)
        epoch.incrementAndGet()
    }

    /** Drops [userId] once the current transaction commits; a rollback leaves the cache alone. */
    fun invalidateAfterCommit(userId: UUID) {
        TransactionManager.current().registerInterceptor(
            object : StatementInterceptor {
                override fun afterCommit(transaction: Transaction) = invalidate(userId)
            },
        )
    }

    fun size(): Int = entries.size

    fun clear() {
        entries.clear()
        epoch.incrementAndGet()
    }

    private fun store(
        userId: UUID,
        state: AuthState,
    ) {
        val now = nanoTime()
        if (entries.size >= MAX_ENTRIES) {
            entries.values.removeIf { now - it.expiresAtNanos >= 0 }
            if (entries.size >= MAX_ENTRIES) entries.clear()
        }
        entries[userId] = Entry(state, now + TTL_NANOS)
    }
}
