package com.companyb.companyapp.finance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Allowance feature commands (#323, ADR-0024). The mutating command owns exactly one business
 * transaction: persistence runs on it via `AllowanceRepository.createInTransaction`, and the
 * audit row is inserted directly into it — so domain write + audit commit atomically or not at all.
 */
object AllowanceService {
    private val logger = KotlinLogging.logger {}

    // #596: 6-param creation command stays whole per #535; bundle only on a real ownership decision.
    @Suppress("LongParameterList") // #596
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        reason: String? = null,
    ): Allowance {
        if (!AccountReads.userExists(userId)) {
            throw NotFoundException("User not found")
        }
        return transaction {
            // In-tx replay classification before the day gate (mirrors #509/#510/#511):
            // a same-id row already committed acks without gating so retries landing
            // after a day transition still ack; ownership matches createInTransaction below.
            AllowanceRepository.findByIdInTransaction(id)?.let { existing ->
                if (existing.branchDayId != branchDayId) {
                    throw NotFoundException("Allowance not found for this branch day")
                }
                if (existing.userId != userId ||
                    existing.assignedBy != callerId ||
                    existing.amount.compareTo(amount) != 0
                ) {
                    throw ConflictException("Allowance id already belongs to another create request")
                }
                return@transaction existing
            }
            // Locked day read: serializes this create with remittance's REMITTED transition.
            val (branchDay, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDayId, reason)

            val result =
                AllowanceRepository.createInTransaction(
                    AllowanceCreateParams(
                        id = id,
                        branchDayId = branchDayId,
                        userId = userId,
                        amount = amount,
                        assignedBy = callerId,
                    ),
                )
            if (result.created) {
                AllowanceAudit.inserted(
                    AuditContext(callerId, branchDay.branchId, isRemitted, reason),
                    result.allowance,
                )
            }
            result.allowance
        }.also { created ->
            logger.info { "[CREATE-ALLOWANCE] Allowance ${created.id.toString().maskUUID()} created" }
        }
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> {
        BranchDayService.requireBranchDayExists(branchDayId)

        return AllowanceRepository.findByBranchDayId(branchDayId)
    }
}

/**
 * Allowance audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object AllowanceAudit {
    fun inserted(
        context: AuditContext,
        allowance: Allowance,
    ) = AuditLog.recordInsert(
        tableName = AllowanceTable.tableName,
        recordId = allowance.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = AllowanceTable.auditFields(allowance),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )
}
