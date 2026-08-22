package com.companyb.companyapp.service.session

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AddPractitionerResult
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.findSessionByIdInTransaction
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionPractitioner
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.service.branchday.BranchDayService
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

    @Suppress("ReturnCount", "ThrowsCount", "LongParameterList")
    fun addPractitioner(
        callerId: UUID,
        id: UUID,
        sessionId: UUID,
        practitionerId: UUID,
        remarks: String?,
        reason: String? = null,
    ): AddPractitionerResult =
        transaction {
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

            val assignment =
                UserBranchAssignmentRepository.findActiveByBranchAndUser(
                    branchDay.branchId,
                    practitionerId,
                )
            val slotAtTime = assignment?.slot ?: DEFAULT_SLOT

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

    @Suppress("ReturnCount", "ThrowsCount")
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

    @Suppress("ReturnCount", "ThrowsCount")
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
        val session = findSessionByIdInTransaction(sessionId) ?: throw NotFoundException("Session not found")
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, session.branchDayId, reason)
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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
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
    ) = AuditLogRepository.recordDelete(
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
