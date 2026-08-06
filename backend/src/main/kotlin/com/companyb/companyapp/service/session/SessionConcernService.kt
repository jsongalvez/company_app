package com.companyb.companyapp.service.session

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

internal object SessionConcernService {
    private val logger = KotlinLogging.logger {}

    fun getForSession(
        callerId: UUID,
        sessionId: UUID,
    ): List<Concern> {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)
        return ConcernRepository.getConcernsForSession(sessionId)
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun addToSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
    ) {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundException("Concern not found")

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concernId,
            auditFn = { sc ->
                AuditLogRepository.recordInsert(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    fields = SessionConcernTable.auditFields(sc),
                    isFlagged = isRemitted,
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
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundException("Concern not found")

        ConcernRepository.removeFromSession(
            sessionId = sessionId,
            concernId = concernId,
            auditFn = { sc ->
                AuditLogRepository.recordDelete(
                    tableName = SessionConcernTable.tableName,
                    recordId = sessionId,
                    before = sc,
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    isFlagged = isRemitted,
                    auditFields = SessionConcernTable::auditFields,
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
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId)

        val concern =
            ConcernRepository.promoteConcern(
                concernId = concernId,
                label = label,
                createdBy = callerId,
                sessionId = sessionId,
                onConcernCreated = { c ->
                    AuditLogRepository.recordInsert(
                        tableName = ConcernTable.tableName,
                        recordId = c.id,
                        changedBy = callerId,
                        fields = ConcernTable.auditFields(c),
                        isFlagged = isRemitted,
                    )
                },
                onLinkCreated = { sc ->
                    AuditLogRepository.recordInsert(
                        tableName = SessionConcernTable.tableName,
                        recordId = sessionId,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        fields = SessionConcernTable.auditFields(sc),
                        isFlagged = isRemitted,
                    )
                },
                onSessionOtherConcernsCleared = { before, after ->
                    AuditLogRepository.recordUpdate(
                        tableName = SessionTable.tableName,
                        recordId = after.id,
                        before = before,
                        after = after,
                        changedBy = callerId,
                        branchId = branchDay.branchId,
                        isFlagged = isRemitted,
                        auditFields = SessionTable::auditFields,
                    )
                },
            )

        logger.info { "[PROMOTE-CONCERN] Promoted concern '$label' for session $sessionId, cleared other_concerns" }

        return concern
    }
}
