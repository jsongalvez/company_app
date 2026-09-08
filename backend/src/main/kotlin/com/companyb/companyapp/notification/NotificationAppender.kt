package com.companyb.companyapp.notification

/**
 * Named notification-append seam for cross-owner producers (map #533 #550): relief event
 * broadcasts and appointment sweeps outside this owner. Thin delegation to the internal
 * notification store — not a facade over every method. Service-to-service calls need no
 * architecture allowlist entry; direct store imports from other owners stay banned.
 *
 * Relief wording/event intent stays workforce-owned (ReliefNotifications builds the message
 * and audience); this seam only persists the occurrence-keyed rows. Notifications carry no
 * audit row (system read-state, not a §12.1 covered table).
 */
object NotificationAppender {
    /**
     * Occurrence-keyed batch append — relief broadcasts call this inside their command
     * transaction so the rows commit atomically with the change they announce (Exposed joins
     * the caller's transaction when one is open; jobs/sweeps without an outer transaction get
     * their own). No audit row (system read-state, not a §12.1 covered table).
     */
    fun append(params: List<NotificationCreateParams>): Int = NotificationRepository.insertBatch(params)

    /**
     * Store operation for the owning command (ADR-0024, #602): runs on the caller's transaction
     * and opens none. Commands that already own a transaction (relief broadcasts, the
     * next-appointment sweep) call this so the rows commit atomically with the change they
     * announce. No audit row (system read-state, not a §12.1 covered table).
     */
    fun appendInTransaction(params: List<NotificationCreateParams>): Int =
        NotificationRepository.insertBatchInTransaction(params)
}
