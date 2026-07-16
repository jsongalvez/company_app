package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CompensationCreateParams
import com.companyb.companyapp.repository.CompensationRepository
import com.companyb.companyapp.repository.model.Compensation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.util.UUID

object CompensationService {
    private val logger = KotlinLogging.logger {}

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
