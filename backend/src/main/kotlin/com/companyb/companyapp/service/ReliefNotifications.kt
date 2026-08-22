package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.findDisplayNamesByIds
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.service.branchday.BranchDayService
import java.time.LocalDate
import java.util.UUID

/** Where + when a relief event happened; every broadcast names the branch and day. */
data class ReliefEventContext(
    val branchId: UUID,
    val branchName: String,
    val date: LocalDate,
) {
    companion object {
        fun of(branchDay: BranchDay): ReliefEventContext =
            ReliefEventContext(
                branchId = branchDay.branchId,
                branchName = BranchRepository.findById(branchDay.branchId)?.name ?: "branch",
                date = branchDay.date,
            )
    }
}

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

    // #359 — accepted-invite reminders: T-3 days, T-1 day, day-of (Manila calendar days).
    // sourceId = the invite; one marker row per (event, invite) makes job re-runs idempotent.
    const val REMINDER_3_DAYS = "RELIEF_REMINDER_3_DAYS"
    const val REMINDER_1_DAY = "RELIEF_REMINDER_1_DAY"
    const val REMINDER_DAY_OF = "RELIEF_REMINDER_DAY_OF"

    /** A relief request went out to the whole branch ("today" or the named date). */
    fun requestCreated(
        requestId: UUID,
        requesterId: UUID,
        context: ReliefEventContext,
    ) {
        val names = findDisplayNamesByIds(listOf(requesterId))
        val requesterName = names[requesterId] ?: "A user"
        broadcast(
            eventType = REQUESTED,
            sourceId = requestId,
            recipients = members(context.branchId),
            message = "$requesterName requested relief duty at ${context.branchName} ${dayPhrase(context.date)}",
            context = context,
        )
    }

    /** Grant/deny outcome: everyone hears it, including who acted and the requester. */
    fun requestOutcome(
        eventType: String,
        requestId: UUID,
        actorId: UUID,
        requesterId: UUID,
        context: ReliefEventContext,
    ) {
        val names = findDisplayNamesByIds(listOf(actorId, requesterId))
        val actorName = names[actorId] ?: "A member"
        val requesterName = names[requesterId] ?: "a user"
        val verb = if (eventType == GRANTED) "granted" else "denied"
        broadcast(
            eventType = eventType,
            sourceId = requestId,
            recipients = members(context.branchId) + requesterId,
            message =
                "$actorName $verb $requesterName's relief duty request " +
                    "at ${context.branchName} ${dayPhrase(context.date)}",
            context = context,
        )
    }

    /** Invite accept/decline broadcast ("everyone is notified when the person accepts or not"). */
    fun inviteResponded(
        eventType: String,
        inviteId: UUID,
        inviteeId: UUID,
        context: ReliefEventContext,
    ) {
        val inviteeName = findDisplayNamesByIds(listOf(inviteeId))[inviteeId] ?: "A user"
        val verb = if (eventType == INVITE_ACCEPTED) "accepted" else "declined"
        broadcast(
            eventType = eventType,
            sourceId = inviteId,
            recipients = members(context.branchId),
            message = "$inviteeName $verb the relief invite at ${context.branchName} ${dayPhrase(context.date)}",
            context = context,
        )
    }

    /**
     * Accepted-invite reminder (#359): goes to the invitee only — the branch stays quiet
     * until the duty day itself (#352 reminders ruling). The phrase follows the slot, not
     * a clock read: the day-of sweep says "for today", the earlier sweeps name the date.
     */
    fun inviteReminder(
        eventType: String,
        inviteId: UUID,
        inviteeId: UUID,
        context: ReliefEventContext,
    ): Int {
        val phrase = if (eventType == REMINDER_DAY_OF) "for today" else "on ${context.date}"
        return broadcast(
            eventType = eventType,
            sourceId = inviteId,
            recipients = listOf(inviteeId),
            message = "You have relief duty at ${context.branchName} $phrase",
            context = context,
        )
    }

    /**
     * Unanswered requests announce themselves when their Branch Day ends (#358). The notice
     * goes to the original ping list only — resolved from the stored RELIEF_REQUESTED rows.
     */
    fun requestExpired(
        requestId: UUID,
        requesterId: UUID,
        context: ReliefEventContext,
    ): Int {
        val originalPingList = NotificationRepository.findUsersBySource(REQUESTED, requestId)
        if (originalPingList.isEmpty()) return 0
        val requesterName =
            findDisplayNamesByIds(listOf(requesterId))[requesterId] ?: "A user"
        return broadcast(
            eventType = EXPIRED,
            sourceId = requestId,
            recipients = originalPingList,
            message =
                "$requesterName's relief duty request at ${context.branchName} on ${context.date} " +
                    "expired unanswered",
            context = context,
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
        recipients: Collection<UUID>,
        message: String,
        context: ReliefEventContext,
    ): Int =
        NotificationRepository.insertBatch(
            recipients.distinct().map { recipient ->
                NotificationCreateParams(
                    sessionId = null,
                    userId = recipient,
                    branchId = context.branchId,
                    message = message,
                    eventType = eventType,
                    sourceId = sourceId,
                    targetDate = context.date,
                )
            },
        )
}
