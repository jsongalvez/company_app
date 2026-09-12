package com.companyb.companyapp.identity

import java.util.UUID

/**
 * Named account-read seams for cross-owner consumers (map #533 #537): workforce delegate
 * eligibility and assignment pre-checks, workforce/HTTP notification copy. Thin delegations
 * to the internal account store — not a facade over every method. Service-to-service calls
 * need no architecture allowlist entry; direct store imports from other owners stay banned.
 */
object AccountReads {
    /** Account-existence pre-check for the workforce assignment command. */
    fun userExists(userId: UUID): Boolean = UserRepository.existsById(userId)

    /**
     * ACTIVE-status check for roster-path practitioner adds (#926) — status-only,
     * runs on the caller's command transaction. Never membership: ACTIVE outsiders
     * (relief) stay eligible for the 999 fallback.
     */
    fun isActiveUserInTransaction(userId: UUID): Boolean = UserRepository.isActiveUserInTransaction(userId)

    /** Delegate-eligibility read — runs on the caller's command transaction. */
    fun isActiveManagerInTransaction(userId: UUID): Boolean = UserRepository.isActiveManagerInTransaction(userId)

    /** Display names for notification copy (#358) — batched account read, same semantics. */
    fun findDisplayNamesByIds(ids: Collection<UUID>): Map<UUID, String> = UserRepository.findDisplayNamesByIds(ids)
}
