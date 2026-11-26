package com.companyb.companyapp.remittance

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.exception.NotFoundException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal object RemittanceReadService {
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

    fun listRemittances(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> {
        BranchService.findById(branchId)
        return RemittanceRepository.findByBranchId(branchId, status)
    }

    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> {
        BranchService.findById(branchId)
        return RemittanceRepository.findSessionsInRange(branchId, from, to)
    }

    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> {
        BranchService.findById(branchId)
        return RemittanceRepository.findProductSalesInRange(branchId, from, to)
    }

    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceDayPickerEntry> {
        BranchService.findById(branchId)
        val today = BranchDayService.currentOperationalDate()
        return RemittanceRepository
            .findBranchDaysInRange(branchId, from, to)
            .map { day ->
                RemittanceDayPickerEntry(
                    id = day.id,
                    date = day.date,
                    status = BranchDayService.evaluateStatus(day.status, day.date, today),
                )
            }
    }

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
        val currentNet = RemittancePolicy.netOf(snapshot.grossIncome, currentCompensation, currentExpenses)

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

/** One pickable branch day for the remittance day-range picker — no Branch Day record crosses the seam. */
data class RemittanceDayPickerEntry(
    val id: UUID,
    val date: LocalDate,
    val status: DayStatus,
)
