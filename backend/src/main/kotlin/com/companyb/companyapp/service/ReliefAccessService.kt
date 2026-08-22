package com.companyb.companyapp.service

import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.GrantWithCapabilityParams
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.attendance.BranchDayUser
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Relief-access feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: the Branch Day gate runs inside it, persistence runs via
 * `ReliefAccessRepository.*InTransaction` store operations, and the audit row is inserted
 * into the same transaction — so mutation + audit commit atomically or not at all.
 */
object ReliefAccessService {
    private val logger = KotlinLogging.logger {}

    /**
     * Caller-relative discovery (#351): pending requests targeting the caller (the grant
     * surface) plus the caller's own outgoing requests (the outcome view) for one branch
     * day. Authorization is row ownership — the query only returns rows involving the
     * caller, so bearer auth suffices (the #141 notification-as-authorization precedent);
     * no capability gate exists on this read by design.
     */
    fun listForCaller(
        callerId: UUID,
        branchDayId: UUID,
    ): List<ReliefAccess> = ReliefAccessRepository.findInvolving(callerId, branchDayId)

    /**
     * Checked-in users a relief user may target for an access request (#351), caller
     * excluded. Gated on the caller's own active clock-in at the branch day — the
     * dashboard universal-post-clock-in precedent: pre-grant relief users hold no
     * capabilities, so a capability gate would 403 the primary flow.
     */
    fun listCandidates(
        callerId: UUID,
        branchDayId: UUID,
    ): List<BranchDayUser> {
        if (!AttendanceService.hasActiveClockIn(callerId, branchDayId)) {
            throw ForbiddenException("Only users with an active clock-in can list relief candidates")
        }
        return AttendanceService.findUsersByBranchDayId(branchDayId).filterNot { it.userId == callerId }
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun grantAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefGrantOutcome {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can grant this request")
        }

        if (request.requestStatus == ReliefAccessStatus.GRANTED) {
            return ReliefGrantOutcome.Granted(request)
        }

        val result =
            transaction {
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

                // #354 edge 2: presence was checked when the request was created; recheck it
                // atomically here so a target who clocked out cannot grant afterwards.
                if (!ReliefAccessRepository.hasActiveClockInInTransaction(request.targetUser, request.branchDayId)) {
                    throw ValidationException("Target user does not have an active clock-in on this branch day")
                }

                val mutation =
                    ReliefAccessRepository.grantInTransaction(
                        GrantWithCapabilityParams(
                            requestId = requestId,
                            grantedBy = callerId,
                            userId = request.requestedBy,
                            branchDayId = request.branchDayId,
                            sourceId = requestId,
                            validTo = BranchDayService.expirationUtc(branchDay.date),
                            requestedBy = request.requestedBy,
                        ),
                    ) ?: error("Grant failed: relief access request not found in transaction")

                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
                    )
                }
                mutation.after
            }

        return when {
            result.id == requestId && result.requestStatus == ReliefAccessStatus.GRANTED -> {
                logger.info { "[RELIEF-ACCESS-GRANT] Request $requestId granted by $callerId" }
                ReliefGrantOutcome.Granted(result)
            }

            else -> {
                // #354 edge 3: another decision won the day-row lock (a different request of
                // the same requester got granted, or this one was denied concurrently).
                logger.info {
                    "[RELIEF-ACCESS-GRANT] Request $requestId superseded — returning winner ${result.id} " +
                        "(${result.requestStatus}) to caller $callerId"
                }
                ReliefGrantOutcome.Superseded(result)
            }
        }
    }

    @Suppress("ThrowsCount")
    fun denyAccess(
        requestId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        val request =
            ReliefAccessRepository.findById(requestId)
                ?: throw NotFoundException("Relief access request not found")

        if (callerId != request.targetUser) {
            throw ForbiddenException("Only the target user can deny this request")
        }

        if (request.requestStatus == ReliefAccessStatus.DENIED) {
            return request
        }

        if (request.requestStatus == ReliefAccessStatus.GRANTED) {
            throw ValidationException("Cannot deny a request that has already been granted")
        }

        val result =
            transaction {
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditable(callerId, request.branchDayId, reason)

                val mutation = ReliefAccessRepository.denyInTransaction(requestId)
                if (mutation.updated) {
                    ReliefAccessAudit.updated(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        mutation.before,
                        mutation.after,
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

    @Suppress("ThrowsCount")
    fun requestReliefAccess(
        requestId: UUID,
        branchDayId: UUID,
        targetUserId: UUID,
        callerId: UUID,
        reason: String? = null,
    ): ReliefAccess {
        if (targetUserId == callerId) {
            // #354 edge 1: the candidate listing excludes the caller; this guard closes the
            // direct-API hole so a relief user cannot request access through themselves.
            throw ValidationException("Cannot request relief access from yourself")
        }

        val (reliefAccess, wasCreated) =
            transaction {
                val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

                // #354 edges 2+4: eligibility rechecked atomically at write time — presence
                // and account status can change between listing and request.
                if (!ReliefAccessRepository.hasActiveClockInInTransaction(targetUserId, branchDayId)) {
                    throw ValidationException("Target user does not have an active clock-in on this branch day")
                }
                if (!ReliefAccessRepository.isActiveUserInTransaction(targetUserId)) {
                    throw ValidationException("Target user is not active")
                }
                if (!ReliefAccessRepository.isReliefUserInTransaction(callerId, branchDayId)) {
                    throw ForbiddenException("Only relief users can request relief access")
                }

                val pair =
                    ReliefAccessRepository.insertRequestInTransaction(
                        id = requestId,
                        branchDayId = branchDayId,
                        requestedBy = callerId,
                        targetUser = targetUserId,
                    )
                if (pair.second) {
                    ReliefAccessAudit.inserted(
                        AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                        pair.first,
                    )
                }
                pair
            }

        logger.info {
            "[RELIEF-ACCESS-REQUEST] Request $requestId created " +
                "(relief=$callerId, target=$targetUserId, new=$wasCreated)"
        }

        return reliefAccess
    }
}

/**
 * Grant outcome (#354 edge 3): a caller whose grant lost a race must be able to tell that
 * apart from success — [Superseded] carries the row that actually won the day (another
 * granted request, or this request denied concurrently). Top-level so routes and tests can
 * pattern-match without nesting.
 */
sealed interface ReliefGrantOutcome {
    /** The caller's grant won; includes idempotent replays of an already-granted request. */
    data class Granted(
        val reliefAccess: ReliefAccess,
    ) : ReliefGrantOutcome

    /** This decision did not win — [reliefAccess] is the surviving row, not the caller's request. */
    data class Superseded(
        val reliefAccess: ReliefAccess,
    ) : ReliefGrantOutcome
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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
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
