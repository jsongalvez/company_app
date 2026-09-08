package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientReads
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.session.isStatusCorrection
import com.companyb.companyapp.contracts.session.isStatusTransitionAllowed
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.workforce.WorkforceReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object SessionService {
    private val logger = KotlinLogging.logger {}

    fun computeSessionType(
        branchType: BranchType,
        priorNonMedicalMissionCount: Long,
    ): SessionType {
        if (branchType == BranchType.MEDICAL_MISSION) {
            return SessionType.MEDICAL_MISSION
        }
        if (branchType == BranchType.PROVINCIAL_TOUR && priorNonMedicalMissionCount == 0L) {
            return SessionType.PROVINCIAL_FIRST
        }
        if (priorNonMedicalMissionCount == 0L) return SessionType.REGULAR
        if (priorNonMedicalMissionCount == 1L) return SessionType.SECOND_SESSION
        return SessionType.SUBSEQUENT
    }

    // #593 twelve-param creation command stays whole (coherent session inputs; #535 no arbitrary DTO).
    @Suppress("LongParameterList") // #593
    fun create(
        callerId: UUID,
        id: UUID,
        clientId: UUID,
        branchId: UUID,
        isWalkIn: Boolean,
        requestedPractitionerId: UUID?,
        finalPrice: BigDecimal,
        remarks: String?,
        otherConcerns: String?,
        nextAppointmentDate: LocalDate?,
        // #157: the branch day the route gate resolved (find-only). When provided, the create
        // writes to THIS day instead of re-resolving today — the gate and the write share one
        // resolution so a request straddling the Manila midnight boundary can't be gated
        // against the granted day while landing on the next (the mixed-resolution-base class).
        // Must belong to [branchId] — a foreign or missing day fails closed with 404
        // (parent-child convention).
        gatedBranchDayId: UUID? = null,
        // #509 — REMITTED-day reason (BR §Day State Machine): PAST needs coordinator-only,
        // REMITTED needs coordinator + reason. Plumbed route→service like every sibling
        // session mutation; null on OPEN days.
        reason: String? = null,
    ): SessionCreateResult {
        val branchType =
            SessionRepository.getBranchType(branchId)
                ?: throw NotFoundException("Branch not found")

        // #366 — the requested practitioner must be an ACTIVE member of the session's branch
        // (today any existing UUID is accepted). Optional field: null passes untouched.
        if (requestedPractitionerId != null &&
            !WorkforceReads.hasActiveMember(branchId, requestedPractitionerId)
        ) {
            throw ValidationException("Requested practitioner is not an active member of this branch")
        }

        val existing = SessionRepository.findById(id)
        if (existing != null) {
            return fastPathReplay(existing, clientId, branchId, callerId, gatedBranchDayId)
        }

        val today = BranchDayService.currentOperationalDate()
        val branchDay =
            gatedBranchDayId?.let { BranchDayService.requireBranchDayForBranch(it, branchId) }
                ?: BranchDayService.resolveOrCreate(branchId, today)

        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)

        // #405 — BR §Session types: a medical-mission visit is always free (₱0). The rule is
        // an invariant of the domain, not an input constraint, so a non-zero caller-supplied
        // price is normalized to zero rather than rejected — no client version or direct API
        // caller can persist one.
        val effectiveFinalPrice =
            if (sessionType == SessionType.MEDICAL_MISSION) BigDecimal.ZERO else finalPrice

        val basePrice = resolveDefaultBasePrice(branchId, clientId, sessionType)

        return transaction {
            // #509 — client-first lock order (matches status/price/void/unvoid): the client
            // row is locked before the day row so concurrent session mutations serialize
            // instead of deadlocking. The day gate below locks the day row, serializing
            // this create with remittance submit/undo's REMITTED transition.
            val lockedClient = ClientReads.acquireLockInTransaction(clientId)
            if (lockedClient?.deletedAt != null) {
                throw ConflictException("Cannot create a session for an anonymized client")
            }
            idempotentReplayOrNull(SessionRepository.findByIdInTransaction(id), clientId, branchDay.id, callerId)?.let {
                return@transaction it
            }
            val (lockedDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDay.id, reason)
            val result =
                SessionRepository.createInTransaction(
                    SessionCreateParams(
                        id = id,
                        clientId = clientId,
                        branchDayId = branchDay.id,
                        requestedPractitionerId = requestedPractitionerId,
                        sessionType = sessionType,
                        isWalkIn = isWalkIn,
                        basePrice = basePrice,
                        finalPrice = effectiveFinalPrice,
                        remarks = remarks,
                        otherConcerns = otherConcerns,
                        nextAppointmentDate = nextAppointmentDate,
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                SessionAudit.inserted(AuditContext(callerId, lockedDay.branchId, isRemitted, reason), result.session)
            }

            logger.info {
                "[CREATE-SESSION] Session ${result.session.id} created (type=$sessionType, branchType=$branchType)"
            }

            result
        }
    }

    /**
     * Fast-path replay check (authoritative classification lives in the command
     * transaction's [idempotentReplayOrNull] — this only preserves 409 precedence before
     * the day resolution below). Ownership is the stored created_by (#453). Extracted so
     * [create] stays under the complexity gate.
     */
    private fun fastPathReplay(
        existing: Session,
        clientId: UUID,
        branchId: UUID,
        callerId: UUID,
        gatedBranchDayId: UUID?,
    ): SessionCreateResult {
        val expectedBranchDayId =
            gatedBranchDayId ?: BranchDayService.findToday(branchId)?.id
        val sameClient = existing.clientId == clientId
        val sameBranch = SessionRepository.branchDayBelongsToBranch(existing.branchDayId, branchId)
        val sameDay = existing.branchDayId == expectedBranchDayId
        val sameCaller = existing.createdBy == callerId
        if (listOf(sameClient, sameBranch, sameDay, sameCaller).any { !it }) {
            throw ConflictException("Session id already belongs to another create request")
        }
        logger.info { "[CREATE-SESSION] Session ${existing.id} already exists, returning existing (idempotent)" }
        return SessionCreateResult(existing, false)
    }

    /**
     * #509 — in-transaction idempotent-replay classification (runs on the caller's command
     * transaction, after the client lock, before the day gate): a same-id row already
     * committed returns without gating so retries that land after a day transition still
     * acknowledge; a foreign row fails closed with 409. Ownership is the stored created_by
     * (#453). Extracted so [create] stays under the complexity gate.
     */
    private fun idempotentReplayOrNull(
        existing: Session?,
        clientId: UUID,
        branchDayId: UUID,
        callerId: UUID,
    ): SessionCreateResult? {
        if (existing == null) return null
        val sameClient = existing.clientId == clientId
        val sameBranchDay = existing.branchDayId == branchDayId
        val sameCaller = existing.createdBy == callerId
        if (!sameClient || !sameBranchDay || !sameCaller) {
            throw ConflictException("Session id already belongs to another create request")
        }
        return SessionCreateResult(existing, false)
    }

    private fun computeBasePrice(
        branchId: UUID,
        sessionType: SessionType,
    ): BigDecimal {
        val activeRates = SessionBaseRateRepository.findActiveByBranch(branchId)
        return activeRates
            .firstOrNull { it.sessionType == sessionType }
            ?.rate
            ?: throw ValidationException("No base rate configured for session type $sessionType at this branch")
    }

    /**
     * #424 — BR §Clients: after a free session the next visit's DEFAULT offered price is the
     * branch's SUBSEQUENT base rate instead of the derived type's rate. The trigger keys on
     * price alone (owner ruling 2026-08-26): the client's most recent non-MEDICAL_MISSION,
     * non-voided session has final price ₱0 — mission sessions are ₱0 by definition and never
     * count toward history, and voids don't count as "most recent". Session TYPE stays
     * count-derived via [computeSessionType]; only the default price changes. The
     * practitioner's explicit finalPrice at create always wins. Mission-branch creates keep
     * their ₱0 invariant (#405): no SUBSEQUENT default is offered there.
     */
    private fun resolveDefaultBasePrice(
        branchId: UUID,
        clientId: UUID,
        sessionType: SessionType,
    ): BigDecimal =
        if (sessionType != SessionType.MEDICAL_MISSION &&
            SessionRepository.findMostRecentPriorSessionFinalPrice(clientId)?.signum() == 0
        ) {
            computeBasePrice(branchId, SessionType.SUBSEQUENT)
        } else {
            computeBasePrice(branchId, sessionType)
        }

    fun updateStatus(
        callerId: UUID,
        sessionId: UUID,
        newStatus: SessionStatus,
        expectedVersion: Int,
        reason: String? = null,
    ): Session =
        transaction {
            val initial =
                SessionRepository.findByIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")
            // Client-first lock order matches create, anonymize, void, and unvoid commands.
            val client =
                ClientReads.acquireLockInTransaction(initial.clientId)
                    ?: throw NotFoundException("Client not found")
            val session =
                SessionRepository.acquireLockInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")

            if (session.version != expectedVersion) {
                throw ConflictException("Session version mismatch")
            }

            if (!isStatusTransitionAllowed(session.sessionStatus, newStatus, session.isWalkIn)) {
                throw ValidationException(
                    "Illegal session status transition from ${session.sessionStatus} to $newStatus",
                )
            }

            val requiresEditPastDay = isStatusCorrection(session.sessionStatus, newStatus)
            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(
                    callerId,
                    session.branchDayId,
                    reason,
                    requiresEditPastDay,
                )

            if (newStatus == SessionStatus.PENDING && client.deletedAt != null) {
                throw ConflictException("Cannot reopen a session for an anonymized client")
            }

            if (newStatus == SessionStatus.PENDING &&
                SessionRepository.hasActivePendingSessionInTransaction(session.clientId, sessionId)
            ) {
                throw ConflictException("Client already has an active PENDING session")
            }

            val updated = SessionRepository.updateStatusInTransaction(sessionId, newStatus, expectedVersion)

            SessionAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                session,
                updated,
            )

            logger.info {
                "[UPDATE-SESSION-STATUS] Session $sessionId status changed" +
                    " from ${session.sessionStatus.name} to ${newStatus.name}"
            }

            updated
        }

    fun updateFinalPrice(
        callerId: UUID,
        sessionId: UUID,
        newFinalPrice: BigDecimal,
        expectedVersion: Int,
        reason: String? = null,
    ): Session =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val initial =
                SessionRepository.findByIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")
            // Keep every session mutation's lock order client -> session -> branch day.
            ClientReads.acquireLockInTransaction(initial.clientId)
                ?: throw NotFoundException("Client not found")
            val session =
                SessionRepository.acquireLockInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")

            if (session.version != expectedVersion) {
                throw ConflictException("Session version mismatch")
            }

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, session.branchDayId, reason)

            // #405 — same invariant as create (BR §Session types): a medical-mission session
            // carries MEDICAL_MISSION for life, so its price normalizes to ₱0 no matter what
            // the caller sent.
            val effectivePrice =
                if (session.sessionType == SessionType.MEDICAL_MISSION) BigDecimal.ZERO else newFinalPrice

            val updated =
                SessionRepository.updateFinalPriceInTransaction(sessionId, effectivePrice, expectedVersion)

            SessionAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                session,
                updated,
            )

            logger.info {
                "[UPDATE-SESSION-FINAL-PRICE] Session $sessionId final price changed" +
                    " from ${session.finalPrice.toPlainString()} to ${effectivePrice.toPlainString()}"
            }

            updated
        }

    fun voidSession(
        callerId: UUID,
        sessionId: UUID,
        voidId: UUID,
        voidReason: String,
    ): VoidResult {
        return transaction {
            val initial =
                SessionRepository.findByIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")
            // Client-first lock order serializes a PENDING void with create and anonymize guards.
            ClientReads.acquireLockInTransaction(initial.clientId)
                ?: throw NotFoundException("Client not found")
            val session =
                SessionRepository.acquireLockInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")
            val existing = SessionVoidRepository.findBySessionIdInTransaction(sessionId)
            if (existing != null && existing.unvoidedAt == null) {
                SessionRepository.setVoidedInTransaction(sessionId, true)
                logger.info { "[VOID-SESSION] Session $sessionId already voided, returning existing (idempotent)" }
                return@transaction VoidResult(existing, false)
            }

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(
                    callerId,
                    session.branchDayId,
                    voidReason,
                )

            val result =
                SessionVoidRepository.voidInTransaction(
                    id = voidId,
                    sessionId = sessionId,
                    voidReason = voidReason,
                    voidedBy = callerId,
                )
            if (result.sessionVoid.unvoidedAt == null) {
                SessionRepository.setVoidedInTransaction(sessionId, true)
            }
            if (result.created) {
                SessionAudit.voidInserted(
                    AuditContext(callerId, branchDay.branchId, isFlagged = isRemitted, reason = voidReason),
                    result.sessionVoid,
                )
            }

            logger.info { "[VOID-SESSION] Session $sessionId voided" }

            result
        }
    }

    fun unvoidSession(
        callerId: UUID,
        sessionId: UUID,
        unvoidedReason: String,
    ): SessionVoid =
        transaction {
            val initial =
                SessionRepository.findByIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")
            // Client-first lock order matches create, anonymize, status, and void commands.
            ClientReads.acquireLockInTransaction(initial.clientId)
                ?: throw NotFoundException("Client not found")
            val session =
                SessionRepository.acquireLockInTransaction(sessionId)
                    ?: throw NotFoundException("Session not found")

            val sessionVoid =
                SessionVoidRepository.findBySessionIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session is not voided")

            if (sessionVoid.unvoidedAt != null) {
                SessionRepository.setVoidedInTransaction(sessionId, false)
                logger.info { "[UNVOID-SESSION] Session $sessionId already unvoided, returning existing (idempotent)" }
                return@transaction sessionVoid
            }

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(
                    callerId,
                    session.branchDayId,
                    unvoidedReason,
                )

            if (session.sessionStatus == SessionStatus.PENDING &&
                SessionRepository.hasActivePendingSessionInTransaction(session.clientId, excludedSessionId = sessionId)
            ) {
                throw ConflictException("Client already has an active PENDING session")
            }

            val updated =
                SessionVoidRepository.unvoidInTransaction(
                    sessionVoidId = sessionVoid.id,
                    unvoidedBy = callerId,
                    unvoidedReason = unvoidedReason,
                ) ?: throw NotFoundException("Session void record not found after unvoid")

            SessionRepository.setVoidedInTransaction(sessionId, false)

            SessionAudit.voidUpdated(
                AuditContext(callerId, branchDay.branchId, isFlagged = isRemitted, reason = unvoidedReason),
                sessionVoid,
                updated,
            )

            logger.info { "[UNVOID-SESSION] Session $sessionId unvoided" }

            updated
        }

    /**
     * #348 — pre-create preview for the SessionCreate screen: the session type create WILL
     * assign and the base rate that defaults the final price. Reads the exact same inputs as
     * [create] (branch type, global non-medical-mission history, free-session SUBSEQUENT
     * default (#424), active branch rate) so the preview can never diverge from the created
     * row. No transaction needed — independent reads; a concurrent client session landing
     * between them only makes the preview stale by one visit, and the server recomputes
     * authoritatively at create.
     */
    fun previewSession(
        branchId: UUID,
        clientId: UUID,
    ): SessionPreview {
        val branchType =
            SessionRepository.getBranchType(branchId)
                ?: throw NotFoundException("Branch not found")
        ClientReads.findById(clientId) ?: throw NotFoundException("Client not found")
        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)
        return SessionPreview(sessionType, resolveDefaultBasePrice(branchId, clientId, sessionType))
    }
}

