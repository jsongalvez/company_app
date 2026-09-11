package com.companyb.companyapp.contracts.remittance

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`): old clients decode newer server values as UNKNOWN instead of failing
 * the whole response. Never persisted, never sent.
 */
@Serializable
enum class RemittanceType { SESSION, PRODUCT, UNKNOWN }

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`). Never persisted, never sent.
 */
@Serializable
enum class RemittanceMethod { BANK_TRANSFER, HANDED_TO_ACCOUNTANT, UNKNOWN }

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`). Never persisted, never sent.
 */
@Serializable
enum class RemittanceStatus { DRAFT, SUBMITTED, UNKNOWN }

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`). Never persisted, never sent.
 */
@Serializable
enum class RemittanceLineType { SESSION, PRODUCT_SALE, UNKNOWN }

@Serializable
data class CreateRemittanceDraftRequest(
    val id: String,
    val type: RemittanceType,
    val branchId: String,
    val method: RemittanceMethod,
    val dateRangeStart: String,
    val dateRangeEnd: String,
)

@Serializable
data class RemittanceResponse(
    val id: String,
    val type: RemittanceType = RemittanceType.UNKNOWN,
    val status: RemittanceStatus = RemittanceStatus.UNKNOWN,
    val branchId: String,
    val method: RemittanceMethod = RemittanceMethod.UNKNOWN,
    val submittedDate: String,
    val submittedAt: String? = null,
    val submittedBy: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val createdAt: String,
    val version: Int,
    val netIncome: String? = null,
)

@Serializable
data class CreateRemittanceLineRequest(
    val id: String,
    val type: RemittanceLineType,
    val sessionId: String? = null,
    val productSaleId: String? = null,
    val amount: String,
)

@Serializable
data class RemittanceLineResponse(
    val id: String,
    val remittanceId: String,
    val type: RemittanceLineType = RemittanceLineType.UNKNOWN,
    val sessionId: String?,
    val productSaleId: String?,
    val createdBy: String,
    val createdAt: String,
    val deletedBy: String?,
    val deletedAt: String?,
    val amount: String,
)

@Serializable
data class AddDayBreakdownRequest(
    val id: String,
    val branchDayId: String,
)

@Serializable
data class RemittanceDayBreakdownResponse(
    val id: String,
    val remittanceId: String,
    val branchDayId: String,
)

@Serializable
data class SubmitRemittanceRequest(
    val expectedVersion: Int,
)

@Serializable
data class UndoRemittanceRequest(
    val expectedVersion: Int,
    val reason: String,
)

@Serializable
data class UpdateRemittanceHeaderRequest(
    val type: RemittanceType,
    val method: RemittanceMethod,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val expectedVersion: Int,
)

@Serializable
data class RemittanceSubmitResponse(
    val id: String,
    val type: RemittanceType = RemittanceType.UNKNOWN,
    val status: RemittanceStatus = RemittanceStatus.UNKNOWN,
    val branchId: String,
    val method: RemittanceMethod = RemittanceMethod.UNKNOWN,
    val submittedDate: String,
    val submittedAt: String? = null,
    val submittedBy: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val createdAt: String,
    val version: Int,
    val grossIncome: String,
    val totalCompensation: String,
    val totalExpenses: String,
    val netIncome: String,
)

@Serializable
data class RemittanceDetailResponse(
    val id: String,
    val type: RemittanceType = RemittanceType.UNKNOWN,
    val status: RemittanceStatus = RemittanceStatus.UNKNOWN,
    val branchId: String,
    val method: RemittanceMethod = RemittanceMethod.UNKNOWN,
    val submittedDate: String,
    val submittedAt: String? = null,
    val submittedBy: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val createdAt: String,
    val version: Int,
    val lines: List<RemittanceLineResponse>,
    val totalAmount: String,
    val dayBreakdowns: List<RemittanceDayBreakdownResponse>,
    val snapshot: RemittanceFinancialSnapshotResponse? = null,
)

@Serializable
data class RemittanceFinancialSnapshotResponse(
    val remittanceId: String,
    val grossIncome: String,
    val totalCompensation: String,
    val totalExpenses: String,
    val netIncome: String,
    val snapshottedAt: String,
)

@Serializable
data class RemittanceDriftResponse(
    val frozen: RemittanceFinancialSnapshotResponse,
    val currentCompensation: String,
    val currentExpenses: String,
    val currentNet: String,
)

@Serializable
data class RemittanceSessionPickerEntryResponse(
    val id: String,
    val clientName: String?,
    val bookedAt: String?,
    val sessionStatus: SessionStatus = SessionStatus.UNKNOWN,
    val finalPrice: String,
)

@Serializable
data class RemittanceProductSalePickerEntryResponse(
    val id: String,
    val productName: String,
    val quantity: Int,
    val totalAmountAtTime: String,
    val soldAt: String,
)

@Serializable
data class RemittanceDayPickerEntryResponse(
    val id: String,
    val date: String,
    val status: DayStatus = DayStatus.UNKNOWN,
)
