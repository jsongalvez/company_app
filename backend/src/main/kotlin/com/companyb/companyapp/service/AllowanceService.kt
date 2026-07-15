package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AllowanceRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.model.Allowance
import com.companyb.companyapp.repository.model.CapabilityContextType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.util.UUID

object AllowanceService {
    private val logger = KotlinLogging.logger {}

    private const val ASSIGN_COMPENSATION = "ASSIGN_COMPENSATION"

    @Suppress("ThrowsCount", "ReturnCount")
    fun create(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
    ): Allowance {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = ASSIGN_COMPENSATION,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[CREATE-ALLOWANCE] User $callerId lacks $ASSIGN_COMPENSATION capability" }
            throw ForbiddenResponse("ASSIGN_COMPENSATION capability required")
        }

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

    fun findByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<Allowance> {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = ASSIGN_COMPENSATION,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[FIND-ALLOWANCES] User $callerId lacks $ASSIGN_COMPENSATION capability" }
            throw ForbiddenResponse("ASSIGN_COMPENSATION capability required")
        }

        return AllowanceRepository.findByBranchDayId(branchDayId)
    }
}
