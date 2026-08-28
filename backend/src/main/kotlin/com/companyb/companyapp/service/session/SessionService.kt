package com.companyb.companyapp.service.session

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.domain.isStatusCorrection
import com.companyb.companyapp.domain.isStatusTransitionAllowed
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchMemberRepository
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SessionCreateParams
import com.companyb.companyapp.repository.SessionCreateResult
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.SetRateResult
import com.companyb.companyapp.repository.VoidResult
import com.companyb.companyapp.repository.findSessionByIdInTransaction
import com.companyb.companyapp.repository.hasActivePendingSessionInTransaction
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Suppress("TooManyFunctions")
object SessionService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ReturnCount", "ThrowsCount")
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

    @Suppress("ComplexCondition", "LongParameterList", "ReturnCount", "ThrowsCount", "LongMethod")
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
    ): SessionCreateResult {
        val branchType =
            SessionRepository.getBranchType(branchId)
                ?: throw NotFoundException("Branch not found")

        // #366 — the requested practitioner must be an ACTIVE member of the session's branch
        // (today any existing UUID is accepted). Optional field: null passes untouched.
        if (requestedPractitionerId != null &&
            !BranchMemberRepository.hasActiveMember(branchId, requestedPractitionerId)
        ) {
            throw ValidationException("Requested practitioner is not an active member of this branch")
        }

        val existing = SessionRepository.findById(id)
        if (existing != null) {
            val expectedBranchDayId =
                gatedBranchDayId ?: BranchDayService.findToday(branchId)?.id
            val sameClient = existing.clientId == clientId
            val sameBranch = SessionRepository.branchDayBelongsToBranch(existing.branchDayId, branchId)
            val sameDay = existing.branchDayId == expectedBranchDayId
            val sameCaller = SessionRepository.createdBy(id) == callerId
            if (!sameClient || !sameBranch || !sameDay || !sameCaller) {
                throw ConflictException("Session id already belongs to another create request")
            }
            logger.info { "[CREATE-SESSION] Session $id already exists, returning existing (idempotent)" }
            return SessionCreateResult(existing, false)
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
                SessionAudit.inserted(AuditContext(callerId, branchId), result.session)
            }

            logger.info {
                "[CREATE-SESSION] Session ${result.session.id} created (type=$sessionType, branchType=$branchType)"
            }

            result
        }
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

    @Suppress("ReturnCount", "ThrowsCount")
    fun updateStatus(
        callerId: UUID,
        sessionId: UUID,
        newStatus: SessionStatus,
        expectedVersion: Int,
        reason: String? = null,
    ): Session =
        transaction {
            val initial = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
            // Client-first lock order matches create, anonymize, void, and unvoid commands.
            val client =
                ClientRepository.acquireLockInTransaction(initial.clientId)
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
                hasActivePendingSessionInTransaction(session.clientId, sessionId)
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

    @Suppress("ReturnCount", "ThrowsCount")
    fun updateFinalPrice(
        callerId: UUID,
        sessionId: UUID,
        newFinalPrice: BigDecimal,
        expectedVersion: Int,
        reason: String? = null,
    ): Session =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val initial = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
            // Keep every session mutation's lock order client -> session -> branch day.
            ClientRepository.acquireLockInTransaction(initial.clientId)
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

    @Suppress("ReturnCount", "ThrowsCount")
    fun voidSession(
        callerId: UUID,
        sessionId: UUID,
        voidId: UUID,
        voidReason: String,
    ): VoidResult {
        return transaction {
            val initial = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
            // Client-first lock order serializes a PENDING void with create and anonymize guards.
            ClientRepository.acquireLockInTransaction(initial.clientId)
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

    @Suppress("ReturnCount", "ThrowsCount")
    fun unvoidSession(
        callerId: UUID,
        sessionId: UUID,
        unvoidedReason: String,
    ): SessionVoid =
        transaction {
            val initial = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
            // Client-first lock order matches create, anonymize, status, and void commands.
            ClientRepository.acquireLockInTransaction(initial.clientId)
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
                hasActivePendingSessionInTransaction(session.clientId, excludedSessionId = sessionId)
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

    // --- Practitioner pass-throughs ---

    @Suppress("LongParameterList")
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): AddPractitionerResult =
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = id,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = remarks,
            reason = reason,
        )

    fun updatePractitionerRemarks(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): SessionPractitioner =
        SessionPractitionerService.updatePractitionerRemarks(
            callerId = callerId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = remarks,
            reason = reason,
        )

    fun removePractitioner(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        reason: String? = null,
    ) = SessionPractitionerService.removePractitioner(
        callerId = callerId,
        sessionId = sessionId,
        practitionerId = practitionerId,
        reason = reason,
    )

    // #348 — the read behind GET /api/sessions/{sessionId}/practitioners (add-self refresh).
    fun getSessionPractitioners(
        callerId: UUID,
        sessionId: UUID,
    ): List<SessionPractitioner> = SessionPractitionerService.getForSession(callerId, sessionId)

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
        ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")
        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)
        return SessionPreview(sessionType, resolveDefaultBasePrice(branchId, clientId, sessionType))
    }

    // --- Base rate pass-throughs ---

    fun setRate(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        sessionType: SessionType,
        rate: BigDecimal,
    ): SetRateResult =
        SessionBaseRateService.setRate(
            callerId = callerId,
            id = id,
            branchId = branchId,
            sessionType = sessionType,
            rate = rate,
        )

    fun findActiveRates(branchId: UUID): List<SessionBaseRate> = SessionBaseRateService.findActiveRates(branchId)

    // --- Concern pass-throughs ---

    fun getSessionConcerns(
        callerId: UUID,
        sessionId: UUID,
    ): List<Concern> = SessionConcernService.getForSession(callerId, sessionId)

    fun addSessionConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        reason: String? = null,
    ) = SessionConcernService.addToSession(callerId, sessionId, concernId, reason)

    fun removeSessionConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        reason: String? = null,
    ) = SessionConcernService.removeFromSession(callerId, sessionId, concernId, reason)

    fun promoteConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        label: String,
        reason: String? = null,
    ): Concern = SessionConcernService.promoteConcern(callerId, sessionId, concernId, label, reason)
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
    ) = AuditLogRepository.recordInsert(
        tableName = SessionTable.tableName,
        recordId = session.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionTable.auditFields(session),
    )

    fun updated(
        context: AuditContext,
        before: Session,
        after: Session,
    ) = AuditLogRepository.recordUpdate(
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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
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
