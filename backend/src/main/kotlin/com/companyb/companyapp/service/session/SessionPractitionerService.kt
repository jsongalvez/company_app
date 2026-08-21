package com.companyb.companyapp.service.session

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

internal object SessionPractitionerService {
    private val logger = KotlinLogging.logger {}

    private const val DEFAULT_SLOT: Short = 999

    @Suppress("ReturnCount", "ThrowsCount", "LongParameterList")
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): AddPractitionerResult {
        val (_, branchDay, isRemitted) = resolveSession(sessionId, callerId, reason)

        val existing = SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
        if (existing != null) {
            logger.info { "[ADD-PRACTITIONER] Practitioner $practitionerId already in session $sessionId (idempotent)" }
            return AddPractitionerResult(existing, false)
        }

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
                    AuditLogRepository.recordInsert(
                        tableName = SessionPractitionerTable.tableName,
                        recordId = p.id,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        fields = SessionPractitionerTable.auditFields(p),
                        isFlagged = isRemitted,
                        reason = reason,
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
        reason: String? = null,
    ): SessionPractitioner {
        val (_, branchDay, isRemitted) = resolveSession(sessionId, callerId, reason)
        val updated =
            SessionPractitionerRepository.updateRemarks(
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = remarks,
                auditFn = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = SessionPractitionerTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        isFlagged = isRemitted,
                        reason = reason,
                        auditFields = SessionPractitionerTable::auditFields,
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
        reason: String? = null,
    ) {
        val (_, branchDay, isRemitted) = resolveSession(sessionId, callerId, reason)
        requirePractitionerInSession(sessionId, practitionerId)

        val removed =
            SessionPractitionerRepository.remove(
                sessionId = sessionId,
                practitionerId = practitionerId,
                auditFn = { p ->
                    AuditLogRepository.recordDelete(
                        tableName = SessionPractitionerTable.tableName,
                        recordId = p.id,
                        before = p,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        isFlagged = isRemitted,
                        reason = reason,
                        auditFields = SessionPractitionerTable::auditFields,
                    )
                },
            )
        if (!removed) throw NotFoundException("Practitioner not found in session")

        logger.info { "[REMOVE-PRACTITIONER] Removed practitioner $practitionerId from session $sessionId" }
    }

    private fun resolveSession(
        sessionId: UUID,
        callerId: UUID,
        reason: String?,
    ): Triple<Session, BranchDay, Boolean> {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)
        return Triple(session, branchDay, isRemitted)
    }

    private fun requirePractitionerInSession(
        sessionId: UUID,
        practitionerId: UUID,
    ): SessionPractitioner =
        SessionPractitionerRepository.findBySessionAndPractitioner(sessionId, practitionerId)
            ?: throw NotFoundException("Practitioner not found in session")
}
