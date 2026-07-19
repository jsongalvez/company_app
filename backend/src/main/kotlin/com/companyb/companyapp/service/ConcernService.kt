package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.AuditValues
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object ConcernService {
    private val logger = KotlinLogging.logger {}

    fun listAll(): List<Concern> = ConcernRepository.findAll()

    fun getForSession(sessionId: UUID): List<Concern> {
        SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        return ConcernRepository.getConcernsForSession(sessionId)
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun addToSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
    ) {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundException("Concern not found")

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concernId,
            auditFn = { sc ->
                AuditLogRepository.record(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    action = AuditAction.INSERT,
                    changedBy = callerId,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "concernId" to sc.concernId.toString(),
                        ),
                )
            },
        )

        logger.info { "[ADD-CONCERN] Added concern ${concern.label} to session $sessionId" }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun removeFromSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
    ) {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundException("Concern not found")

        ConcernRepository.removeFromSession(
            sessionId = sessionId,
            concernId = concernId,
            auditFn = { sc ->
                AuditLogRepository.record(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    action = AuditAction.DELETE,
                    changedBy = callerId,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "concernId" to sc.concernId.toString(),
                        ),
                )
            },
        )

        logger.info { "[REMOVE-CONCERN] Removed concern ${concern.label} from session $sessionId" }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun promoteConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        label: String,
    ): Concern {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern =
            ConcernRepository.create(
                id = concernId,
                label = label,
                createdBy = callerId,
                auditFn = { c ->
                    AuditLogRepository.recordInsert(
                        tableName = ConcernTable.tableName,
                        recordId = c.id,
                        changedBy = callerId,
                        fields = ConcernTable.auditFields(c),
                    )
                },
            )

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concern.id,
            auditFn = { sc ->
                AuditLogRepository.record(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    action = AuditAction.INSERT,
                    changedBy = callerId,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "sessionId" to sessionId.toString(),
                            "concernId" to sc.concernId.toString(),
                        ),
                )
            },
        )

        val oldOtherConcerns = session.otherConcerns

        SessionRepository.updateOtherConcerns(
            sessionId = sessionId,
            otherConcerns = null,
            changedBy = callerId,
            auditFn = { updatedSession ->
                AuditLogRepository.recordUpdate(
                    tableName = SessionTable.tableName,
                    recordId = updatedSession.id,
                    oldFields = mapOf("otherConcerns" to (oldOtherConcerns ?: AuditValues.NULL)),
                    newFields = mapOf("otherConcerns" to AuditValues.NULL),
                    changedBy = callerId,
                )
            },
        )

        logger.info { "[PROMOTE-CONCERN] Promoted concern '$label' for session $sessionId, cleared other_concerns" }

        return concern
    }
}
