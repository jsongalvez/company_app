package com.companyb.companyapp.client

import java.util.UUID

/**
 * Named client-read seams for cross-owner consumers (map #533 #541): session create,
 * status/price/void/unvoid and preview. Thin delegations to the internal client store —
 * not a facade over every method. Service-to-service calls need no architecture allowlist
 * entry; direct store imports from other owners stay banned.
 */
object ClientReads {
    /** Client-first row lock for session commands — runs on the caller's command transaction. */
    fun acquireLockInTransaction(clientId: UUID): Client? = ClientRepository.acquireLockInTransaction(clientId)

    /** Existence + anonymization-state read for session preview. */
    fun findById(clientId: UUID): Client? = ClientRepository.findById(clientId)
}
