package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CompensationCreateParams
import com.companyb.companyapp.repository.CompensationRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.Compensation
import com.companyb.companyapp.repository.model.CompensationTable
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
            ) { compensation ->
                AuditLogRepository.record(
                    tableName = CompensationTable.tableName,
                    recordId = compensation.id,
                    action = AuditAction.INSERT,
                    changedBy = callerId,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to compensation.id.toString(),
                            "workBranchDayId" to compensation.workBranchDayId.toString(),
                            "payingBranchDayId" to compensation.payingBranchDayId.toString(),
                            "userId" to compensation.userId.toString(),
                            "amount" to compensation.amount.toPlainString(),
                        ),
                )
            }
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
        val before =
            CompensationRepository.findById(compensationId)
                ?: throw NotFoundException("Compensation not found")

        BranchDayService.checkBranchDayEditable(callerId, before.payingBranchDayId)

        return try {
            CompensationRepository.update(compensationId, amount, note, expectedVersion) { after ->
                AuditLogRepository.record(
                    tableName = CompensationTable.tableName,
                    recordId = compensationId,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "amount" to before.amount.toPlainString(),
                            "note" to (before.note ?: "null"),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "amount" to after.amount.toPlainString(),
                            "note" to (after.note ?: "null"),
                        ),
                )
            }
        } catch (e: IllegalStateException) {
            when (e.message) {
                "version_mismatch" -> throw ConflictException("Compensation version mismatch")
                else -> throw e
            }
        }
    }
}
