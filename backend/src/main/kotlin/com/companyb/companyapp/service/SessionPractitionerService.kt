package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.SessionPractitioner
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

object SessionPractitionerService {
    private val logger = KotlinLogging.logger {}

    private const val DEFAULT_SLOT: Short = 999

    @Suppress("ReturnCount", "ThrowsCount")
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
    ): AddPractitionerResult {
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
}
