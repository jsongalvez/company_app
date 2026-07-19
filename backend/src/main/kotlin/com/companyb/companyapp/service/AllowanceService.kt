package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AllowanceRepository
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.AllowanceCreateParams
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.UUID

object AllowanceService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "ReturnCount")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
    ): Allowance {
        BranchDayService.checkBranchDayEditable(callerId, branchDayId)

        val result =
            AllowanceRepository.create(
                AllowanceCreateParams(
                    id = id,
                    branchDayId = branchDayId,
                    userId = userId,
                    amount = amount,
                    assignedBy = callerId,
                ),
                auditFn = { created ->
                    AuditLogRepository.recordInsert(
                        tableName = AllowanceTable.tableName,
                        recordId = created.id,
                        changedBy = callerId,
                        fields = AllowanceTable.auditFields(created),
                    )
                },
            )
        logger.info { "[CREATE-ALLOWANCE] Created allowance ${result.allowance.id} created=${result.created}" }
        return result.allowance
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> = AllowanceRepository.findByBranchDayId(branchDayId)
}
