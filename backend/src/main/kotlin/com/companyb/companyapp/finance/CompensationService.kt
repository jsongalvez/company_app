package com.companyb.companyapp.finance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Compensation feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * business transaction: persistence runs on it via `CompensationRepository.*InTransaction`
 * store operations, the before/after state is captured inside it (ADR-0019 invariant), and
 * the audit row is inserted directly into it — so domain write + audit commit atomically or
 * not at all.
 */
object CompensationService {
    private val logger = KotlinLogging.logger {}

    fun findByPayingBranchDayId(branchDayId: UUID): List<CompensationWithUser> {
        BranchDayService.requireBranchDayExists(branchDayId)
        return CompensationRepository.findByPayingBranchDayId(branchDayId)
    }

    /**
     * #421: the paying branch day carries the financial effect (remittance snapshot and
     * daily-summary aggregation read the pay side), and business rules pin it to the duty
     * branch — "compensation is deducted from the branch where duty was performed". A day
     * pair spanning branches would let a caller authorized only at the work branch charge
     * another branch's OPEN day, so the shape itself is invalid.
     */
    private fun assertPayingMatchesWork(
        workDay: BranchDay,
        payingDay: BranchDay,
    ) {
        if (payingDay.branchId != workDay.branchId) {
            throw ValidationException("Paying branch day must belong to the same branch as the work branch day")
        }
    }

    // #596: 8-param creation command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #596
    fun create(
        callerId: UUID,
        id: UUID,
        workBranchDayId: UUID,
        payingBranchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        note: String?,
        reason: String? = null,
    ): Compensation {
        // Fast-fail existence check before the transaction; the in-transaction locked
        // gate below re-reads the work day for editability (#859).
        BranchDayService.requireBranchDayExists(workBranchDayId)

        if (!AccountReads.userExists(userId)) {
            throw NotFoundException("User not found")
        }

        return transaction {
            replayIfExistsInTransaction(id, workBranchDayId, payingBranchDayId, userId, callerId)?.let {
                return@transaction it
            }
            // #859: gate BOTH legs — the work day carries the duty being priced, so a
            // REMITTED work day needs the same reason discipline even when the paying day is OPEN.
            val gate =
                checkWorkAndPayingDaysEditableInTransaction(callerId, workBranchDayId, payingBranchDayId, reason)
            assertPayingMatchesWork(gate.workDay, gate.payingDay)

            val result =
                CompensationRepository.createInTransaction(
                    CompensationCreateParams(
                        id = id,
                        workBranchDayId = workBranchDayId,
                        payingBranchDayId = payingBranchDayId,
                        userId = userId,
                        amount = amount,
                        assignedBy = callerId,
                        note = note,
                    ),
                )
            if (result.created) {
                CompensationAudit.inserted(
                    AuditContext(callerId, gate.payingDay.branchId, gate.isRemitted, reason),
                    result.compensation,
                )
            }
            result.compensation
        }.also { created ->
            logger.info {
                "[CREATE-COMPENSATION] Compensation ${created.id.toString().maskUUID()} created"
            }
        }
    }

    /**
     * #859 — locked editability gate for both compensation legs. Days lock in stable
     * sorted-UUID order (the same order remittance submit uses), so a concurrent REMITTED
     * transition serializes with this command instead of deadlocking. Either leg REMITTED
     * flags the audit row. Uses the same `requiresEditPastDay = false` shape as the
     * paying-day gate: PAST needs EDIT_PAST_DAY, REMITTED needs EDIT_PAST_DAY plus a reason.
     */
    private fun checkWorkAndPayingDaysEditableInTransaction(
        callerId: UUID,
        workBranchDayId: UUID,
        payingBranchDayId: UUID,
        reason: String?,
    ): WorkPayingGate {
        val gated =
            listOf(workBranchDayId, payingBranchDayId).sorted().distinct().associateWith { dayId ->
                BranchDayService.checkBranchDayEditableInTransaction(callerId, dayId, reason)
            }
        val (workDay, workIsRemitted) = gated.getValue(workBranchDayId)
        val (payingDay, payingIsRemitted) = gated.getValue(payingBranchDayId)
        return WorkPayingGate(workDay, payingDay, workIsRemitted || payingIsRemitted)
    }

    private data class WorkPayingGate(
        val workDay: BranchDay,
        val payingDay: BranchDay,
        val isRemitted: Boolean,
    )

    /**
     * #511 — in-tx replay classification before the day gate (mirrors #509/#510):
     * a same-id row already committed acks without gating so retries landing
     * after a day transition still ack; a foreign row fails closed.
     * Extracted so [create] stays under the ThrowsCount gate (#695 repair).
     */
    private fun replayIfExistsInTransaction(
        id: UUID,
        workBranchDayId: UUID,
        payingBranchDayId: UUID,
        userId: UUID,
        callerId: UUID,
    ): Compensation? {
        val existing = CompensationRepository.findByIdInTransaction(id) ?: return null
        if (existing.workBranchDayId != workBranchDayId ||
            existing.payingBranchDayId != payingBranchDayId
        ) {
            throw NotFoundException("Compensation not found for this branch day")
        }
        if (existing.userId != userId || existing.assignedBy != callerId) {
            throw ConflictException("Compensation id already belongs to another create request")
        }
        return existing
    }

    // #596: 6-param update command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #596
    fun update(
        callerId: UUID,
        compensationId: UUID,
        amount: BigDecimal,
        note: String?,
        expectedVersion: Int,
        reason: String? = null,
    ): Compensation =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction.
            val before =
                CompensationRepository.findByIdInTransaction(compensationId)
                    ?: throw NotFoundException("Compensation not found")

            // #859: gate BOTH stored legs — the work day was previously re-read for the
            // same-branch invariant only, never for editability.
            val gate =
                checkWorkAndPayingDaysEditableInTransaction(
                    callerId,
                    before.workBranchDayId,
                    before.payingBranchDayId,
                    reason,
                )
            // Same-branch invariant on the stored record too (#421): a legacy row whose days
            // span branches must not stay updatable through either day's gate alone.
            assertPayingMatchesWork(gate.workDay, gate.payingDay)

            val after =
                CompensationRepository.updateInTransaction(compensationId, amount, note, expectedVersion)

            CompensationAudit.updated(
                AuditContext(callerId, gate.payingDay.branchId, gate.isRemitted, reason),
                before,
                after,
            )
            after
        }.also {
            logger.info { "[UPDATE-COMPENSATION] Compensation ${compensationId.toString().maskUUID()} updated" }
        }
}

/**
 * Compensation audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object CompensationAudit {
    fun inserted(
        context: AuditContext,
        compensation: Compensation,
    ) = AuditLog.recordInsert(
        tableName = CompensationTable.tableName,
        recordId = compensation.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = CompensationTable.auditFields(compensation),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: Compensation,
        after: Compensation,
    ) = AuditLog.recordUpdate(
        tableName = CompensationTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = CompensationTable::auditFields,
    )
}
