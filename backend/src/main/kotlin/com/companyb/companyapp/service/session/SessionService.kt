package com.companyb.companyapp.service.session

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SessionCreateParams
import com.companyb.companyapp.repository.SessionCreateResult
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.SetRateResult
import com.companyb.companyapp.repository.VoidResult
import com.companyb.companyapp.repository.findSessionByIdInTransaction
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
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("TooManyFunctions")
object SessionService {
    private val logger = KotlinLogging.logger {}

    private val WALK_IN_FORBIDDEN_STATUSES = setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)

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
        bookedAt: OffsetDateTime?,
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

        val basePrice = computeBasePrice(branchId, sessionType)

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
                        finalPrice = finalPrice,
                        remarks = remarks,
                        otherConcerns = otherConcerns,
                        bookedAt = bookedAt,
                        nextAppointmentDate = nextAppointmentDate,
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                SessionAudit.inserted(
                    changedBy = callerId,
                    branchId = branchId,
                    session = result.session,
                )
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

    @Suppress("ReturnCount", "ThrowsCount")
    fun updateStatus(
        callerId: UUID,
        sessionId: UUID,
        newStatus: SessionStatus,
        expectedVersion: Int,
        reason: String? = null,
    ): Session =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")

            if (session.version != expectedVersion) {
                throw ConflictException("Session version mismatch")
            }

            val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)

            if (session.isWalkIn && newStatus in WALK_IN_FORBIDDEN_STATUSES) {
                throw ValidationException("Walk-in sessions cannot transition to NO_SHOW or CANCELLED")
            }

            val updated = SessionRepository.updateStatusInTransaction(sessionId, newStatus, expectedVersion)

            SessionAudit.updated(
                changedBy = callerId,
                branchId = branchDay.branchId,
                before = session,
                after = updated,
                isFlagged = isRemitted,
                reason = reason,
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
            val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")

            if (session.version != expectedVersion) {
                throw ConflictException("Session version mismatch")
            }

            val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)

            val updated =
                SessionRepository.updateFinalPriceInTransaction(sessionId, newFinalPrice, expectedVersion)

            SessionAudit.updated(
                changedBy = callerId,
                branchId = branchDay.branchId,
                before = session,
                after = updated,
                isFlagged = isRemitted,
                reason = reason,
            )

            logger.info {
                "[UPDATE-SESSION-FINAL-PRICE] Session $sessionId final price changed" +
                    " from ${session.finalPrice.toPlainString()} to ${newFinalPrice.toPlainString()}"
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
        val existing = SessionVoidRepository.findBySessionId(sessionId)
        if (existing != null && existing.unvoidedAt == null) {
            logger.info { "[VOID-SESSION] Session $sessionId already voided, returning existing (idempotent)" }
            return VoidResult(existing, false)
        }

        return transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditable(
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
            if (result.created) {
                SessionAudit.voidInserted(
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    voidRecord = result.sessionVoid,
                    isFlagged = isRemitted,
                    reason = voidReason,
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
            // Transaction-local before-state (ADR-0019): reads inside the command's transaction.
            val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")

            val sessionVoid =
                SessionVoidRepository.findBySessionIdInTransaction(sessionId)
                    ?: throw NotFoundException("Session is not voided")

            if (sessionVoid.unvoidedAt != null) {
                logger.info { "[UNVOID-SESSION] Session $sessionId already unvoided, returning existing (idempotent)" }
                return@transaction sessionVoid
            }

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditable(
                    callerId,
                    session.branchDayId,
                    unvoidedReason,
                )

            val updated =
                SessionVoidRepository.unvoidInTransaction(
                    sessionVoidId = sessionVoid.id,
                    unvoidedBy = callerId,
                    unvoidedReason = unvoidedReason,
                ) ?: throw NotFoundException("Session void record not found after unvoid")

            SessionAudit.voidUpdated(
                changedBy = callerId,
                branchId = branchDay.branchId,
                before = sessionVoid,
                after = updated,
                isFlagged = isRemitted,
                reason = unvoidedReason,
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
 * Session feature audit vocabulary (#323, ADR-0024 rule 3). Called by the commands inside their
 * own transaction so audit rows commit atomically with the mutation. Owns the persistence-table
 * imports (session + session_void) so the public command surface does not.
 */
internal object SessionAudit {
    fun inserted(
        changedBy: UUID,
        branchId: UUID,
        session: Session,
    ) = AuditLogRepository.recordInsert(
        tableName = SessionTable.tableName,
        recordId = session.id,
        changedBy = changedBy,
        branchId = branchId,
        fields = SessionTable.auditFields(session),
    )

    fun updated(
        changedBy: UUID,
        branchId: UUID,
        before: Session,
        after: Session,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordUpdate(
        tableName = SessionTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
        auditFields = SessionTable::auditFields,
    )

    fun voidInserted(
        changedBy: UUID,
        branchId: UUID,
        voidRecord: SessionVoid,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordInsert(
        tableName = SessionVoidTable.tableName,
        recordId = voidRecord.id,
        changedBy = changedBy,
        branchId = branchId,
        fields = SessionVoidTable.auditFields(voidRecord),
        isFlagged = isFlagged,
        reason = reason,
    )

    fun voidUpdated(
        changedBy: UUID,
        branchId: UUID,
        before: SessionVoid,
        after: SessionVoid,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordUpdate(
        tableName = SessionVoidTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
        auditFields = SessionVoidTable::auditFields,
    )
}
