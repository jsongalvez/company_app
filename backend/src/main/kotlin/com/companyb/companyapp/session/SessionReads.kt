package com.companyb.companyapp.session

import java.util.UUID

/**
 * Named session-read seams for cross-owner consumers (map #533 #542): commerce same-day
 * linkage, remittance line validation, dashboard detail, authorization branch-day resolution,
 * and the client anonymize PENDING guard + free-text scrub (#908). Thin delegations to the internal session store —
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
     * Scrubbed free-text census for one client's anonymization (#908): the
     * live session ids nulled plus the practitioner-row ids whose audit
     * payloads the caller must redact (removed rows join via history census).
     */
    data class SessionTextScrub(
        val sessionIds: List<UUID>,
        val practitionerIds: List<UUID>,
    )

    /**
     * Anonymization free-text scrub (#908) — nulls session + practitioner free
     * text for every session of one client on the caller's command
     * transaction. Writes no audit row of its own (the #524 no-new-events
     * precedent); the caller redacts the matching session audit payloads
     * through the audit seam before recording the anonymization event.
     */
    fun scrubSessionTextForClientInTransaction(clientId: UUID): SessionTextScrub {
        val sessionIds = SessionRepository.findSessionIdsForClientInTransaction(clientId)
        val practitionerIds = SessionPractitionerRepository.findIdsForSessionsInTransaction(sessionIds)
        SessionRepository.scrubFreeTextForClientInTransaction(clientId)
        SessionPractitionerRepository.scrubRemarksForSessionsInTransaction(sessionIds)
        return SessionTextScrub(sessionIds, practitionerIds)
    }

    /**
     * Void-state read for remittance line validation (#750) — runs on the caller's open
     * transaction. Delegates to the internal store; authority is `active_session_voids`
     * (unvoided rows drop out of the view).
     */
    fun isVoidedInTransaction(sessionId: UUID): Boolean = SessionRepository.isVoidedInTransaction(sessionId)
}
