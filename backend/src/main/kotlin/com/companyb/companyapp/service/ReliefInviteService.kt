package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ReliefCandidate
import com.companyb.companyapp.repository.ReliefInviteRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.ReliefInvite
import com.companyb.companyapp.repository.model.ReliefInviteTable
import com.companyb.companyapp.repository.model.ReliefInviteView
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.util.UUID

/**
 * Branch-initiated relief invites (#159, the #160 build). The inviter is anyone with an
 * ACTIVE `user_branch_assignment` at the branch — the app's only membership record — and
 * the invitee is any ACTIVE user (capabilities ≠ assignments; a redundant grant is
 * harmless). One day per invite; accept writes the day-scoped grant immediately via the
 * shared relief-grant writer ([ReliefAccessRepository.grantReliefCapability]); day-state
 * is the expiry (a past/REMITTED day accept 400s — no cron).
 */
object ReliefInviteService {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a PENDING invite for [inviteeUserId] on [date] at [branchId].
     *
     * Day resolution is a resolve-or-create (NOT find-only): the invite is a real write
     * referencing the day row, and the invite use case is FUTURE-day planning — a lazy
     * day row for tomorrow usually does not exist yet, so find-only would 404 the
     * primary flow (the #158 find-only discipline governs gates, never writes; the
     * request flow's own grant path is the resolve-or-create precedent).
     *
     * @throws ForbiddenException when the caller lacks an active assignment at the branch,
     *   or the day is not OPEN and the caller lacks EDIT_PAST_DAY.
     * @throws ValidationException when the invitee is the caller, is not ACTIVE, or the day
     *   is REMITTED without a reason.
     * @throws ConflictException when the invitee already holds a PENDING/ACCEPTED invite or
     *   an active grant for the day (the per-person guard).
     */
    @Suppress("ThrowsCount")
    fun createInvite(
        callerId: UUID,
        branchId: UUID,
        inviteeUserId: UUID,
        date: LocalDate,
    ): ReliefInvite {
        requireActiveAssignment(callerId, branchId)

        if (inviteeUserId == callerId) {
            throw ValidationException("You cannot invite yourself")
        }
        if (!ReliefInviteRepository.isActiveUser(inviteeUserId)) {
            throw ValidationException("Invitee must be an active user")
        }

        val branchDay = BranchDayService.resolveOrCreate(branchId, date)
        val (_, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDay.id)

        if (
            ReliefInviteRepository.hasPendingOrAcceptedInvite(inviteeUserId, branchDay.id) ||
            ReliefInviteRepository.hasActiveGrant(inviteeUserId, branchDay.id)
        ) {
            throw ConflictException("This user already has a pending invite or active grant for the day")
        }

        val (invite, isNew) =
            ReliefInviteRepository.insert(
                id = UUID.randomUUID(),
                branchDayId = branchDay.id,
                invitedBy = callerId,
                invitee = inviteeUserId,
                auditFn = { created ->
                    AuditLogRepository.recordInsert(
                        tableName = ReliefInviteTable.tableName,
                        recordId = created.id,
                        changedBy = callerId,
                        branchId = branchId,
                        fields = ReliefInviteTable.auditFields(created),
                        isFlagged = isRemitted,
                    )
                },
            )

        if (!isNew) {
            // The partial unique index swallowed a concurrent duplicate — never a success
            // (the count-0 discipline: the swallowed constraint class is exactly the
            // per-person guard).
            throw ConflictException("This user already has a pending invite or active grant for the day")
        }

        logger.info {
            "[RELIEF-INVITE-CREATE] Invite ${checkNotNull(invite).id} created by $callerId for $inviteeUserId on $date"
        }
        return checkNotNull(invite)
    }

    /** The caller's own sent invites at [branchId] — same gate as create. */
    fun listSent(
        callerId: UUID,
        branchId: UUID,
    ): List<ReliefInviteView> {
        requireActiveAssignment(callerId, branchId)
        return ReliefInviteRepository.findSentByInviterAndBranch(callerId, branchId)
    }

    /** The caller's received invites — bearer-only (caller = invitee). */
    fun listReceived(callerId: UUID): List<ReliefInviteView> = ReliefInviteRepository.findReceivedByInvitee(callerId)

    /**
     * Accepts a PENDING invite: writes the day-scoped grant immediately
     * (grantedBy = inviter, grantedAt = accept time — both live on the invite row:
     * invitedBy + respondedAt; the capability row's sourceId ties it to the invite).
     *
     * @throws ValidationException when the day is past/REMITTED — the invite expired
     *   (day-state is the expiry, #159 Q6; no cron).
     * @throws ConflictException when the invite was already responded to.
     */
    @Suppress("ThrowsCount")
    fun acceptInvite(
        callerId: UUID,
        inviteId: UUID,
    ): ReliefInvite {
        val invite = requireOwnInvite(callerId, inviteId)
        val branchDay = BranchDayService.requireBranchDayExists(invite.branchDayId)
        val effectiveStatus = BranchDayService.getEffectiveStatus(invite.branchDayId)
        if (effectiveStatus != DayStatus.OPEN) {
            throw ValidationException("This relief invite has expired — the day is no longer open")
        }

        val validTo = BranchDayService.expirationUtc(branchDay.date)
        val result =
            ReliefInviteRepository.accept(
                id = inviteId,
                invitee = callerId,
                validTo = validTo,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = ReliefInviteTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        auditFields = ReliefInviteTable::auditFields,
                    )
                },
            ) ?: throw ConflictException("This invite was already responded to")

