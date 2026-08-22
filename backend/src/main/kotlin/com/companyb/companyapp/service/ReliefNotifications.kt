package com.companyb.companyapp.service

import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.findDisplayNamesByIds
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.service.branchday.BranchDayService
import java.time.LocalDate
import java.util.UUID

/**
 * Relief notification events (#358, rules from the #352 resolution). Every function runs
 * inside the owning command's transaction — the broadcast commits with the change it
 * announces (ADR-0024 shape; a failing insert rolls the mutation back).
 *
 * Audience (owner Q5): every member of the branch, clocked in or not, one message per
 * person. Outcomes also reach the requester — "let ben know carla's response in addition
 * to ana". Expiry reaches the original ping list: the users holding the request's
 * RELIEF_REQUESTED rows. Cancellation is deliberately silent — not in the #352 event list.
 */
internal object ReliefNotifications {
    const val REQUESTED = "RELIEF_REQUESTED"
    const val GRANTED = "RELIEF_REQUEST_GRANTED"
    const val DENIED = "RELIEF_REQUEST_DENIED"
    const val EXPIRED = "RELIEF_REQUEST_EXPIRED"
    const val INVITE_ACCEPTED = "RELIEF_INVITE_ACCEPTED"
    const val INVITE_DECLINED = "RELIEF_INVITE_DECLINED"

    /** A relief request went out to the whole branch ("today" or the named date). */
    fun requestCreated(
        requestId: UUID,
        requesterId: UUID,
        branchId: UUID,
        branchName: String,
        date: LocalDate,
    ) {
        val names = findDisplayNamesByIds(listOf(requesterId))
        val requesterName = names[requesterId] ?: "A user"
        broadcast(
            eventType = REQUESTED,
            sourceId = requestId,
            branchId = branchId,
            date = date,
            recipients = members(branchId),
            message = "$requesterName requested relief duty at $branchName ${dayPhrase(date)}",
        )
    }

    /** Grant/deny outcome: everyone hears it, including who acted and the requester. */
    fun requestOutcome(
        eventType: String,
        requestId: UUID,
        actorId: UUID,
        requesterId: UUID,
        branchId: UUID,
        branchName: String,
        date: LocalDate,
    ) {
        val names = findDisplayNamesByIds(listOf(actorId, requesterId))
        val actorName = names[actorId] ?: "A member"
        val requesterName = names[requesterId] ?: "a user"
        val verb = if (eventType == GRANTED) "granted" else "denied"
        broadcast(
            eventType = eventType,
            sourceId = requestId,
            branchId = branchId,
            date = date,
            recipients = members(branchId) + requesterId,
            message =
                "$actorName $verb $requesterName's relief duty request " +
                    "at $branchName ${dayPhrase(date)}",
        )
    }

    /** Invite accept/decline broadcast ("everyone is notified when the person accepts or not"). */
    fun inviteResponded(
        eventType: String,
        inviteId: UUID,
        inviteeId: UUID,
        branchId: UUID,
        branchName: String,
        date: LocalDate,
    ) {
        val inviteeName = findDisplayNamesByIds(listOf(inviteeId))[inviteeId] ?: "A user"
        val verb = if (eventType == INVITE_ACCEPTED) "accepted" else "declined"
        broadcast(
            eventType = eventType,
            sourceId = inviteId,
            branchId = branchId,
            date = date,
            recipients = members(branchId),
            message = "$inviteeName $verb the relief invite at $branchName ${dayPhrase(date)}",
        )
    }

    /**
     * Unanswered requests announce themselves when their Branch Day ends (#358). The notice
     * goes to the original ping list only — resolved from the stored RELIEF_REQUESTED rows.
     */
    fun requestExpired(
        requestId: UUID,
        requesterId: UUID,
        branchId: UUID,
        branchName: String,
        date: LocalDate,
    ): Int {
        val originalPingList = NotificationRepository.findUsersBySource(REQUESTED, requestId)
        if (originalPingList.isEmpty()) return 0
        val requesterName =
            findDisplayNamesByIds(listOf(requesterId))[requesterId] ?: "A user"
        return broadcast(
            eventType = EXPIRED,
            sourceId = requestId,
            branchId = branchId,
            date = date,
            recipients = originalPingList,
            message =
                "$requesterName's relief duty request at $branchName on $date " +
                    "expired unanswered",
        )
    }

    private fun members(branchId: UUID): List<UUID> =
        UserBranchAssignmentRepository.findActiveByBranch(branchId).map { it.userId }

    /** Same-day messages say today; future/past ones name the date (owner Q5). */
    private fun dayPhrase(date: LocalDate): String =
        if (date == BranchDayService.currentOperationalDate()) "for today" else "on $date"

    private fun broadcast(
        eventType: String,
        sourceId: UUID,
        branchId: UUID,
        date: LocalDate,
        recipients: Collection<UUID>,
        message: String,
    ): Int =
        NotificationRepository.insertBatch(
            recipients.distinct().map { recipient ->
                NotificationCreateParams(
                    sessionId = null,
                    userId = recipient,
                    branchId = branchId,
                    message = message,
                    eventType = eventType,
                    sourceId = sourceId,
                    targetDate = date,
                )
            },
        )
}
