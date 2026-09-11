package com.companyb.companyapp.notification

import java.util.UUID

/**
 * Named notification-read seams for cross-owner consumers (map #533 #550): session-detail
 * bearer check and relief expiry source lookup. Thin delegations to the internal notification
 * store — not a facade over every method. Service-to-service calls need no architecture
 * allowlist entry; direct store imports from other owners stay banned.
 */
object NotificationReads {
    /** Bearer check for the session-detail read — the notification row plus a current read window. */
    fun existsForSessionAndUser(
        sessionId: UUID,
        userId: UUID,
    ): Boolean = NotificationRepository.existsForSessionAndUser(sessionId, userId)

    /** Original ping list for relief expiry — distinct recipients holding the source row. */
    fun findUsersBySource(
        eventType: String,
        sourceId: UUID,
    ): List<UUID> = NotificationRepository.findUsersBySource(eventType, sourceId)
}
