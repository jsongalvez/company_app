package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse

internal fun Remittance.toResponse(): RemittanceResponse =
    RemittanceResponse(
        id = id.toString(),
        type = type,
        status = status,
        branchId = branchId.toString(),
        method = method,
        submittedDate = submittedDate.toString(),
        submittedAt = submittedAt?.toString(),
        submittedBy = submittedBy.toString(),
        dateRangeStart = dateRangeStart.toString(),
        dateRangeEnd = dateRangeEnd.toString(),
        createdAt = createdAt.toString(),
        version = version,
    )

internal fun RemittanceWithNet.toListResponse(): RemittanceResponse =
    remittance.toResponse().copy(netIncome = netIncome?.toPlainString())

internal fun RemittanceLine.toResponse(): RemittanceLineResponse =
    RemittanceLineResponse(
        id = id.toString(),
        remittanceId = remittanceId.toString(),
        type = type,
        sessionId = sessionId?.toString(),
        productSaleId = productSaleId?.toString(),
        createdBy = createdBy.toString(),
        createdAt = createdAt.toString(),
        deletedBy = deletedBy?.toString(),
        deletedAt = deletedAt?.toString(),
        amount = amount.toPlainString(),
    )

internal fun RemittanceDayBreakdown.toResponse(): RemittanceDayBreakdownResponse =
    RemittanceDayBreakdownResponse(
        id = id.toString(),
        remittanceId = remittanceId.toString(),
        branchDayId = branchDayId.toString(),
    )

internal fun RemittanceDetail.toResponse(): RemittanceDetailResponse =
    RemittanceDetailResponse(
        id = remittance.id.toString(),
        type = remittance.type,
        status = remittance.status,
        branchId = remittance.branchId.toString(),
        method = remittance.method,
        submittedDate = remittance.submittedDate.toString(),
        submittedAt = remittance.submittedAt?.toString(),
        submittedBy = remittance.submittedBy.toString(),
        dateRangeStart = remittance.dateRangeStart.toString(),
        dateRangeEnd = remittance.dateRangeEnd.toString(),
        createdAt = remittance.createdAt.toString(),
        version = remittance.version,
        lines = lines.map { it.toResponse() },
        totalAmount = totalAmount.toPlainString(),
        dayBreakdowns = dayBreakdowns.map { it.toResponse() },
        snapshot = snapshot?.toResponse(),
    )

private fun RemittanceFinancialSnapshot.toResponse(): RemittanceFinancialSnapshotResponse =
    RemittanceFinancialSnapshotResponse(
        remittanceId = remittanceId.toString(),
        grossIncome = grossIncome.toPlainString(),
        totalCompensation = totalCompensation.toPlainString(),
        totalExpenses = totalExpenses.toPlainString(),
        netIncome = netIncome.toPlainString(),
        snapshottedAt = snapshottedAt.toString(),
    )

internal fun RemittanceDrift.toResponse(): RemittanceDriftResponse =
    RemittanceDriftResponse(
        frozen = frozen.toResponse(),
        currentCompensation = currentCompensation.toPlainString(),
        currentExpenses = currentExpenses.toPlainString(),
        currentNet = currentNet.toPlainString(),
    )

internal fun RemittanceSubmissionResult.toSubmitResponse(): RemittanceSubmitResponse =
    RemittanceSubmitResponse(
        id = remittance.id.toString(),
        type = remittance.type,
        status = remittance.status,
        branchId = remittance.branchId.toString(),
        method = remittance.method,
        submittedDate = remittance.submittedDate.toString(),
        submittedAt = remittance.submittedAt?.toString(),
        submittedBy = remittance.submittedBy.toString(),
        dateRangeStart = remittance.dateRangeStart.toString(),
        dateRangeEnd = remittance.dateRangeEnd.toString(),
        createdAt = remittance.createdAt.toString(),
        version = remittance.version,
        grossIncome = grossIncome.toPlainString(),
        totalCompensation = totalCompensation.toPlainString(),
        totalExpenses = totalExpenses.toPlainString(),
        netIncome = netIncome.toPlainString(),
    )
