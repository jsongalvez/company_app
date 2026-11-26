package com.companyb.companyapp.finance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.DayWrite
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Compensation feature commands (ADR-0024). Each mutating command owns exactly one
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
     * the paying branch day carries the financial effect (remittance snapshot and
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

    fun create(
        callerId: UUID,
        new: NewCompensation,
    ): Compensation {
        return traced("CompensationService.create") {
            // Fast-fail existence check before the transaction; the in-transaction locked
            // gate below re-reads the work day for editability.
            BranchDayService.requireBranchDayExists(new.workBranchDayId)

            if (!AccountReads.userExists(new.userId)) {
                throw NotFoundException("User not found")
            }

            validateMoneyAmount(new.amount, "amount")
            val params =
                CompensationCreateParams(
                    id = new.id,
                    workBranchDayId = new.workBranchDayId,
                    payingBranchDayId = new.payingBranchDayId,
                    userId = new.userId,
                    amount = new.amount,
                    assignedBy = callerId,
                    note = new.note,
                )
            return@traced transaction {
                replayIfExistsInTransaction(params)?.let {
                    return@transaction it
                }
                // gate BOTH legs — the work day carries the duty being priced, so a
                // REMITTED work day needs the same reason discipline even when the paying day is OPEN.
                val gate =
                    checkWorkAndPayingDaysEditableInTransaction(
                        callerId,
                        new.workBranchDayId,
                        new.payingBranchDayId,
                        new.reason,
                    )
                assertPayingMatchesWork(gate.workDay, gate.payingDay)

                val result = CompensationRepository.createInTransaction(params)
                if (result.created) {
                    CompensationAudit.inserted(
                        AuditContext(callerId, gate.payingDay.branchId, gate.isRemitted, new.reason),
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
    }

    /**
     * locked editability gate for both compensation legs. Days lock in stable
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
                BranchDayService.checkBranchDayEditableInTransaction(callerId, dayId, reason, DayWrite.PAY)
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
     * in-tx replay classification before the day gate:
     * a same-id row already committed acks without gating so retries landing
     * after a day transition still ack; a foreign row fails closed.
     * Extracted so [create] stays under the ThrowsCount gate (repair).
     */
    private fun replayIfExistsInTransaction(request: CompensationCreateParams): Compensation? {
        val existing = CompensationRepository.findByIdInTransaction(request.id) ?: return null
        if (existing.workBranchDayId != request.workBranchDayId ||
            existing.payingBranchDayId != request.payingBranchDayId
        ) {
            throw NotFoundException("Compensation not found for this branch day")
        }
        if (existing.userId != request.userId ||
            existing.assignedBy != request.assignedBy ||
            !samePayload(existing, request.amount, request.note)
        ) {
            throw ConflictException("Compensation id already belongs to another create request")
        }
        return existing
    }

    private fun samePayload(
        existing: Compensation,
        amount: BigDecimal,
        note: String?,
    ): Boolean = existing.amount.compareTo(amount) == 0 && existing.note == note

    fun update(
        callerId: UUID,
        compensationId: UUID,
        edit: CompensationEdit,
        expectedVersion: Int,
    ): Compensation {
        return traced("CompensationService.update") {
            validateMoneyAmount(edit.amount, "amount")
            return@traced transaction {
                // Transaction-local before-state (ADR-0019): read inside the command's transaction.
                val before =
                    CompensationRepository.findByIdInTransaction(compensationId)
                        ?: throw NotFoundException("Compensation not found")

                val gate =
                    checkWorkAndPayingDaysEditableInTransaction(
                        callerId,
                        before.workBranchDayId,
                        before.payingBranchDayId,
                        edit.reason,
                    )
                assertPayingMatchesWork(gate.workDay, gate.payingDay)

                val after =
                    CompensationRepository.updateInTransaction(compensationId, edit.amount, edit.note, expectedVersion)

                CompensationAudit.updated(
                    AuditContext(callerId, gate.payingDay.branchId, gate.isRemitted, edit.reason),
                    before,
                    after,
                )
                after
            }.also {
                logger.info { "[UPDATE-COMPENSATION] Compensation ${compensationId.toString().maskUUID()} updated" }
            }
        }
    }
}

/**
 * Compensation audit vocabulary (ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object CompensationAudit {
    fun inserted(
        context: AuditContext,
        compensation: Compensation,
    ) = AuditLog.recordInsert(
        context = context,
        row = AuditedRow(CompensationTable.tableName, compensation.id),
        fields = CompensationTable.auditFields(compensation),
    )

    fun updated(
        context: AuditContext,
        before: Compensation,
        after: Compensation,
    ) = AuditLog.recordUpdate(
        context = context,
        row = AuditedRow(CompensationTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = CompensationTable::auditFields,
    )
}

data class NewCompensation(
    val id: UUID,
    val workBranchDayId: UUID,
    val payingBranchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val note: String?,
    val reason: String? = null,
)

data class CompensationEdit(
    val amount: BigDecimal,
    val note: String?,
    val reason: String? = null,
)
