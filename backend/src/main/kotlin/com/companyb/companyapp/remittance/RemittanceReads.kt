package com.companyb.companyapp.remittance

import java.util.UUID

/**
 * Named remittance-read seams for cross-owner consumers: authorization branch
 * resolution. Thin delegations to the internal remittance store — not a facade over
 * every method. Service-to-service calls need no architecture allowlist entry; direct
 * store imports from other owners stay banned.
 */
object RemittanceReads {
    /** Existence read for the remittance branch gate — runs on its own transaction. */
    fun findById(remittanceId: UUID): Remittance? = RemittanceRepository.findById(remittanceId)

    /** Undo guard for session status: is this session on an active remittance line? Caller's transaction. */
    fun isSessionIncludedInTransaction(sessionId: UUID): Boolean =
        RemittanceLineRepository.isSessionIncludedInTransaction(sessionId)
}
