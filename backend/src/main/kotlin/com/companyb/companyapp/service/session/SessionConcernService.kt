package com.companyb.companyapp.service.session

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.findSessionByIdInTransaction
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionConcern
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Session concern commands (#323, ADR-0024). Each mutating command owns exactly one business
 * transaction: the before-state read, day gate, store write, and the audit insert(s) run on it.
 * [promoteConcern] writes THREE audit rows (concern insert, link insert, session other-concerns
 * clear) into that one transaction.
 */
internal object SessionConcernService {
    private val logger = KotlinLogging.logger {}

    fun getForSession(
        callerId: UUID,
        sessionId: UUID,
    ): List<Concern> {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayReadable(callerId, session.branchDayId)
        return ConcernRepository.getConcernsForSession(sessionId)
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun addToSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        reason: String? = null,
    ) {
        transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            val concern =
                ConcernRepository.findByIdInTransaction(concernId) ?: throw NotFoundException("Concern not found")

            val created = ConcernRepository.addToSessionInTransaction(sessionId, concernId)
            if (created) {
                SessionConcernAudit.linkInserted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    sessionId,
                    concernId,
                )
            }

            logger.info { "[ADD-CONCERN] Added concern ${concern.label} to session $sessionId" }
        }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun removeFromSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        reason: String? = null,
    ) {
        transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            val concern =
                ConcernRepository.findByIdInTransaction(concernId) ?: throw NotFoundException("Concern not found")

            val deleted = ConcernRepository.removeFromSessionInTransaction(sessionId, concernId)
            if (deleted) {
                SessionConcernAudit.linkDeleted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    sessionId,
                    concernId,
                )
            }

            logger.info { "[REMOVE-CONCERN] Removed concern ${concern.label} from session $sessionId" }
        }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun promoteConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        label: String,
        reason: String? = null,
    ): Concern =
        transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            val result =
                ConcernRepository.promoteConcernInTransaction(
                    concernId = concernId,
                    label = label,
                    createdBy = callerId,
                    sessionId = sessionId,
                )

            if (result.concernCreated) {
                SessionConcernAudit.concernInserted(
                    AuditContext(callerId, isFlagged = isRemitted, reason = reason),
                    result.concern,
                )
            }
            if (result.linkCreated) {
                SessionConcernAudit.linkInserted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    sessionId,
                    concernId,
                )
            }
            SessionConcernAudit.otherConcernsCleared(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                result.sessionBefore,
                result.sessionAfter,
            )

            logger.info { "[PROMOTE-CONCERN] Promoted concern '$label' for session $sessionId, cleared other_concerns" }

            result.concern
        }

    private fun resolveSessionInTransaction(
        sessionId: UUID,
        callerId: UUID,
        reason: String?,
    ): Triple<Session, BranchDay, Boolean> {
        val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
        val (branchDay, isRemitted) =
            BranchDayService.checkBranchDayEditableInTransaction(callerId, session.branchDayId, reason)
        return Triple(session, branchDay, isRemitted)
    }
}

/**
 * Session-concern audit vocabulary (#323, ADR-0024 rule 3). Called by the commands inside their
 * own transaction so the audit rows commit atomically with the mutation. Owns the persistence-
 * table imports (session_concern + the concern/session rows promotion touches).
 */
internal object SessionConcernAudit {
    fun linkInserted(
        context: AuditContext,
        sessionId: UUID,
        concernId: UUID,
    ) = AuditLog.recordInsert(
        tableName = SessionConcernTable.tableName,
        recordId = sessionId,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionConcernTable.auditFields(SessionConcern(sessionId, concernId)),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun linkDeleted(
        context: AuditContext,
        sessionId: UUID,
        concernId: UUID,
    ) = AuditLog.recordDelete(
        tableName = SessionConcernTable.tableName,
        recordId = sessionId,
        before = SessionConcern(sessionId, concernId),
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionConcernTable::auditFields,
    )

    fun concernInserted(
        context: AuditContext,
        concern: Concern,
    ) = AuditLog.recordInsert(
        tableName = ConcernTable.tableName,
        recordId = concern.id,
        changedBy = context.changedBy,
        fields = ConcernTable.auditFields(concern),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun otherConcernsCleared(
        context: AuditContext,
        before: Session,
        after: Session,
    ) = AuditLog.recordUpdate(
        tableName = SessionTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionTable::auditFields,
    )
}
