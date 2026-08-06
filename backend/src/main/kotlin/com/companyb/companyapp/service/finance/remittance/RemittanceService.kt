package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object RemittanceService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun submit(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
    ): RemittanceSubmissionResult {
        val result =
            RemittanceRepository.submit(
                remittanceId = remittanceId,
                expectedVersion = expectedVersion,
                callerId = callerId,
                auditFn = { ctx ->
                    AuditLogRepository.recordUpdate(
                        tableName = RemittanceTable.tableName,
                        recordId = ctx.remittanceAfter.id,
                        before = ctx.remittanceBefore,
                        after = ctx.remittanceAfter,
                        changedBy = callerId,
                        branchId = ctx.remittanceAfter.branchId,
                        auditFields = RemittanceTable::auditFields,
                    )
                    ctx.branchDayPairs.forEach { (before, after) ->
                        AuditLogRepository.recordUpdate(
                            tableName = BranchDayTable.tableName,
                            recordId = after.id,
                            before = before,
                            after = after,
                            changedBy = callerId,
                            branchId = before.branchId,
                            auditFields = BranchDayTable::auditFields,
                        )
                    }
                },
            ) ?: throw NotFoundException("Remittance not found")

        logger.info {
            "[SUBMIT-REMITTANCE] Remittance $remittanceId submitted. Gross=${result.grossIncome} " +
                "Net=${result.netIncome}"
        }
        return result
    }

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList")
    fun createDraft(
        callerId: UUID,
        id: UUID,
        type: RemittanceType,
        branchId: UUID,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
    ): Remittance {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")

        val today = LocalDate.now(BranchDayService.manilaZone)
        val result =
            RemittanceRepository.createDraft(
                CreateDraftParams(
                    id = id,
                    type = type,
                    branchId = branchId,
                    method = method,
                    dateRangeStart = dateRangeStart,
                    dateRangeEnd = dateRangeEnd,
                    submittedDate = today,
                    submittedBy = callerId,
                ),
            ) { remittance ->
                AuditLogRepository.recordInsert(
                    tableName = RemittanceTable.tableName,
                    recordId = remittance.id,
                    changedBy = callerId,
                    branchId = branchId,
                    fields = RemittanceTable.auditFields(remittance),
                )
            }
        logger.info { "[CREATE-REMITTANCE-DRAFT] Remittance ${result.remittance.id} created=${result.created}" }
        return result.remittance
    }

    @Suppress("ThrowsCount", "ReturnCount", "LongParameterList", "MaxLineLength")
    fun addLine(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        amount: BigDecimal,
    ): RemittanceLine {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only add lines to DRAFT remittances")
        }

        val line =
            RemittanceLineRepository.addLine(
                AddLineParams(
                    id = id,
                    remittanceId = remittanceId,
                    type = type,
                    sessionId = sessionId,
                    productSaleId = productSaleId,
                    amount = amount,
                    createdBy = callerId,
                    expectedVersion = remittance.version,
                ),
            ) { line ->
                AuditLogRepository.recordInsert(
                    tableName = RemittanceLineTable.tableName,
                    recordId = line.id,
                    changedBy = callerId,
                    branchId = remittance.branchId,
                    fields = RemittanceLineTable.auditFields(line),
                )
            }

        logger.info { "[ADD-REMITTANCE-LINE] Line ${line.id} added to remittance $remittanceId" }
        return line
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun removeLine(
        callerId: UUID,
        remittanceId: UUID,
        lineId: UUID,
    ): RemittanceLine {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only delete lines from DRAFT remittances")
        }

        val line =
            RemittanceLineRepository.softDeleteLine(
                lineId,
                remittanceId,
                callerId,
                remittance.version,
            ) { before, after ->
                AuditLogRepository.recordUpdate(
                    tableName = RemittanceLineTable.tableName,
                    recordId = after.id,
                    before = before,
                    after = after,
                    changedBy = callerId,
                    branchId = remittance.branchId,
                    auditFields = RemittanceLineTable::auditFields,
                )
            }
                ?: throw NotFoundException("Remittance line not found")

        logger.info { "[DELETE-REMITTANCE-LINE] Line $lineId deleted from remittance $remittanceId" }
        return line
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun addDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        id: UUID,
        branchDayId: UUID,
    ): RemittanceDayBreakdown {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only add day breakdowns to DRAFT remittances")
        }

        BranchDayService.requireBranchDayExists(branchDayId)

        val breakdown =
            RemittanceDayBreakdownRepository.addDayBreakdown(
                id = id,
                remittanceId = remittanceId,
                branchDayId = branchDayId,
            ) { breakdown ->
                AuditLogRepository.recordInsert(
                    tableName = RemittanceDayBreakdownTable.tableName,
                    recordId = breakdown.id,
                    changedBy = callerId,
                    branchId = remittance.branchId,
                    fields = RemittanceDayBreakdownTable.auditFields(breakdown),
                )
            }

        logger.info { "[ADD-REMITTANCE-BREAKDOWN] Day breakdown ${breakdown.id} added to remittance $remittanceId" }
        return breakdown
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun getRemittance(remittanceId: UUID): RemittanceDetail {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        val lines = RemittanceLineRepository.findByRemittanceId(remittanceId)
        val totalAmount = RemittanceLineRepository.sumAmountsByRemittanceId(remittanceId)
        val dayBreakdowns = RemittanceDayBreakdownRepository.findByRemittanceId(remittanceId)

        return RemittanceDetail(
            remittance = remittance,
            lines = lines,
            totalAmount = totalAmount,
            dayBreakdowns = dayBreakdowns,
        )
    }

    fun getBranchIdForRemittance(remittanceId: UUID): UUID {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")
        return remittance.branchId
    }
}

data class RemittanceDetail(
    val remittance: Remittance,
    val lines: List<RemittanceLine>,
    val totalAmount: BigDecimal,
    val dayBreakdowns: List<RemittanceDayBreakdown>,
)
