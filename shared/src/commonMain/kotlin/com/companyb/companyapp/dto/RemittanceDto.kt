package com.companyb.companyapp.dto

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import kotlinx.serialization.Serializable

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
    val type: RemittanceType,
    val status: RemittanceStatus,
    val branchId: String,
    val method: RemittanceMethod,
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
    val type: RemittanceLineType,
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
    val type: RemittanceType,
    val status: RemittanceStatus,
    val branchId: String,
    val method: RemittanceMethod,
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
    val type: RemittanceType,
    val status: RemittanceStatus,
    val branchId: String,
    val method: RemittanceMethod,
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
    val sessionStatus: SessionStatus,
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
    val status: DayStatus,
)
