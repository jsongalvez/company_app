package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.AuditLogger
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
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
                AuditLogger.insert(
                    table = SessionConcernTable.tableName,
                    id = sessionId,
                    by = callerId,
                    "sessionId" to sessionId.toString(),
                    "concernId" to sc.concernId.toString(),
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
                AuditLogger.delete(
                    table = SessionConcernTable.tableName,
                    id = sessionId,
                    by = callerId,
                    oldFields = arrayOf("sessionId" to sessionId.toString(), "concernId" to sc.concernId.toString()),
                    newFields = emptyArray(),
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
                    AuditLogger.insert(
                        table = ConcernTable.tableName,
                        id = c.id,
                        by = callerId,
                        "id" to c.id.toString(),
                        "label" to label,
                    )
                },
            )

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concern.id,
            auditFn = { sc ->
                AuditLogger.insert(
                    table = SessionConcernTable.tableName,
                    id = sessionId,
                    by = callerId,
                    "sessionId" to sessionId.toString(),
                    "concernId" to sc.concernId.toString(),
                )
            },
        )

        SessionRepository.updateOtherConcerns(
            sessionId = sessionId,
            otherConcerns = null,
            changedBy = callerId,
            auditFn = { session ->
                AuditLogRepository.record(
                    tableName = SessionTable.tableName,
                    recordId = session.id,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue = AuditLogRepository.jsonField("otherConcerns", session.otherConcerns ?: ""),
                    newValue = AuditLogRepository.jsonField("otherConcerns", ""),
                )
            },
        )

        logger.info { "[PROMOTE-CONCERN] Promoted concern '$label' for session $sessionId, cleared other_concerns" }

        return concern
    }
}
