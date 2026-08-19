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
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionBaseRate
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

@Suppress("TooManyFunctions")
object SessionService {
    private val logger = KotlinLogging.logger {}
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

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

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount", "LongMethod")
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
            logger.info { "[CREATE-SESSION] Session $id already exists, returning existing (idempotent)" }
            return SessionCreateResult(existing, false)
        }

        val today = LocalDate.now(manilaZone)
        val branchDay =
            gatedBranchDayId?.let { BranchDayService.requireBranchDayForBranch(it, branchId) }
                ?: BranchDayService.resolveOrCreate(branchId, today)

        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)

        val basePrice = computeBasePrice(branchId, sessionType)

        val result =
            SessionRepository.create(
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
            ) { session ->
                AuditLogRepository.recordInsert(
                    tableName = SessionTable.tableName,
                    recordId = session.id,
                    changedBy = callerId,
                    branchId = branchId,
                    fields = SessionTable.auditFields(session),
                )
            }

        logger.info {
            "[CREATE-SESSION] Session ${result.session.id} created (type=$sessionType, branchType=$branchType)"
        }

        return result
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
    ): Session {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        if (session.version != expectedVersion) {
            throw ConflictException("Session version mismatch")
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)

        if (session.isWalkIn && newStatus in setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)) {
            throw ValidationException("Walk-in sessions cannot transition to NO_SHOW or CANCELLED")
        }

        val oldStatus = session.sessionStatus

        val updated =
            SessionRepository.updateStatus(
                sessionId = sessionId,
                oldStatus = oldStatus,
                newStatus = newStatus,
                expectedVersion = expectedVersion,
                changedBy = callerId,
            ) { updatedSession ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionTable.tableName,
                    recordId = sessionId,
                    before = session,
                    after = updatedSession,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    isFlagged = isRemitted,
                    reason = reason,
                    auditFields = SessionTable::auditFields,
                )
            }

        logger.info {
            "[UPDATE-SESSION-STATUS] Session $sessionId status changed" +
                " from ${session.sessionStatus.name} to ${newStatus.name}"
        }

        return updated
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun updateFinalPrice(
        callerId: UUID,
        sessionId: UUID,
        newFinalPrice: BigDecimal,
        expectedVersion: Int,
        reason: String? = null,
    ): Session {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        if (session.version != expectedVersion) {
            throw ConflictException("Session version mismatch")
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)

        val updated =
            SessionRepository.updateFinalPrice(
                sessionId = sessionId,
                newFinalPrice = newFinalPrice,
                expectedVersion = expectedVersion,
                changedBy = callerId,
            ) { updatedSession ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionTable.tableName,
                    recordId = sessionId,
                    before = session,
                    after = updatedSession,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    isFlagged = isRemitted,
                    reason = reason,
                    auditFields = SessionTable::auditFields,
                )
            }

        logger.info {
            "[UPDATE-SESSION-FINAL-PRICE] Session $sessionId final price changed" +
                " from ${session.finalPrice.toPlainString()} to ${newFinalPrice.toPlainString()}"
        }

        return updated
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun voidSession(
        callerId: UUID,
        sessionId: UUID,
        voidId: UUID,
        voidReason: String,
    ): VoidResult {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        val existing = SessionVoidRepository.findBySessionId(sessionId)
        if (existing != null) {
            if (existing.unvoidedAt == null) {
                logger.info { "[VOID-SESSION] Session $sessionId already voided, returning existing (idempotent)" }
                return VoidResult(existing, false)
            }
        }

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, voidReason)

        val result =
            SessionVoidRepository.void(
                id = voidId,
                sessionId = sessionId,
                voidReason = voidReason,
                voidedBy = callerId,
            ) { voidRecord ->
                AuditLogRepository.recordInsert(
                    tableName = SessionVoidTable.tableName,
                    recordId = voidRecord.id,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    fields = SessionVoidTable.auditFields(voidRecord),
                    isFlagged = isRemitted,
                    reason = voidReason,
                )
            }

        logger.info { "[VOID-SESSION] Session $sessionId voided" }

        return result
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun unvoidSession(
        callerId: UUID,
        sessionId: UUID,
        unvoidedReason: String,
    ): SessionVoid {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        val sessionVoid =
            SessionVoidRepository.findBySessionId(sessionId)
                ?: throw NotFoundException("Session is not voided")

        if (sessionVoid.unvoidedAt != null) {
            logger.info { "[UNVOID-SESSION] Session $sessionId already unvoided, returning existing (idempotent)" }
            return sessionVoid
        }

        val (branchDay, isRemitted) =
            BranchDayService.checkBranchDayEditable(
                callerId,
                session.branchDayId,
                unvoidedReason,
            )

        val updated =
            SessionVoidRepository.unvoid(
                sessionVoidId = sessionVoid.id,
                unvoidedBy = callerId,
                unvoidedReason = unvoidedReason,
            ) { unvoided ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionVoidTable.tableName,
                    recordId = unvoided.id,
                    before = sessionVoid,
                    after = unvoided,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    isFlagged = isRemitted,
                    reason = unvoidedReason,
                    auditFields = SessionVoidTable::auditFields,
                )
            } ?: throw NotFoundException("Session void record not found after unvoid")

        logger.info { "[UNVOID-SESSION] Session $sessionId unvoided" }

        return updated
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
