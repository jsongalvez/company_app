package com.companyb.companyapp.identity

import java.util.UUID

/**
 * Named account-read seams for cross-owner consumers: workforce delegate
 * eligibility and assignment pre-checks, workforce/HTTP notification copy. Thin delegations
 * to the internal account store — not a facade over every method. Service-to-service calls
 * need no architecture allowlist entry; direct store imports from other owners stay banned.
 */
object AccountReads {
    /** Account-existence pre-check for the workforce assignment command. */
    fun userExists(userId: UUID): Boolean = UserRepository.existsById(userId)

    /**
     * ACTIVE-status check for roster-path practitioner adds — status-only,
     * runs on the caller's command transaction. Never membership: ACTIVE outsiders
     * (relief) stay eligible for the 999 fallback.
     */
    fun isActiveUserInTransaction(userId: UUID): Boolean = UserRepository.isActiveUserInTransaction(userId)

    /** An Accountant never gets a home branch (user, message 13065) — the assignment command asks this. */
    fun isAccountantInTransaction(userId: UUID): Boolean =
        UserRepository.holdsRoleInTransaction(userId, ACCOUNTANT_ROLE)

    /** Delegate-eligibility read — runs on the caller's command transaction. */
    fun isActiveStaffInTransaction(userId: UUID): Boolean = UserRepository.isActiveStaffInTransaction(userId)

    /** Display names for notification copy — batched account read, same semantics. */
    fun findDisplayNamesByIds(ids: Collection<UUID>): Map<UUID, String> = UserRepository.findDisplayNamesByIds(ids)
}
