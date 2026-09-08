package com.companyb.companyapp.contracts.finance

import kotlinx.serialization.Serializable

@Serializable
enum class ExpenseCategory {
    PANTRY,
    COMMUNICATION,
    WATER,
    TRANSPORTATION,
    ELECTRICITY,
    RENTAL,
    OFFICE_SUPPLIES,
    FURNITURE_FIXTURES,
    MISCELLANEOUS,
}

@Serializable
data class CreateExpenseRequest(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: ExpenseCategory,
    val notes: String? = null,
    val reason: String? = null,
)

@Serializable
data class DeleteExpenseRequest(
    val reason: String,
    val expectedVersion: Int,
)

// #153 — restore body carries only the optional day-state reason (required iff the owning
// branch day is REMITTED); the restore itself needs no content.
@Serializable
data class RestoreExpenseRequest(
    val reason: String? = null,
)

@Serializable
data class UpdateExpenseRequest(
    val amount: String,
    val category: ExpenseCategory,
    val notes: String? = null,
    val expectedVersion: Int,
    val reason: String? = null,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: ExpenseCategory,
    val notes: String?,
    val createdBy: String,
    val createdAt: String,
    val deletedBy: String?,
    val deletedAt: String?,
    // #153 — additive nullable: the deletion reason (displayed dimmed on restored/soft-deleted
    // rows); null for live rows and for responses from pre-V18 serializers.
    val deletedReason: String? = null,
    val version: Int,
)

@Serializable
data class CreateCompensationRequest(
    val id: String,
    val workBranchDayId: String,
    val payingBranchDayId: String,
    val userId: String,
    val amount: String,
    val note: String? = null,
    val reason: String? = null,
)

@Serializable
data class UpdateCompensationRequest(
    val amount: String,
    val expectedVersion: Int,
    val note: String? = null,
    val reason: String? = null,
)

@Serializable
data class CompensationResponse(
    val id: String,
    val workBranchDayId: String,
    val payingBranchDayId: String,
    val userId: String,
    val userName: String? = null,
    val amount: String,
    val assignedBy: String,
    val assignedAt: String,
    val note: String?,
    val version: Int,
)

@Serializable
data class CreateAllowanceRequest(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val amount: String,
    val reason: String? = null,
)

@Serializable
data class AllowanceResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val amount: String,
    val assignedBy: String,
    val assignedAt: String,
)