        logger.info { "[RELIEF-INVITE-ACCEPT] Invite $inviteId accepted by $callerId (day grant written)" }
        return result
    }

    /** Declines a PENDING invite — bearer, caller must be the invitee. */
    fun declineInvite(
        callerId: UUID,
        inviteId: UUID,
    ): ReliefInvite {
        val invite = requireOwnInvite(callerId, inviteId)
        val branchDay = BranchDayService.requireBranchDayExists(invite.branchDayId)
        val result =
            ReliefInviteRepository.respond(
                id = inviteId,
                status = ReliefInviteStatus.DECLINED,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = ReliefInviteTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        auditFields = ReliefInviteTable::auditFields,
                    )
                },
            ) ?: throw ConflictException("This invite was already responded to")
        return result
    }

    /** Retracts a PENDING invite — same gate as create (active assignment at the branch). */
    @Suppress("ThrowsCount")
    fun retractInvite(
        callerId: UUID,
        inviteId: UUID,
    ): ReliefInvite {
        val invite =
            ReliefInviteRepository.findById(inviteId)
                ?: throw NotFoundException("Relief invite not found")
        if (invite.invitedBy != callerId) {
            throw ForbiddenException("Only the inviter can retract this invite")
        }
        val branchId = BranchDayService.requireBranchDayExists(invite.branchDayId).branchId
        requireActiveAssignment(callerId, branchId)

        val result =
            ReliefInviteRepository.respond(
                id = inviteId,
                status = ReliefInviteStatus.RETRACTED,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = ReliefInviteTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchId,
                        auditFields = ReliefInviteTable::auditFields,
                    )
                },
            ) ?: throw ConflictException("This invite was already responded to")
        return result
    }

    /**
     * Candidate search for the inviter side: ACTIVE users whose username/displayName
     * starts with [query] (blank → all), minus the caller and anyone already
     * PENDING/ACCEPTED/GRANTED for the day. The day resolves find-only: no day row means
     * no invites/grants can exist for it, so no exclusions apply (the #158 gate shape —
     * a search must never create rows).
     */
    fun searchCandidates(
        callerId: UUID,
        branchId: UUID,
        query: String,
        date: LocalDate,
    ): List<ReliefCandidate> {
        requireActiveAssignment(callerId, branchId)

        val branchDayId = BranchDayService.findByBranchAndDate(branchId, date)?.id
        val excluded = mutableSetOf(callerId)
        if (branchDayId != null) {
            excluded += ReliefInviteRepository.findLiveInviteeIds(branchDayId)
            excluded += ReliefInviteRepository.findActiveGrantUserIds(branchDayId)
        }
        return ReliefInviteRepository.findActiveUsersWithPrefix(query.trim()).filterNot { it.id in excluded }
    }

    /** The received/sent list shape for a single invite (post-mutation response mapping). */
    fun viewFor(invite: ReliefInvite): ReliefInviteView? = ReliefInviteRepository.findViewByInviteId(invite.id)

    private fun requireOwnInvite(
        callerId: UUID,
        inviteId: UUID,
    ): ReliefInvite {
        val invite =
            ReliefInviteRepository.findById(inviteId)
                ?: throw NotFoundException("Relief invite not found")
        if (invite.invitee != callerId) {
            throw ForbiddenException("Only the invitee can respond to this invite")
        }
        return invite
    }

    private fun requireActiveAssignment(
        callerId: UUID,
        branchId: UUID,
    ) {
        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        if (assignment == null) {
            throw ForbiddenException("An active assignment at this branch is required")
        }
    }
}
