package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CompensationCreateParams
import com.companyb.companyapp.repository.CompensationRepository
import com.companyb.companyapp.repository.CompensationWithUser
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.service.branchday.BranchDayService
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

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
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
        BranchDayService.requireBranchDayExists(workBranchDayId)

        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, payingBranchDayId, reason)

        return transaction {
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
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
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

    @Suppress("ThrowsCount", "LongParameterList")
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

            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditable(
                    callerId,
                    before.payingBranchDayId,
                    reason,
                )

            val after =
                CompensationRepository.updateInTransaction(compensationId, amount, note, expectedVersion)

            CompensationAudit.updated(
                AuditContext(callerId, branchDay.branchId, isRemitted, reason),
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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
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
