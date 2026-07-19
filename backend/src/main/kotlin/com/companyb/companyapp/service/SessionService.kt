package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.AuditValues
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SessionCreateParams
import com.companyb.companyapp.repository.SessionCreateResult
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.VoidResult
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoid
import com.companyb.companyapp.repository.model.SessionVoidTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
        val branchDay = BranchDayService.resolveOrCreate(branchId, today)

        val priorCount = SessionRepository.countPriorNonMedicalMissionSessions(clientId)
        val sessionType = computeSessionType(branchType, priorCount)

        val basePrice = computeBasePrice(branchId, sessionType)

        val result =
            try {
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
                        fields = SessionTable.auditFields(session),
                    )
                }
            } catch (e: IllegalStateException) {
                when (e.message) {
                    "client_already_has_pending_session" -> {
                        throw ConflictException("Client already has an active PENDING session")
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

    private fun computeBasePrice(
        branchId: UUID,
        sessionType: SessionType,
    ): BigDecimal {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val activeRates = SessionBaseRateRepository.findActiveByBranch(branchId, now)
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
    ): Session {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        if (session.version != expectedVersion) {
            throw ConflictException("Session version mismatch")
        }

        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        if (session.isWalkIn && newStatus in setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)) {
            throw ValidationException("Walk-in sessions cannot transition to NO_SHOW or CANCELLED")
        }

        val oldStatus = SessionStatus.valueOf(session.sessionStatus)

        val updated =
            SessionRepository.updateStatus(
                sessionId = sessionId,
                oldStatus = oldStatus,
                newStatus = newStatus,
                expectedVersion = expectedVersion,
                changedBy = callerId,
            ) { session ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionTable.tableName,
                    recordId = sessionId,
                    oldFields = mapOf("sessionStatus" to oldStatus.name),
                    newFields = mapOf("sessionStatus" to newStatus.name),
                    changedBy = callerId,
                )
            }

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
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")

        val existing = SessionVoidRepository.findBySessionId(sessionId)
        if (existing != null) {
            if (existing.unvoidedAt == null) {
                logger.info { "[VOID-SESSION] Session $sessionId already voided, returning existing (idempotent)" }
                return VoidResult(existing, false)
            }
        }

        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

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
                    fields = SessionVoidTable.auditFields(voidRecord),
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

        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val updated =
            SessionVoidRepository.unvoid(
                sessionVoidId = sessionVoid.id,
                unvoidedBy = callerId,
                unvoidedReason = unvoidedReason,
            ) { unvoided ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionVoidTable.tableName,
                    recordId = unvoided.id,
                    oldFields =
                        mapOf(
                            "unvoidedAt" to AuditValues.NULL,
                            "unvoidedBy" to AuditValues.NULL,
                            "unvoidedReason" to AuditValues.NULL,
                        ),
                    newFields =
                        mapOf(
                            "unvoidedAt" to unvoided.unvoidedAt.toString(),
                            "unvoidedBy" to callerId.toString(),
                            "unvoidedReason" to unvoidedReason,
                        ),
                    changedBy = callerId,
                )
            } ?: throw NotFoundException("Session void record not found after unvoid")

        logger.info { "[UNVOID-SESSION] Session $sessionId unvoided" }

        return updated
    }
}
