package com.companyb.companyapp.authorization

import java.util.UUID

/**
 * Narrow grant seam for workforce commands: the relief request/accept/revoke flows write
 * relief days only through here — thin delegations to the
 * internal [GrantStore], not a facade over every store method. Service-to-service calls need no
 * architecture allowlist entry; direct
 * grant-table imports from other owners stay banned.
 */
object AuthorizationGrants {
    /**
     * Writes the day-scoped relief grant for the request-grant and invite-accept flows.
     * Runs on the caller's command transaction.
     */
    fun grantReliefCapabilityInTransaction(params: GrantReliefCapabilityParams) =
        GrantStore.grantReliefCapabilityInTransaction(params)

    /** Removes the day-scoped relief grant for the invite-revoke flow. Runs on the caller's transaction. */
    fun deleteReliefGrantBySourceIdInTransaction(
        userId: UUID,
        sourceId: UUID,
    ) = GrantStore.deleteReliefGrantBySourceIdInTransaction(userId, sourceId)
}
