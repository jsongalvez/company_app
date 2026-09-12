package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.authorization.AuthorizationGrants
import com.companyb.companyapp.authorization.GrantReliefCapabilityParams
import com.companyb.companyapp.branch.Branch
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.workforce.ShiftGuard
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * Relief-access feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: the Branch Day gate runs inside it, persistence runs via
 * `ReliefAccessRepository.*InTransaction` store operations, and the audit row is inserted
 * into the same transaction — so mutation + audit commit atomically or not at all.
 *
 * #357 broadcast model: a request names no target user (the #352 rules change) and is
 * branch-day-scoped; any ACTIVE user without a home assignment at the branch may raise
 * one for today or a future date, every branch member can grant/deny/cancel it, and all
 * retraction locks once the requester clocks in. The #354 supersede machinery is retired:
 * multiple relief workers per branch-day are allowed, so there is no cross-request race
 * to arbitrate.
 */
object ReliefAccessService {
    private val logger = KotlinLogging.logger {}

    /**
     * Per-day request surface (#357): branch members see every request on the day (the
     * broadcast audience); non-members see only their own rows. Authorization is bearer +
     * row scoping (the #141 notification-as-authorization precedent); no capability gate.
     */
    fun listForCaller(
        callerId: UUID,
        branchDayId: UUID,
    ): List<ReliefAccess> {
        val branchId = BranchDayService.requireBranchDayExists(branchDayId).branchId
        val requests = ReliefAccessRepository.findByBranchDayId(branchDayId)
        if (requests.isEmpty()) return requests
        val isMember = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId) != null
        return if (isMember) requests else requests.filter { it.requestedBy == callerId }
    }

    /** The caller's own requests across branches/days with branch context (#357). */
    fun listMine(callerId: UUID): List<ReliefRequestWithBranch> = ReliefAccessRepository.findMine(callerId)

    /**
     * #358 deep-link read: a notification tap carries (branchId, date), not a branch-day id.
     * Find-only day resolution — a stale link never materializes a missing day; empty list
     * when no such day exists. Same audience rules as [listForCaller].
     */
    fun listForCallerByDay(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate,
    ): List<ReliefAccess> {
        // #725 — 404 precedence for an unknown branch before the day read (#721
        // precedent): findByBranchAndDate alone conflates "unknown branch" with
        // "known branch with no day yet".
        BranchService.findById(branchId)
        val branchDay = BranchDayService.findByBranchAndDate(branchId, date) ?: return emptyList()
        return listForCaller(callerId, branchDay.id)
    }

    /**
     * Active branches for the pre-clock-in request picker (#357). Bearer-only: the
     * requester holds no capabilities by definition (the dashboard universal-read
     * precedent — a capability gate would 403 the primary flow).
     */
    fun listBranchOptions(): List<Branch> = BranchService.findAll()

    fun grantAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        // Non-locking entry read (the #513 discipline — no lock widening): the day lock
        // inside the command transaction is the serializer, not the relief row.
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        val result =
            transaction {
                // #515 — membership at commit + locked day gate inside the command
                // transaction: a concurrent remittance submit flipping the day to REMITTED
                // can no longer slip between the gate and the grant.
                requireActiveMemberInTransaction(callerId, request.branchDayId)
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditableInTransaction(callerId, request.branchDayId, reason)

                // #913 — one duty per person per day (idx_one_grant_per_day backstop):
                // a sibling GRANTED for the same requester/day turns this grant into
                // a 409 instead of a unique-violation 500. Day-lock serialized with
                // the sibling's grant, so the check+flip are atomic.
                requireNoSiblingGrantedInTransaction(request.requestedBy, request.branchDayId, requestId)

                val mutation =
                    ReliefAccessRepository.grantInTransaction(
                        GrantWithCapabilityParams(
                            requestId = requestId,
                            grantedBy = callerId,
                        ),
                    ) ?: error("Grant failed: relief access request not found in transaction")

                // #913 — granting an already-decided ask is a 409, not a silent 200
                // carrying the wrong status (the deny/cancel decided-state discipline).
                // The GRANTED replay leg stays idempotent (updated=false, after=GRANTED).
                if (!mutation.updated && mutation.after.requestStatus != ReliefAccessStatus.GRANTED) {
                    throw ConflictException(
                        "This relief request was already decided (${mutation.after.requestStatus})",
                    )
                }
                // #918 — cross-path twin of the sibling guard (fresh flips only; the
                // GRANTED replay leg above returns its own grant, which the view
                // check would otherwise mistake for another path's duty):
                // an ACCEPTED invite for the same user/day already wrote the
                // day-scoped grant (same capability, other table), so a fresh
                // grant here would stack a second active row. Post-mutation throw
                // rolls the flip back — the request stays PENDING.
                if (mutation.updated) {
                    requireNoInviteDutyInTransaction(request.requestedBy, request.branchDayId)
                }
                if (mutation.updated) {
                    AuthorizationGrants.grantReliefCapabilityInTransaction(
                        GrantReliefCapabilityParams(
                            userId = request.requestedBy,
                            branchDayId = request.branchDayId,
                            sourceId = requestId,
                            validTo = BranchDayService.expirationUtc(branchDay.date),
                        ),
                    )
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                    // #358 — outcome broadcast: everyone incl. who granted + the requester.
                    ReliefNotifications.requestOutcome(
                        eventType = ReliefNotifications.GRANTED,
                        requestId = requestId,
                        actorId = callerId,
                        requesterId = request.requestedBy,
                        context = ReliefEventContext.of(branchDay),
                    )
                }
                mutation.after
            }

        logger.info {
            "[RELIEF-ACCESS-GRANT] Request $requestId handled by $callerId " +
                "(status=${result.requestStatus}, updated=${result.requestStatus == ReliefAccessStatus.GRANTED})"
        }
        return result
    }

    fun denyAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        // Non-locking entry read (the #513 discipline — no lock widening): the store's
        // conditional update arbitrates races. No pre-transaction decided-state
        // fast-path (#928): the membership + day gates below must run on every leg
        // so non-members get 403 on DENIED/GRANTED rows instead of a 200/400 oracle.
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        val result =
            transaction {
                // #515 — membership at commit + locked day gate in the same tx
                // (same TOCTOU shape as grant).
                requireActiveMemberInTransaction(callerId, request.branchDayId)
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditableInTransaction(callerId, request.branchDayId, reason)

                val mutation = ReliefAccessRepository.denyInTransaction(requestId)
                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                    // #358 — outcome broadcast: everyone incl. who denied + the requester.
                    ReliefNotifications.requestOutcome(
                        eventType = ReliefNotifications.DENIED,
                        requestId = requestId,
                        actorId = callerId,
                        requesterId = request.requestedBy,
                        context = ReliefEventContext.of(branchDay),
                    )
                }
                mutation.after
            }

        if (result.requestStatus == ReliefAccessStatus.GRANTED) {
            throw ValidationException("Cannot deny a request that has already been granted")
        }

        logger.info { "[RELIEF-ACCESS-DENY] Request $requestId denied by $callerId" }

        return result
    }

    /**
     * Withdraw/cancel (#357, owner rule "one rule for both"): the requester withdraws
     * theirs, any active branch member cancels someone else's pending ask; both die once
     * the requester has clocked in at the branch day.
     */
    fun cancelRequest(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        val isRequester = request.requestedBy == callerId

        val result =
            transaction {
                // #376 — membership at commit for the member-cancel leg: the FOR UPDATE
                // re-read orders against a concurrently committing assignment removal.
                if (!isRequester) {
                    requireActiveMemberInTransaction(callerId, request.branchDayId)
                }
                // Owner rule: ALL retraction stops once the requester clocks in as relief
                // (#376 — the check serializes on the branch_day row, so a concurrent
                // clock-in cannot slip between the read and this command's commit).
                ShiftGuard.ensureRetractionAllowed(
                    request.requestedBy,
                    request.branchDayId,
                    "Relief duty has already started — this request can no longer be cancelled",
                )

                val (branchDay, isRemitted) =
                    // The day gate only arbitrates edit authority for members; a withdrawing
                    // requester acts on their own row regardless of day state (a scheduled
                    // ask can be withdrawn before its day even opens).
                    if (isRequester) {
                        BranchDayService.requireBranchDayExists(request.branchDayId).let {
                            it to false
                        }
                    } else {
                        BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)
                    }

                val mutation = ReliefAccessRepository.cancelInTransaction(requestId)
                if (!mutation.updated && mutation.after.requestStatus != ReliefAccessStatus.CANCELLED) {
                    throw ConflictException(
                        "This relief request was already decided (${mutation.after.requestStatus})",
                    )
                }
                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                }
                mutation.after
            }

        logger.info {
            "[RELIEF-ACCESS-CANCEL] Request $requestId cancelled by $callerId (requester=$isRequester)"
        }
        return result
    }

    fun requestReliefAccess(
        requestId: UUID,
        branchId: UUID,
        date: LocalDate?,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val branch = BranchService.findById(branchId)
        requireRequesterEligible(callerId, branchId)

        val operationalDate = date ?: BranchDayService.currentOperationalDate()
        requireOperationalDate(operationalDate)

        val (reliefAccess, wasCreated) =
            transaction {
                // Resolve-or-create stays nested (existing precedent); the locked gate
                // below serializes against the remittance transition.
                val branchDay = BranchDayService.resolveOrCreate(branchId, operationalDate)

                // #515 — in-tx replay classification before the day gate (the #510
                // discipline): a same-id row already committed acks without re-gating so
                // retries landing after a day transition still ack; ownership matches the
                // insert below (the #510/#514 class).
                ReliefAccessRepository.findByIdInTransaction(requestId)?.let { existing ->
                    requireReplayOwnership(existing, branchDay.id, callerId)
                    return@transaction existing to false
                }

                // Locked day read: serializes this request with remittance's REMITTED transition.
                val (lockedDay, isRemitted) =
                    BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDay.id, reason)

                // #913 — per-person duty guard (mirrors the invite create guard): the
                // requester must not already hold the day (a GRANTED request or an
                // active day grant incl. the invite path). Without it a second grant
                // would hit idx_one_grant_per_day as a 500.
                requireNoExistingDutyInTransaction(callerId, lockedDay.id)

                val pair =
                    ReliefAccessRepository.insertRequestInTransaction(
                        id = requestId,
                        branchDayId = lockedDay.id,
                        requestedBy = callerId,
                    )
                if (pair.first == null) {
                    // A different live request stands for this branch day — the #352 Q4
                    // flood rule. Throw inside the transaction so the swallowed insert
                    // and the conflict commit atomically as nothing.
                    throw ConflictException("You already have a live relief request for this branch day")
                }
                if (pair.second) {
                    ReliefAccessAudit.inserted(
                        AuditContext(callerId, lockedDay.branchId, isRemitted, reason),
                        checkNotNull(pair.first),
                    )
                    // #358 — the branch-wide ping commits with the request it announces.
                    ReliefNotifications.requestCreated(
                        requestId = requestId,
                        requesterId = callerId,
                        context =
                            ReliefEventContext(
                                branchId = branchId,
                                branchName = branch.name,
                                date = operationalDate,
                            ),
                    )
                }
                pair
            }

        logger.info {
            "[RELIEF-ACCESS-REQUEST] Request $requestId created " +
                "(relief=$callerId, branch=$branchId, date=$operationalDate, replay=${!wasCreated})"
        }

        return checkNotNull(reliefAccess) { "conflict path already threw" }
    }

    /**
     * #376 — in-transaction membership gate with a FOR UPDATE read of the assignment row:
     * an assignment ending concurrently blocks here or is already visible, so membership
     * holds at command commit, not just at entry. Resolves the day's branch inside the
     * caller's transaction.
     */
    private fun requireActiveMemberInTransaction(
        callerId: UUID,
        branchDayId: UUID,
    ) {
        val branchId = BranchDayService.requireBranchDayExists(branchDayId).branchId
        val assignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUserInTransaction(branchId, callerId, forUpdate = true)
        if (assignment == null) {
            throw ForbiddenException("An active assignment at this branch is required")
        }
    }

    /**
     * #598: pre-transaction requester eligibility split from the command body
     * (ThrowsCount budget is 2 per function).
     */
    private fun requireRequesterEligible(
        callerId: UUID,
        branchId: UUID,
    ) {
        if (!ReliefAccessRepository.isActiveUser(callerId)) {
            throw ForbiddenException("Inactive users cannot request relief duty")
        }
        if (UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId) != null) {
            throw ValidationException("You are already assigned to this branch — relief duty does not apply")
        }
    }

    private fun requireOperationalDate(operationalDate: LocalDate) {
        if (operationalDate < BranchDayService.currentOperationalDate()) {
            throw ValidationException("Relief duty cannot be requested for a past date")
        }
    }

    private fun requireReplayOwnership(
        existing: ReliefAccess,
        branchDayId: UUID,
        callerId: UUID,
    ) {
        if (existing.branchDayId != branchDayId) {
            throw NotFoundException("Relief request not found for this branch day")
        }
        if (existing.requestedBy != callerId) {
            throw ConflictException("Relief request id already belongs to another request")
        }
    }

    /**
     * #913 — grant-path sibling guard (ThrowsCount split): the requester must not
     * already hold a GRANTED row for the day under a different request id.
     */
    private fun requireNoSiblingGrantedInTransaction(
        requestedBy: UUID,
        branchDayId: UUID,
        excludeRequestId: UUID,
    ) {
        if (
            ReliefAccessRepository.findSiblingGrantedInTransaction(requestedBy, branchDayId, excludeRequestId) != null
        ) {
            throw ConflictException("This user already holds relief duty for the day")
        }
    }

    /**
     * #918 — grant-path invite guard (ThrowsCount split): the requester must not
     * already hold the day through the invite path (an ACCEPTED invite writes the
     * same day-scoped capability under another table, invisible to the sibling
     * guard above).
     */
    private fun requireNoInviteDutyInTransaction(
        requestedBy: UUID,
        branchDayId: UUID,
    ) {
        if (ReliefInviteRepository.hasActiveGrantInTransaction(requestedBy, branchDayId)) {
            throw ConflictException("This user already holds relief duty for the day")
        }
    }

    /**
     * #913 — creation-path duty guard (ThrowsCount split): the requester must hold
     * neither a GRANTED request nor an active day grant (either path's grant writes
     * the same day-scoped capability) for the day.
     */
    private fun requireNoExistingDutyInTransaction(
        requesterId: UUID,
        branchDayId: UUID,
    ) {
        if (
            ReliefAccessRepository.findSiblingGrantedInTransaction(requesterId, branchDayId, null) != null ||
            ReliefInviteRepository.hasActiveGrantInTransaction(requesterId, branchDayId)
        ) {
            throw ConflictException("This user already holds relief duty for the day")
        }
    }
}

/**
 * Relief-access audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ReliefAccessAudit {
    fun inserted(
        context: AuditContext,
        reliefAccess: ReliefAccess,
    ) = AuditLog.recordInsert(
        tableName = GrantReliefAccessTable.tableName,
        recordId = reliefAccess.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = GrantReliefAccessTable.auditFields(reliefAccess),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: ReliefAccess,
        after: ReliefAccess,
    ) = AuditLog.recordUpdate(
        tableName = GrantReliefAccessTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = GrantReliefAccessTable::auditFields,
    )
}
