package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AllowanceRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.model.Allowance
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
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
        if (amount < BigDecimal.ZERO) {
            throw BadRequestResponse("Amount must be non-negative")
        }

        BranchDayRepository.findById(branchDayId)
            ?: throw NotFoundResponse("Branch day not found")

        BranchDayService.assertEditable(branchDayId, callerId)

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

    @Suppress("UnusedParameter")
    fun findByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<Allowance> = AllowanceRepository.findByBranchDayId(branchDayId)
}
