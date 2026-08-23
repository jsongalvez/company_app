package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.ReliefCandidate
import com.companyb.companyapp.repository.ReliefInviteRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.ReliefInvite
import com.companyb.companyapp.repository.model.ReliefInviteTable
import com.companyb.companyapp.repository.model.ReliefInviteView
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * Branch-initiated relief invites (#159, the #160 build). The inviter is anyone with an
 * ACTIVE `user_branch_assignment` at the branch — the app's only membership record — and
 * the invitee is any ACTIVE user (capabilities ≠ assignments; a redundant grant is
 * harmless). One day per invite; accept writes the day-scoped grant immediately via the
 * shared relief-grant writer ([ReliefAccessRepository.grantReliefCapability]); day-state
 * is the expiry (a past/REMITTED day accept 400s — no cron).
 *
 * Mutating commands own exactly one transaction (#323, ADR-0024): persistence runs via
 * `ReliefInviteRepository.*InTransaction` store operations and the audit row is inserted
 * into the same transaction. Accept's day-open gate and expiration live inside its command
 * transaction so the expiry decision commits atomically with the grant it authorizes.
 */
@Suppress("TooManyFunctions")
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
            transaction {
                val pair =
                    ReliefInviteRepository.insertInTransaction(
                        id = UUID.randomUUID(),
                        branchDayId = branchDay.id,
                        invitedBy = callerId,
                        invitee = inviteeUserId,
                    )
                if (pair.second && pair.first != null) {
                    ReliefInviteAudit.inserted(AuditContext(callerId, branchId, isRemitted), checkNotNull(pair.first))
                }
                pair
            }

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

        val result =
            transaction {
                val branchDay = BranchDayService.requireBranchDayExists(invite.branchDayId)
                val effectiveStatus = BranchDayService.getEffectiveStatus(invite.branchDayId)
                if (effectiveStatus != DayStatus.OPEN) {
                    throw ValidationException("This relief invite has expired — the day is no longer open")
                }
                val validTo = BranchDayService.expirationUtc(branchDay.date)

                val mutation =
                    ReliefInviteRepository.acceptInTransaction(id = inviteId, invitee = callerId, validTo = validTo)
                        ?: throw ConflictException("This invite was already responded to")
                ReliefInviteAudit.updated(AuditContext(callerId, branchDay.branchId), mutation.before, mutation.after)
                // #358 — "everyone is notified when the person accepts or not".
                ReliefNotifications.inviteResponded(
                    eventType = ReliefNotifications.INVITE_ACCEPTED,
                    inviteId = inviteId,
                    inviteeId = callerId,
                    context = ReliefEventContext.of(branchDay),
                )
                mutation.after
            }

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
            transaction {
                val mutation =
                    ReliefInviteRepository.respondInTransaction(inviteId, ReliefInviteStatus.DECLINED)
                        ?: throw ConflictException("This invite was already responded to")
                ReliefInviteAudit.updated(AuditContext(callerId, branchDay.branchId), mutation.before, mutation.after)
                // #358 — decline broadcast, same audience as accept.
                ReliefNotifications.inviteResponded(
                    eventType = ReliefNotifications.INVITE_DECLINED,
                    inviteId = inviteId,
                    inviteeId = callerId,
                    context = ReliefEventContext.of(branchDay),
                )
                mutation.after
            }
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
            transaction {
                val mutation =
                    ReliefInviteRepository.respondInTransaction(inviteId, ReliefInviteStatus.RETRACTED)
                        ?: throw ConflictException("This invite was already responded to")
                ReliefInviteAudit.updated(AuditContext(callerId, branchId), mutation.before, mutation.after)
                mutation.after
            }
        return result
    }

    /**
     * Revokes an ACCEPTED invite (#374, the #363 owner rulings): any active branch member
     * — clocked in or not — may undo a wrong/stale acceptance. The command owns exactly
     * one transaction per ADR-0024: the clock-in lock is checked inside it before any
     * mutation (ruling 3: ALL revocation stops once the invitee clocks in at that branch
     * day — any open clock-in counts, relief or home), the status flips ACCEPTED→REVOKED
     * conditionally (0 rows ⇒ already decided → 409), and the day grant is removed by its
     * capability sourceId in the same transaction.
     *
     * Day-state edge (agent-owned per #363): a past/REMITTED duty cannot be revoked — same
     * effective-status discipline as accept; the grant window has lapsed either way.
     *
     * @throws ForbiddenException when the caller lacks an active assignment at the branch.
     * @throws ValidationException when the invitee has clocked in ("duty already started")
     *   or the day is no longer OPEN.
     * @throws ConflictException when the invite was already responded to.
     */
    @Suppress("ThrowsCount")
    fun revokeInvite(
        callerId: UUID,
        inviteId: UUID,
    ): ReliefInvite {
        val invite =
            ReliefInviteRepository.findById(inviteId)
                ?: throw NotFoundException("Relief invite not found")
        val branchDay = BranchDayService.requireBranchDayExists(invite.branchDayId)
        requireActiveAssignment(callerId, branchDay.branchId)

        val result =
            transaction {
                if (ReliefAccessRepository.hasActiveClockInInTransaction(invite.invitee, invite.branchDayId)) {
                    throw ValidationException("This relief duty has already started")
                }
                if (BranchDayService.getEffectiveStatus(invite.branchDayId) != DayStatus.OPEN) {
                    throw ValidationException("This relief duty has ended — the day is no longer open")
                }
                val mutation =
                    ReliefInviteRepository.revokeInTransaction(inviteId)
                        ?: throw ConflictException("This invite was already responded to")
                ReliefAccessRepository.deleteGrantBySourceIdInTransaction(
                    userId = mutation.after.invitee,
                    sourceId = inviteId,
                )
                ReliefInviteAudit.updated(AuditContext(callerId, branchDay.branchId), mutation.before, mutation.after)
                // #363 ruling 4 — the branch hears it; the invitee gets an explicit notice.
                ReliefNotifications.inviteRevoked(
                    actorId = callerId,
                    inviteeId = mutation.after.invitee,
                    inviteId = inviteId,
                    context = ReliefEventContext.of(branchDay),
                )
                mutation.after
            }

        logger.info { "[RELIEF-INVITE-REVOKE] Invite $inviteId revoked by $callerId (day grant removed)" }
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

/**
 * Relief-invite audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ReliefInviteAudit {
    fun inserted(
        context: AuditContext,
        invite: ReliefInvite,
    ) = AuditLogRepository.recordInsert(
        tableName = ReliefInviteTable.tableName,
        recordId = invite.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = ReliefInviteTable.auditFields(invite),
        isFlagged = context.isFlagged,
    )

    fun updated(
        context: AuditContext,
        before: ReliefInvite,
        after: ReliefInvite,
    ) = AuditLogRepository.recordUpdate(
        tableName = ReliefInviteTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = ReliefInviteTable::auditFields,
    )
}
