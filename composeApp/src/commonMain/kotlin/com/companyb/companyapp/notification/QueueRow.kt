package com.companyb.companyapp.notification

import com.companyb.companyapp.contracts.notification.NotificationResponse

/**
 * One notification-queue row (#679): the server row plus whether this visit already marked
 * it read. Rows read this visit stay at their queue position with [readThisVisit] set —
 * the list never reorders under the pointer; an explicit refresh reconciles groups.
 */
data class QueueRow(
    val notification: NotificationResponse,
    val readThisVisit: Boolean,
)
