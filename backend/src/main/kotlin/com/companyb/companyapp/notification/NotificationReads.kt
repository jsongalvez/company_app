package com.companyb.companyapp.notification

import java.util.UUID

/**
 * Named notification-read seams for cross-owner consumers: session-detail
 * bearer check and relief expiry source lookup. Thin delegations to the internal notification
 * store — not a facade over every method. Service-to-service calls need no architecture
 * allowlist entry; direct store imports from other owners stay banned.
 */
object NotificationReads {
    /** Original ping list for relief expiry — distinct recipients holding the source row. */
    fun findUsersBySource(
        eventType: String,
        sourceId: UUID,
    ): List<UUID> = NotificationRepository.findUsersBySource(eventType, sourceId)
}
