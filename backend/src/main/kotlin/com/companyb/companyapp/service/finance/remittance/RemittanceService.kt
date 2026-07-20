package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.AuditValues
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.DayStatus
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
            SubmissionEngine.submit(
                remittanceId = remittanceId,
                expectedVersion = expectedVersion,
                callerId = callerId,
                auditFn = { ctx ->
                    AuditLogRepository.record(
                        tableName = RemittanceTable.tableName,
                        recordId = ctx.remittanceId,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        oldValue = AuditLogRepository.jsonField("status", RemittanceStatus.DRAFT.name),
                        newValue = AuditLogRepository.jsonField("status", RemittanceStatus.SUBMITTED.name),
                    )
                    for (bdId in ctx.breakdownIds) {
                        AuditLogRepository.record(
                            tableName = BranchDayTable.tableName,
                            recordId = bdId,
                            action = AuditAction.UPDATE,
                            changedBy = callerId,
                            oldValue = AuditLogRepository.jsonField("status", DayStatus.OPEN.name),
                            newValue = AuditLogRepository.jsonField("status", DayStatus.REMITTED.name),
                        )
                    }
                },
            )

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
            try {
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
                        fields = RemittanceLineTable.auditFields(line),
                    )
                }
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictException("Remittance version mismatch")
                }
                throw e
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
            try {
                RemittanceLineRepository.softDeleteLine(lineId, remittanceId, callerId, remittance.version) { line ->
                    AuditLogRepository.record(
                        tableName = RemittanceLineTable.tableName,
                        recordId = line.id,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        oldValue = AuditLogRepository.jsonField("deletedAt", AuditValues.NULL),
                        newValue = AuditLogRepository.jsonField("deletedAt", AuditValues.NOW_FN),
                    )
                }
                    ?: throw NotFoundException("Remittance line not found")
            } catch (e: IllegalStateException) {
                if (e.message == "version_mismatch") {
                    throw ConflictException("Remittance version mismatch")
                }
                throw e
            }

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
