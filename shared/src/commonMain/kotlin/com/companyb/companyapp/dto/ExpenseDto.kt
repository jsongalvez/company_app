package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateExpenseRequest(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: String,
    val notes: String? = null,
    val reason: String? = null,
)

@Serializable
data class DeleteExpenseRequest(
    val reason: String,
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
    val category: String,
    val notes: String? = null,
    val expectedVersion: Int,
    val reason: String? = null,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: String,
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
