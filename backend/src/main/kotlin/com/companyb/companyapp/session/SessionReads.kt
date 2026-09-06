package com.companyb.companyapp.session

import java.util.UUID

/**
 * Named session-read seams for cross-owner consumers (map #533 #542): commerce same-day
 * linkage, remittance line validation, dashboard detail, authorization branch-day resolution,
 * and the client anonymize PENDING guard. Thin delegations to the internal session store —
 * not a facade over every method. Service-to-service calls need no architecture allowlist
 * entry; direct store imports from other owners stay banned.
 */
object SessionReads {
    /** Existence + branch-day read for same-day linkage, detail, and capability gates. */
    fun findById(sessionId: UUID): Session? = SessionRepository.findById(sessionId)

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(sessionId: UUID): Session? = SessionRepository.findByIdInTransaction(sessionId)

    /** PENDING-session guard for the client anonymize command — runs on the caller's transaction. */
    fun hasActivePendingSessionInTransaction(
        clientId: UUID,
        excludedSessionId: UUID? = null,
    ): Boolean = SessionRepository.hasActivePendingSessionInTransaction(clientId, excludedSessionId)
}
