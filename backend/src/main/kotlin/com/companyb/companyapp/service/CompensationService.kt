package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CompensationRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Compensation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.util.UUID

object CompensationService {
    private val logger = KotlinLogging.logger {}

    private const val ASSIGN_COMPENSATION = "ASSIGN_COMPENSATION"

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun create(
        callerId: UUID,
        id: UUID,
        workBranchDayId: UUID,
        payingBranchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        note: String?,
    ): Compensation {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = ASSIGN_COMPENSATION,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[CREATE-COMPENSATION] User $callerId lacks $ASSIGN_COMPENSATION capability" }
            throw ForbiddenResponse("ASSIGN_COMPENSATION capability required")
        }

        if (amount < BigDecimal.ZERO) {
            throw BadRequestResponse("Amount must be non-negative")
        }

        BranchDayRepository.findById(workBranchDayId)
            ?: throw NotFoundResponse("Work branch day not found")
        BranchDayRepository.findById(payingBranchDayId)
            ?: throw NotFoundResponse("Paying branch day not found")

        BranchDayService.assertEditable(payingBranchDayId, callerId)

        val existingByKey = CompensationRepository.findByUserAndPayingDay(userId, payingBranchDayId)
        if (existingByKey != null && existingByKey.id != id) {
            throw ConflictResponse("Compensation already exists for this user and paying branch day")
        }

        val result =
            CompensationRepository.create(
                id,
                workBranchDayId,
                payingBranchDayId,
                userId,
                amount,
                callerId,
                note,
            )
        logger.info { "[CREATE-COMPENSATION] Created compensation ${result.compensation.id} created=${result.created}" }
        return result.compensation
    }

    @Suppress("ThrowsCount")
    fun update(
        callerId: UUID,
        compensationId: UUID,
        amount: BigDecimal,
        note: String?,
        expectedVersion: Int,
    ): Compensation {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = ASSIGN_COMPENSATION,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[UPDATE-COMPENSATION] User $callerId lacks $ASSIGN_COMPENSATION capability" }
            throw ForbiddenResponse("ASSIGN_COMPENSATION capability required")
        }

        if (amount < BigDecimal.ZERO) {
            throw BadRequestResponse("Amount must be non-negative")
        }

        val compensation =
            CompensationRepository.findById(compensationId)
                ?: throw NotFoundResponse("Compensation not found")

        BranchDayService.assertEditable(compensation.payingBranchDayId, callerId)

        return try {
            CompensationRepository.update(compensationId, amount, note, expectedVersion, callerId)
        } catch (e: IllegalStateException) {
            when (e.message) {
                "version_mismatch" -> throw ConflictResponse("Compensation version mismatch")
                else -> throw e
            }
        }
    }
}