/**
 * #348 — pre-create preview result: the type create will assign and the rate that defaults the
 * final price. Service-level (no persistence table behind it), mapped to `SessionPreviewResponse`
 * at the route.
 */
data class SessionPreview(
    val sessionType: SessionType,
    val basePrice: BigDecimal,
)

/**
 * Session feature audit vocabulary (#323, ADR-0024 rule 3). Called by the commands inside their
 * own transaction so audit rows commit atomically with the mutation. Owns the persistence-table
 * imports (session + session_void) so the public command surface does not.
 */
internal object SessionAudit {
    fun inserted(
        context: AuditContext,
        session: Session,
    ) = AuditLog.recordInsert(
        tableName = SessionTable.tableName,
        recordId = session.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionTable.auditFields(session),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: Session,
        after: Session,
    ) = AuditLog.recordUpdate(
        tableName = SessionTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionTable::auditFields,
    )

    fun voidInserted(
        context: AuditContext,
        voidRecord: SessionVoid,
    ) = AuditLog.recordInsert(
        tableName = SessionVoidTable.tableName,
        recordId = voidRecord.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionVoidTable.auditFields(voidRecord),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun voidUpdated(
        context: AuditContext,
        before: SessionVoid,
        after: SessionVoid,
    ) = AuditLog.recordUpdate(
        tableName = SessionVoidTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionVoidTable::auditFields,
    )
}
