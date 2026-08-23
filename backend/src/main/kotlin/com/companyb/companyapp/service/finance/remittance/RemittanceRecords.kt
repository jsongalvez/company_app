package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Remittance feature result records (#343). Plain value objects owned by the feature commands and
 * returned across the public service surface; the internal stores map persistence rows onto them.
 * The Exposed `*Table` definitions stay behind the store seam in `repository/model`.
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
