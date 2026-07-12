package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.SessionCreateResult
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object SessionService {
    private val logger = KotlinLogging.logger {}
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    private const val EDIT_BRANCH_DATA = "EDIT_BRANCH_DATA"
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

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount")
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
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required to create sessions")
        }

        val branchType =
            SessionRepository.getBranchType(branchId)
                ?: throw NotFoundResponse("Branch not found")

        val existing = SessionRepository.findById(id)
        if (existing != null) {
            logger.info { "[CREATE-SESSION] Session $id already exists, returning existing (idempotent)" }
            return SessionCreateResult(existing, false)
        }

        val hasActive = SessionRepository.hasActivePendingSession(clientId)
        if (hasActive) {
            throw ConflictResponse("Client already has an active PENDING session")
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

        logger.info {
            "[CREATE-SESSION] Session ${result.session.id} created (type=$sessionType, branchType=$branchType)"
        }

        return result
    }
}
