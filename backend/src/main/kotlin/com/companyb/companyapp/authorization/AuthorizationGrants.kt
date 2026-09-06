package com.companyb.companyapp.authorization

import java.util.UUID

/**
 * Narrow grant seam for workforce commands (#538): the relief request/accept/revoke and
 * delegate assign/revoke flows write grants only through here — thin delegations to the
 * internal [GrantStore], not a facade over every store method (the #537 `AccountReads`
 * precedent). Service-to-service calls need no architecture allowlist entry; direct
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

    /**
     * Writes the branch-scoped delegate grant for the delegate-assign flow.
     * Runs on the caller's command transaction.
     */
    fun grantDelegateCapabilityInTransaction(
        targetUserId: UUID,
        branchId: UUID,
        delegateId: UUID,
        capabilityId: UUID,
    ) = GrantStore.grantDelegateCapabilityInTransaction(targetUserId, branchId, delegateId, capabilityId)

    /** Closes the delegate grant window for the delegate-revoke flow. Runs on the caller's transaction. */
    fun closeDelegateCapabilityInTransaction(delegateId: UUID) =
        GrantStore.closeDelegateCapabilityInTransaction(delegateId)
}
