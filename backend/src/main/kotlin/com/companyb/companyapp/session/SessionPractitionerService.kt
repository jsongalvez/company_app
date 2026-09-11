package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientReads
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.workforce.WorkforceReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Session practitioner commands (#323, ADR-0024). Each mutating command owns exactly one
 * business transaction: the before-state read, day gate, store write (with its session-version
 * bump), and the audit insert all run on it — so domain write + audit commit atomically.
 */
internal object SessionPractitionerService {
    private val logger = KotlinLogging.logger {}

    private const val DEFAULT_SLOT: Short = 999

    /**
     * Practitioner list read (#348 — the route the frontend's add-self refresh calls; it was
     * never registered). Mirrors [SessionConcernService.getForSession]: 404 for a missing
     * session, then the day-state readability gate. Ordered by slot (display order, BR §206).
     */
    fun getForSession(
        callerId: UUID,
        sessionId: UUID,
    ): List<SessionPractitioner> {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundException("Session not found")
        BranchDayService.checkBranchDayReadable(callerId, session.branchDayId)
        return SessionPractitionerRepository.findBySessionId(sessionId)
    }

    // #593 six-param add stays whole (coherent assignment inputs; #535 no arbitrary DTO).
    @Suppress("LongParameterList") // #593
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): AddPractitionerResult {
        if (!AccountReads.userExists(practitionerId)) {
            throw NotFoundException("User not found")
        }
        return transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            val existing =
                SessionPractitionerRepository.findBySessionAndPractitionerInTransaction(
                    sessionId,
                    practitionerId,
                )
            if (existing != null) {
                logger.info {
                    "[ADD-PRACTITIONER] Practitioner $practitionerId already in session $sessionId (idempotent)"
                }
                return@transaction AddPractitionerResult(existing, false)
            }

            val slotAtTime =
                WorkforceReads.findAssignmentSlotInTransaction(branchDay.branchId, practitionerId)
                    ?: DEFAULT_SLOT

            val result =
                SessionPractitionerRepository.addInTransaction(
                    id = id,
                    sessionId = sessionId,
                    practitionerId = practitionerId,
                    slotAtTime = slotAtTime,
                    remarks = remarks,
                )
            if (result.created) {
                SessionPractitionerAudit.inserted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    result.practitioner,
                )
            }

            logger.info {
                "[ADD-PRACTITIONER] Added practitioner $practitionerId to session $sessionId slot=$slotAtTime"
            }

            result
        }
    }

    fun updatePractitionerRemarks(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): SessionPractitioner =
        transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val before =
                SessionPractitionerRepository.findBySessionAndPractitionerInTransaction(sessionId, practitionerId)
                    ?: throw NotFoundException("Practitioner not found in session")

            val after =
                SessionPractitionerRepository.updateRemarksInTransaction(
                    sessionId = sessionId,
                    practitionerId = practitionerId,
                    remarks = remarks,
                )

            SessionPractitionerAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                before,
                after,
            )

            logger.info {
                "[UPDATE-PRACTITIONER-REMARKS] Updated remarks for " +
                    "practitioner $practitionerId in session $sessionId"
            }

            after
        }

    fun removePractitioner(
        callerId: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        reason: String? = null,
    ) {
        transaction {
            val (_, branchDay, isRemitted) = resolveSessionInTransaction(sessionId, callerId, reason)

            // Transaction-local before-state (ADR-0019): read inside the command's transaction;
            // the store operation returns the removed row for the delete audit.
            val removed =
                SessionPractitionerRepository.removeInTransaction(sessionId, practitionerId)
                    ?: throw NotFoundException("Practitioner not found in session")

            SessionPractitionerAudit.deleted(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                removed,
            )

            logger.info { "[REMOVE-PRACTITIONER] Removed practitioner $practitionerId from session $sessionId" }
        }
    }

    private fun resolveSessionInTransaction(
        sessionId: UUID,
        callerId: UUID,
        reason: String?,
    ): Triple<Session, BranchDay, Boolean> {
        val session = SessionRepository.findByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
        // #910 — client-first lock order (the #509 precedent): serialize with
        // ClientService.anonymize on the client row so a practitioner write can
        // never commit inside the anonymize census-to-redaction window. Writes
        // for anonymized clients 409 (sticky anonymization, the session-create
        // #692 and PENDING-reopen 409 precedent) instead of reintroducing the
        // free text #908 scrubs; reads stay open via getForSession.
        val client =
            ClientReads.acquireLockInTransaction(session.clientId) ?: throw NotFoundException("Client not found")
        if (client.deletedAt != null) {
            throw ConflictException("Cannot modify session practitioners for an anonymized client")
        }
        val (branchDay, isRemitted) =
            BranchDayService.checkBranchDayEditableInTransaction(callerId, session.branchDayId, reason)
        return Triple(session, branchDay, isRemitted)
    }
}

/**
 * Session-practitioner audit vocabulary (#323, ADR-0024 rule 3). Called by the commands inside
 * their own transaction so audit rows commit atomically with the mutation. Owns the
 * persistence-table import so the public command surface does not.
 */
internal object SessionPractitionerAudit {
    fun inserted(
        context: AuditContext,
        practitioner: SessionPractitioner,
    ) = AuditLog.recordInsert(
        tableName = SessionPractitionerTable.tableName,
        recordId = practitioner.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = SessionPractitionerTable.auditFields(practitioner),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: SessionPractitioner,
        after: SessionPractitioner,
    ) = AuditLog.recordUpdate(
        tableName = SessionPractitionerTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionPractitionerTable::auditFields,
    )

    fun deleted(
        context: AuditContext,
        practitioner: SessionPractitioner,
    ) = AuditLog.recordDelete(
        tableName = SessionPractitionerTable.tableName,
        recordId = practitioner.id,
        before = practitioner,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = SessionPractitionerTable::auditFields,
    )
}
