package com.companyb.companyapp.service.session

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.findSessionByIdInTransaction
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.Concern
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionConcern
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.branchday.BranchDayService
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
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    sessionId = sessionId,
                    concernId = concernId,
                    isFlagged = isRemitted,
                    reason = reason,
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
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    sessionId = sessionId,
                    concernId = concernId,
                    isFlagged = isRemitted,
                    reason = reason,
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
                    changedBy = callerId,
                    concern = result.concern,
                    isFlagged = isRemitted,
                    reason = reason,
                )
            }
            if (result.linkCreated) {
                SessionConcernAudit.linkInserted(
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    sessionId = sessionId,
                    concernId = concernId,
                    isFlagged = isRemitted,
                    reason = reason,
                )
            }
            SessionConcernAudit.otherConcernsCleared(
                changedBy = callerId,
                branchId = branchDay.branchId,
                before = result.sessionBefore,
                after = result.sessionAfter,
                isFlagged = isRemitted,
                reason = reason,
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
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)
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
        changedBy: UUID,
        branchId: UUID,
        sessionId: UUID,
        concernId: UUID,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordInsert(
        tableName = SessionConcernTable.tableName,
        recordId = sessionId,
        changedBy = changedBy,
        branchId = branchId,
        fields = SessionConcernTable.auditFields(SessionConcern(sessionId, concernId)),
        isFlagged = isFlagged,
        reason = reason,
    )

    fun linkDeleted(
        changedBy: UUID,
        branchId: UUID,
        sessionId: UUID,
        concernId: UUID,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordDelete(
        tableName = SessionConcernTable.tableName,
        recordId = sessionId,
        before = SessionConcern(sessionId, concernId),
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
        auditFields = SessionConcernTable::auditFields,
    )

    fun concernInserted(
        changedBy: UUID,
        concern: Concern,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordInsert(
        tableName = ConcernTable.tableName,
        recordId = concern.id,
        changedBy = changedBy,
        fields = ConcernTable.auditFields(concern),
        isFlagged = isFlagged,
        reason = reason,
    )

    fun otherConcernsCleared(
        changedBy: UUID,
        branchId: UUID,
        before: Session,
        after: Session,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordUpdate(
        tableName = SessionTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = branchId,
        isFlagged = isFlagged,
        reason = reason,
        auditFields = SessionTable::auditFields,
    )
}
