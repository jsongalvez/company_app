package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import org.jetbrains.exposed.v1.core.and
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Remittance feature result records. Plain value objects owned by the feature commands and
 * returned across the public service surface; the internal stores map persistence rows onto them.
 * The Exposed `*Table` definitions stay behind the store seam colocated in the feature package.
 */
data class Remittance(
    val id: UUID,
    val type: RemittanceType,
    val status: RemittanceStatus,
    val branchId: UUID,
    val method: RemittanceMethod,
    val submittedDate: LocalDate,
    val submittedAt: OffsetDateTime?,
    val submittedBy: UUID,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val createdAt: OffsetDateTime,
    val version: Int,
)

data class RemittanceLine(
    val id: UUID,
    val remittanceId: UUID,
    val type: RemittanceLineType,
    val sessionId: UUID?,
    val productSaleId: UUID?,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val deletedBy: UUID?,
    val deletedAt: OffsetDateTime?,
    val amount: BigDecimal,
)

data class RemittanceDayBreakdown(
    val id: UUID,
    val remittanceId: UUID,
    val branchDayId: UUID,
)

data class RemittanceFinancialSnapshot(
    val remittanceId: UUID,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
    val snapshottedAt: OffsetDateTime,
)

data class RemittanceFinancialSnapshotCreateParams(
    val remittanceId: UUID,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)

data class CreateDraftParams(
    val id: UUID,
    val type: RemittanceType,
    val branchId: UUID,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val submittedDate: LocalDate,
    val submittedBy: UUID,
)

data class RemittanceCreateResult(
    val remittance: Remittance,
    val created: Boolean,
)

data class UpdateHeaderParams(
    val remittanceId: UUID,
    val expectedVersion: Int,
    val type: RemittanceType,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
)

data class RemittanceSubmissionResult(
    val remittance: Remittance,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)

data class NewRemittance(
    val id: UUID,
    val type: RemittanceType,
    val branchId: UUID,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
)

data class RemittanceHeader(
    val type: RemittanceType,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
)
