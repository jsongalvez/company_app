package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AllowanceRepository
import com.companyb.companyapp.repository.model.Allowance
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
                id = id,
                branchDayId = branchDayId,
                userId = userId,
                amount = amount,
                assignedBy = callerId,
            )
        logger.info { "[CREATE-ALLOWANCE] Created allowance ${result.allowance.id} created=${result.created}" }
        return result.allowance
    }

    fun findByBranchDayId(branchDayId: UUID): List<Allowance> = AllowanceRepository.findByBranchDayId(branchDayId)
}
