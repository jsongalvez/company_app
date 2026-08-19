package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("TooManyFunctions")
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

    @Suppress("ThrowsCount")
    fun undo(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
        reason: String,
    ): Remittance =
        undoAt(
            callerId = callerId,
            remittanceId = remittanceId,
            expectedVersion = expectedVersion,
            reason = reason,
        )

    @Suppress("ThrowsCount", "LongMethod")
    internal fun undoAt(
        callerId: UUID,
        remittanceId: UUID,
        expectedVersion: Int,
        reason: String,
        now: OffsetDateTime? = null,
    ): Remittance {
        val remittance =
            RemittanceRepository.undo(
                UndoParams(
                    remittanceId = remittanceId,
                    expectedVersion = expectedVersion,
                    reason = reason,
                    now = now,
                ),
            ) { ctx ->
                AuditLogRepository.recordUpdate(
                    tableName = RemittanceTable.tableName,
                    recordId = ctx.remittanceAfter.id,
                    before = ctx.remittanceBefore,
                    after = ctx.remittanceAfter,
                    changedBy = callerId,
                    branchId = ctx.remittanceAfter.branchId,
                    reason = reason,
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
                        reason = reason,
                        auditFields = BranchDayTable::auditFields,
                    )
                }
                ctx.snapshotBefore?.let { snapshot ->
                    AuditLogRepository.recordDelete(
                        tableName = RemittanceFinancialSnapshotTable.tableName,
                        recordId = snapshot.remittanceId,
                        before = snapshot,
                        changedBy = callerId,
                        branchId = ctx.remittanceAfter.branchId,
                        reason = reason,
                        auditFields = RemittanceFinancialSnapshotTable::auditFields,
                    )
                }
            } ?: throw NotFoundException("Remittance not found")

        logger.info {
            "[UNDO-REMITTANCE] Remittance ${remittanceId.toString().maskUUID()} undone"
        }
        return remittance
    }

    @Suppress("ThrowsCount", "LongParameterList")
    fun updateHeader(
        callerId: UUID,
        remittanceId: UUID,
        type: RemittanceType,
        method: RemittanceMethod,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
        expectedVersion: Int,
    ): Remittance {
        val remittance =
            RemittanceRepository.updateHeader(
                UpdateHeaderParams(
                    remittanceId = remittanceId,
                    expectedVersion = expectedVersion,
                    type = type,
                    method = method,
                    dateRangeStart = dateRangeStart,
                    dateRangeEnd = dateRangeEnd,
                ),
            ) { before, after ->
                AuditLogRepository.recordUpdate(
                    tableName = RemittanceTable.tableName,
                    recordId = after.id,
                    before = before,
                    after = after,
                    changedBy = callerId,
                    branchId = after.branchId,
                    auditFields = RemittanceTable::auditFields,
                )
            } ?: throw NotFoundException("Remittance not found")

        logger.info {
            "[UPDATE-REMITTANCE-HEADER] Remittance ${remittanceId.toString().maskUUID()} header updated"
        }
        return remittance
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

        requireSourceBelongsToBranch(type, sessionId, productSaleId, remittance.branchId)

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

    @Suppress("ThrowsCount")
    private fun requireSourceBelongsToBranch(
        type: RemittanceLineType,
        sessionId: UUID?,
        productSaleId: UUID?,
        branchId: UUID,
    ) {
        val sourceBranchDayId =
            when (type) {
                RemittanceLineType.SESSION -> {
                    val sourceId = sessionId ?: throw ValidationException("sessionId is required for SESSION line type")
                    SessionRepository.findById(sourceId)?.branchDayId
                        ?: throw NotFoundException("Session not found")
                }

                RemittanceLineType.PRODUCT_SALE -> {
                    val sourceId =
                        productSaleId
                            ?: throw ValidationException("productSaleId is required for PRODUCT_SALE line type")
                    ProductSaleRepository.findById(sourceId)?.branchDayId
                        ?: throw NotFoundException("Product sale not found")
                }
            }
        if (BranchDayService.requireBranchDayExists(sourceBranchDayId).branchId != branchId) {
            throw NotFoundException("Source does not belong to remittance branch")
        }
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

        BranchDayService.requireBranchDayForBranch(branchDayId, remittance.branchId)

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
    fun removeDayBreakdown(
        callerId: UUID,
        remittanceId: UUID,
        breakdownId: UUID,
    ): RemittanceDayBreakdown {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        if (remittance.status != RemittanceStatus.DRAFT) {
            throw ValidationException("Can only remove day breakdowns from DRAFT remittances")
        }

        val breakdown =
            RemittanceDayBreakdownRepository.deleteDayBreakdown(
                breakdownId = breakdownId,
                remittanceId = remittanceId,
            ) { before ->
                AuditLogRepository.recordDelete(
                    tableName = RemittanceDayBreakdownTable.tableName,
                    recordId = before.id,
                    before = before,
                    changedBy = callerId,
                    branchId = remittance.branchId,
                    auditFields = RemittanceDayBreakdownTable::auditFields,
                )
            }
                ?: throw NotFoundException("Day breakdown not found")

        logger.info { "[DELETE-REMITTANCE-BREAKDOWN] Day breakdown $breakdownId removed from remittance $remittanceId" }
        return breakdown
    }

    @Suppress("ReturnCount")
    fun getRemittance(remittanceId: UUID): RemittanceDetail {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")

        val lines = RemittanceLineRepository.findByRemittanceId(remittanceId)
        val totalAmount = RemittanceLineRepository.sumAmountsByRemittanceId(remittanceId)
        val dayBreakdowns = RemittanceDayBreakdownRepository.findByRemittanceId(remittanceId)
        val snapshot = RemittanceFinancialSnapshotRepository.findByRemittanceId(remittanceId)

        return RemittanceDetail(
            remittance = remittance,
            lines = lines,
            totalAmount = totalAmount,
            dayBreakdowns = dayBreakdowns,
            snapshot = snapshot,
        )
    }

    fun getBranchIdForRemittance(remittanceId: UUID): UUID {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundException("Remittance not found")
        return remittance.branchId
    }

    @Suppress("ThrowsCount")
    fun listRemittances(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")
        return RemittanceRepository.findByBranchId(branchId, status)
    }

    @Suppress("ThrowsCount")
    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")
        return RemittanceRepository.findSessionsInRange(branchId, from, to)
    }

    @Suppress("ThrowsCount")
    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")
        return RemittanceRepository.findProductSalesInRange(branchId, from, to)
    }

    @Suppress("ThrowsCount")
    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<BranchDay> {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")
        val today = LocalDate.now(BranchDayService.manilaZone)
        return RemittanceRepository
            .findBranchDaysInRange(branchId, from, to)
            .map { day -> day.copy(status = BranchDayService.evaluateStatus(day.status, day.date, today)) }
    }

    @Suppress("ThrowsCount", "ReturnCount")
    fun getDrift(remittanceId: UUID): RemittanceDrift {
        RemittanceRepository.findById(remittanceId)
            ?: throw NotFoundException("Remittance not found")

        val snapshot =
            RemittanceFinancialSnapshotRepository.findByRemittanceId(remittanceId)
                ?: throw NotFoundException("No financial snapshot for this remittance")

        val breakdownIds =
            RemittanceDayBreakdownRepository
                .findByRemittanceId(remittanceId)
                .map { it.branchDayId }
        val currentCompensation = RemittanceRepository.calculateCompensationSum(breakdownIds)
        val currentExpenses = RemittanceRepository.calculateExpenseSum(breakdownIds)
        val currentNet = RemittanceRepository.netOf(snapshot.grossIncome, currentCompensation, currentExpenses)

        return RemittanceDrift(
            frozen = snapshot,
            currentCompensation = currentCompensation,
            currentExpenses = currentExpenses,
            currentNet = currentNet,
        )
    }
}

data class RemittanceDetail(
    val remittance: Remittance,
    val lines: List<RemittanceLine>,
    val totalAmount: BigDecimal,
    val dayBreakdowns: List<RemittanceDayBreakdown>,
    val snapshot: RemittanceFinancialSnapshot? = null,
)

data class RemittanceDrift(
    val frozen: RemittanceFinancialSnapshot,
    val currentCompensation: BigDecimal,
    val currentExpenses: BigDecimal,
    val currentNet: BigDecimal,
)
