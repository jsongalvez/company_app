package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.AuditLogger
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import io.github.oshai.kotlinlogging.KotlinLogging
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
        val session = resolveSession(sessionId, callerId)

        val existing = SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
        if (existing != null) {
            logger.info { "[ADD-PRACTITIONER] Practitioner $practitionerId already in session $sessionId (idempotent)" }
            return AddPractitionerResult(existing, false)
        }

        val branchDay =
            BranchDayRepository.findById(session.branchDayId)
                ?: throw NotFoundException("Branch day not found")

        val assignment = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchDay.branchId, practitionerId)
        val slotAtTime = assignment?.slot ?: DEFAULT_SLOT

        val result =
            SessionPractitionerRepository.add(
                id = id,
                sessionId = sessionId,
                practitionerId = practitionerId,
                slotAtTime = slotAtTime,
                remarks = remarks,
                auditFn = { p ->
                    AuditLogger.insert(
                        table = SessionPractitionerTable.tableName,
                        id = p.id,
                        by = callerId,
                        "sessionId" to sessionId.toString(),
                        "practitionerId" to p.practitionerId.toString(),
                        "slotAtTime" to slotAtTime.toString(),
                    )
                },
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
        resolveSession(sessionId, callerId)
        requirePractitionerInSession(sessionId, practitionerId)

        val updated =
            SessionPractitionerRepository.updateRemarks(
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = remarks,
                auditFn = { p ->
                    AuditLogRepository.record(
                        tableName = SessionPractitionerTable.tableName,
                        recordId = p.id,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        newValue = AuditLogRepository.jsonField("remarks", remarks ?: ""),
                    )
                },
            ) ?: throw NotFoundException("Practitioner not found in session")

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
        resolveSession(sessionId, callerId)
        requirePractitionerInSession(sessionId, practitionerId)

        SessionPractitionerRepository.remove(
            sessionId = sessionId,
            practitionerId = practitionerId,
            auditFn = { p ->
                AuditLogger.delete(
                    table = SessionPractitionerTable.tableName,
                    id = practitionerId,
                    by = callerId,
                    oldFields =
                        arrayOf(
                            "sessionId" to sessionId.toString(),
                            "practitionerId" to p.practitionerId.toString(),
                        ),
                    newFields = arrayOf(),
                )
            },
        )

        logger.info { "[REMOVE-PRACTITIONER] Removed practitioner $practitionerId from session $sessionId" }
    }

    private fun resolveSession(
        sessionId: UUID,
        callerId: UUID,
    ): Session {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)
        return session
    }

    private fun requirePractitionerInSession(
        sessionId: UUID,
        practitionerId: UUID,
    ): SessionPractitioner =
        SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
            ?: throw NotFoundException("Practitioner not found in session")
}
