package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientReads
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Session concern commands (ADR-0024). Each mutating command owns exactly one business
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

    /** Turns the session's free-text "other concerns" into a listed concern and clears the text. */
    fun promoteConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        label: String,
        reason: String? = null,
    ): Concern =
        transaction {
            promoteInTransaction(callerId, sessionId, NewConcern(concernId, label), reason, clearOtherConcerns = true)
        }

    /** Adds a brand-new concern to the shared list and to this session; leaves "other concerns" alone. */
    fun addNewConcern(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
        label: String,
        reason: String? = null,
    ): Concern =
        transaction {
            promoteInTransaction(callerId, sessionId, NewConcern(concernId, label), reason, clearOtherConcerns = false)
        }

    private data class NewConcern(
        val id: UUID,
        val label: String,
    )

    private fun promoteInTransaction(
        callerId: UUID,
        sessionId: UUID,
        newConcern: NewConcern,
        reason: String?,
        clearOtherConcerns: Boolean,
    ): Concern =
        run {
            val (concernId, label) = newConcern
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            val result =
                ConcernRepository.promoteConcernInTransaction(
                    concernId = concernId,
                    label = label,
                    createdBy = callerId,
                    sessionId = sessionId,
                    clearOtherConcerns = clearOtherConcerns,
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
            if (result.sessionBefore != result.sessionAfter) {
                SessionConcernAudit.otherConcernsCleared(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    result.sessionBefore,
                    result.sessionAfter,
                )
            }

            logger.info {
                "[PROMOTE-CONCERN] Concern '$label' on session $sessionId, cleared text: $clearOtherConcerns"
            }

            result.concern
        }

    private fun resolveSessionInTransaction(
        sessionId: UUID,
        callerId: UUID,
        reason: String?,
    ): Triple<Session, BranchDay, Boolean> {
        val session = SessionRepository.findByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
        // client-first lock order (the precedent): serialize with
        // ClientService.anonymize on the client row so a concern write can
        // never commit inside the anonymize census-to-redaction window. Writes
        // for anonymized clients 409 (sticky anonymization, the session-create
        // and PENDING-reopen 409 precedent) instead of reintroducing the
        // free text #908 scrubs; reads stay open via getForSession.
        val client =
            ClientReads.acquireLockInTransaction(session.clientId) ?: throw NotFoundException("Client not found")
        if (client.deletedAt != null) {
            throw ConflictException("Cannot modify session concerns for an anonymized client")
        }
        val (branchDay, isRemitted) =
            BranchDayService.checkBranchDayEditableInTransaction(callerId, session.branchDayId, reason)
        return Triple(session, branchDay, isRemitted)
    }
}

/**
 * Session-concern audit vocabulary (ADR-0024 rule 3). Called by the commands inside their
 * own transaction so the audit rows commit atomically with the mutation. Owns the persistence-
 * table imports (session_concern + the concern/session rows promotion touches).
 */
internal object SessionConcernAudit {
    fun linkInserted(
        context: AuditContext,
        sessionId: UUID,
        concernId: UUID,
    ) = AuditLog.recordInsert(
        context = context,
        row = AuditedRow(SessionConcernTable.tableName, sessionId),
        fields = SessionConcernTable.auditFields(SessionConcern(sessionId, concernId)),
    )

    fun linkDeleted(
        context: AuditContext,
        sessionId: UUID,
        concernId: UUID,
    ) = AuditLog.recordDelete(
        context = context,
        row = AuditedRow(SessionConcernTable.tableName, sessionId),
        before = SessionConcern(sessionId, concernId),
        auditFields = SessionConcernTable::auditFields,
    )

    fun concernInserted(
        context: AuditContext,
        concern: Concern,
    ) = AuditLog.recordInsert(
        context =
            AuditContext(
                changedBy = context.changedBy,
                isFlagged = context.isFlagged,
                reason = context.reason,
            ),
        row = AuditedRow(ConcernTable.tableName, concern.id),
        fields = ConcernTable.auditFields(concern),
    )

    fun concernRemovedFromList(
        changedBy: UUID,
        concern: Concern,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy),
        row = AuditedRow(ConcernTable.tableName, concern.id),
        oldFields = mapOf("label" to concern.label, "onList" to "yes"),
        newFields = mapOf("label" to concern.label, "onList" to "no"),
    )

    fun otherConcernsCleared(
        context: AuditContext,
        before: Session,
        after: Session,
    ) = AuditLog.recordUpdate(
        context = context,
        row = AuditedRow(SessionTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = SessionTable::auditFields,
    )
}
