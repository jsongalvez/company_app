package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SessionCreateResult
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.VoidResult
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoid
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object SessionService {
    private val logger = KotlinLogging.logger {}
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    private fun checkVoidSession(callerId: UUID) {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VOID_SESSION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )
    }

    private const val ZERO = "0"

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
    ): SessionCreateResult {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "EDIT_BRANCH_DATA capability required to create sessions",
        )

        val branchType =
            SessionRepository.getBranchType(branchId)
                ?: throw NotFoundResponse("Branch not found")

        val existing = SessionRepository.findById(id)
        if (existing != null) {
            logger.info { "[CREATE-SESSION] Session $id already exists, returning existing (idempotent)" }
            return SessionCreateResult(existing, false)
        }

        val today = LocalDate.now(manilaZone)
        val branchDay = BranchDayService.resolveOrCreate(branchId, today)

        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)

        val now = OffsetDateTime.now()
        val activeRates = SessionBaseRateRepository.findActiveByBranch(branchId, now)
        val basePrice =
            activeRates
                .firstOrNull { it.sessionType == sessionType }
                ?.rate
                ?: BigDecimal(ZERO)

        val result =
            try {
                SessionRepository.create(
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
                )
            } catch (e: IllegalStateException) {
                when (e.message) {
                    "client_already_has_pending_session" -> {
                        throw ConflictResponse("Client already has an active PENDING session")
                    }

                    else -> {
                        throw e
                    }
                }
            }

        logger.info {
            "[CREATE-SESSION] Session ${result.session.id} created (type=$sessionType, branchType=$branchType)"
        }

        return result
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun updateStatus(
        callerId: UUID,
        sessionId: UUID,
        newStatus: SessionStatus,
        expectedVersion: Int,
    ): Session {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")

        if (session.version != expectedVersion) {
            throw ConflictResponse("Session version mismatch")
        }

        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = "EDIT_BRANCH_DATA capability required to update session status",
        )

        BranchDayService.assertEditable(session.branchDayId, callerId)

        if (session.isWalkIn && newStatus in setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)) {
            throw BadRequestResponse("Walk-in sessions cannot transition to NO_SHOW or CANCELLED")
        }

        val oldStatus = SessionStatus.valueOf(session.sessionStatus)

        val updated =
            SessionRepository.updateStatus(
                sessionId = sessionId,
                oldStatus = oldStatus,
                newStatus = newStatus,
                expectedVersion = expectedVersion,
                changedBy = callerId,
            )

        logger.info {
            "[UPDATE-SESSION-STATUS] Session $sessionId status changed" +
                " from ${session.sessionStatus} to ${newStatus.name}"
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
        checkVoidSession(callerId)

        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")

        val existing = SessionVoidRepository.findBySessionId(sessionId)
        if (existing != null) {
            if (existing.unvoidedAt == null) {
                logger.info { "[VOID-SESSION] Session $sessionId already voided, returning existing (idempotent)" }
                return VoidResult(existing, false)
            }
        }

        BranchDayService.assertEditable(session.branchDayId, callerId)

        val result =
            SessionVoidRepository.void(
                id = voidId,
                sessionId = sessionId,
                voidReason = voidReason,
                voidedBy = callerId,
            )

        logger.info { "[VOID-SESSION] Session $sessionId voided" }

        return result
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun unvoidSession(
        callerId: UUID,
        sessionId: UUID,
        unvoidedReason: String,
    ): SessionVoid {
        checkVoidSession(callerId)

        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")

        val sessionVoid =
            SessionVoidRepository.findBySessionId(sessionId)
                ?: throw NotFoundResponse("Session is not voided")

        if (sessionVoid.unvoidedAt != null) {
            logger.info { "[UNVOID-SESSION] Session $sessionId already unvoided, returning existing (idempotent)" }
            return sessionVoid
        }

        BranchDayService.assertEditable(session.branchDayId, callerId)

        val updated =
            SessionVoidRepository.unvoid(
                sessionVoidId = sessionVoid.id,
                unvoidedBy = callerId,
                unvoidedReason = unvoidedReason,
            ) ?: throw NotFoundResponse("Session void record not found after unvoid")

        logger.info { "[UNVOID-SESSION] Session $sessionId unvoided" }

        return updated
    }

    private fun checkEditBranchData(callerId: UUID) {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
    ): AddPractitionerResult {
        checkEditBranchData(callerId)

        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val existing = SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
        if (existing != null) {
            logger.info { "[ADD-PRACTITIONER] Practitioner $practitionerId already in session $sessionId (idempotent)" }
            return AddPractitionerResult(existing, false)
        }

        val branchDay =
            BranchDayRepository.findById(session.branchDayId)
                ?: throw NotFoundResponse("Branch day not found")

        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchDay.branchId, practitionerId)
        val slotAtTime = assignment?.slot ?: DEFAULT_SLOT

        val result =
            SessionPractitionerRepository.add(
                id = id,
                sessionId = sessionId,
                practitionerId = practitionerId,
                slotAtTime = slotAtTime,
                remarks = remarks,
                changedBy = callerId,
            )

        logger.info { "[ADD-PRACTITIONER] Added practitioner $practitionerId to session $sessionId slot=$slotAtTime" }

        return result
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun updatePractitionerRemarks(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
    ): SessionPractitioner {
        checkEditBranchData(callerId)

        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val existing = SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
        if (existing == null) {
            throw NotFoundResponse("Practitioner not found in session")
        }

        val updated =
            SessionPractitionerRepository.updateRemarks(
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = remarks,
                changedBy = callerId,
            ) ?: throw NotFoundResponse("Practitioner not found in session")

        logger.info {
            "[UPDATE-PRACTITIONER-REMARKS] Updated remarks for " +
                "practitioner $practitionerId in session $sessionId"
        }

        return updated
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun removePractitioner(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
    ) {
        checkEditBranchData(callerId)

        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val existing = SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
        if (existing == null) {
            throw NotFoundResponse("Practitioner not found in session")
        }

        SessionPractitionerRepository.remove(
            sessionId = sessionId,
            practitionerId = practitionerId,
            changedBy = callerId,
        )

        logger.info { "[REMOVE-PRACTITIONER] Removed practitioner $practitionerId from session $sessionId" }
    }

    private const val DEFAULT_SLOT: Short = 999
}
