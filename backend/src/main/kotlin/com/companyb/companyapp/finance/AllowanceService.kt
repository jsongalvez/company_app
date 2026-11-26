package com.companyb.companyapp.finance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.DayWrite
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.AccountReads
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.observability.traced
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Allowance feature commands (ADR-0024). The mutating command owns exactly one business
 * transaction: persistence runs on it via `AllowanceRepository.createInTransaction`, and the
 * audit row is inserted directly into it — so domain write + audit commit atomically or not at all.
 */
object AllowanceService {
    private val logger = KotlinLogging.logger {}

    fun create(
        callerId: UUID,
        new: NewAllowance,
    ): Allowance {
        if (!AccountReads.userExists(new.userId)) {
            throw NotFoundException("User not found")
        }
        return traced("AllowanceService.create") {
            validateMoneyAmount(new.amount, "amount")
            return@traced transaction {
                // In-tx replay classification before the day gate:
                // a same-id row already committed acks without gating so retries landing
                // after a day transition still ack; ownership matches createInTransaction below.
                AllowanceRepository.findByIdInTransaction(new.id)?.let { existing ->
                    if (existing.branchDayId != new.branchDayId) {
                        throw NotFoundException("Allowance not found for this branch day")
                    }
                    if (existing.userId != new.userId ||
                        existing.assignedBy != callerId ||
                        existing.amount.compareTo(new.amount) != 0
                    ) {
                        throw ConflictException("Allowance id already belongs to another create request")
                    }
                    return@transaction existing
                }
                // Locked day read: serializes this create with remittance's REMITTED transition.
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditableInTransaction(
                        callerId,
                        new.branchDayId,
                        new.reason,
                        DayWrite.PAY,
                    )

                val result =
                    AllowanceRepository.createInTransaction(
                        AllowanceCreateParams(
                            id = new.id,
                            branchDayId = new.branchDayId,
                            userId = new.userId,
                            amount = new.amount,
                            assignedBy = callerId,
                        ),
                    )
                if (result.created) {
                    AllowanceAudit.inserted(
                        AuditContext(callerId, branchDay.branchId, isRemitted, new.reason),
                        result.allowance,
                    )
                }
                result.allowance
            }.also { created ->
                logger.info { "[CREATE-ALLOWANCE] Allowance ${created.id.toString().maskUUID()} created" }
            }
        }
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> {
        BranchDayService.requireBranchDayExists(branchDayId)

        return AllowanceRepository.findByBranchDayId(branchDayId)
    }
}

/**
 * Allowance audit vocabulary (ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object AllowanceAudit {
    fun inserted(
        context: AuditContext,
        allowance: Allowance,
    ) = AuditLog.recordInsert(
        context = context,
        row = AuditedRow(AllowanceTable.tableName, allowance.id),
        fields = AllowanceTable.auditFields(allowance),
    )
}

data class NewAllowance(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val reason: String? = null,
)
