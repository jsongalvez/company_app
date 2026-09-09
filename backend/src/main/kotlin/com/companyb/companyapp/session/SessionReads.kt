package com.companyb.companyapp.session

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

    /**
     * Void-state read for remittance line validation (#750) — runs on the caller's open
     * transaction. Delegates to the internal store; authority is `active_session_voids`
     * (unvoided rows drop out of the view).
     */
    fun isVoidedInTransaction(sessionId: UUID): Boolean = SessionRepository.isVoidedInTransaction(sessionId)

    /** Non-transactional wrapper for void-state reads outside a command transaction. */
    fun isVoided(sessionId: UUID): Boolean =
        transaction {
            isVoidedInTransaction(sessionId)
        }
}
