package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CompensationCreateParams
import com.companyb.companyapp.repository.CompensationRepository
import com.companyb.companyapp.repository.model.Compensation
import io.github.oshai.kotlinlogging.KotlinLogging
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
        BranchDayRepository.findById(workBranchDayId)
            ?: throw NotFoundException("Work branch day not found")

        BranchDayService.checkBranchDayEditable(callerId, payingBranchDayId)

        val existingByKey = CompensationRepository.findByUserAndPayingDay(userId, payingBranchDayId)
        if (existingByKey != null && existingByKey.id != id) {
            throw ConflictException("Compensation already exists for this user and paying branch day")
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
        val compensation =
            CompensationRepository.findById(compensationId)
                ?: throw NotFoundException("Compensation not found")

        BranchDayService.checkBranchDayEditable(callerId, compensation.payingBranchDayId)

        return try {
            CompensationRepository.update(compensationId, amount, note, expectedVersion, callerId)
        } catch (e: IllegalStateException) {
            when (e.message) {
                "version_mismatch" -> throw ConflictException("Compensation version mismatch")
                else -> throw e
            }
        }
    }
}
