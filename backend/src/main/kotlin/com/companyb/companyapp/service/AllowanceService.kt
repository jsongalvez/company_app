package com.companyb.companyapp.service

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AllowanceRepository
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.AllowanceCreateParams
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.service.branchday.BranchDayService
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

    @Suppress("ThrowsCount", "LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        reason: String? = null,
    ): Allowance {
        val (branchDay, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

        return transaction {
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
                    changedBy = callerId,
                    branchId = branchDay.branchId,
                    allowance = result.allowance,
                    isFlagged = isRemitted,
                    reason = reason,
                )
            }
            result.allowance
        }.also { created ->
            logger.info { "[CREATE-ALLOWANCE] Allowance ${created.id.toString().maskUUID()} created" }
        }
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> = AllowanceRepository.findByBranchDayId(branchDayId)
}

/**
 * Allowance audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object AllowanceAudit {
    fun inserted(
        changedBy: UUID,
        branchId: UUID,
        allowance: Allowance,
        isFlagged: Boolean,
        reason: String?,
    ) = AuditLogRepository.recordInsert(
        tableName = AllowanceTable.tableName,
        recordId = allowance.id,
        changedBy = changedBy,
        branchId = branchId,
        fields = AllowanceTable.auditFields(allowance),
        isFlagged = isFlagged,
        reason = reason,
    )
}
