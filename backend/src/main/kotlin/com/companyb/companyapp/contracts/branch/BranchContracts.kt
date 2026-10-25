package com.companyb.companyapp.contracts.branch

import kotlinx.serialization.Serializable

/**
 * [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `docs/architecture.md`): old clients decode newer server values as UNKNOWN instead of failing
 * the whole response. Never persisted, never sent.
 */
@Serializable
enum class BranchType { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION, UNKNOWN }

/**
 * [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `docs/architecture.md`). Never persisted, never sent.
 */
@Serializable
enum class BranchClockInStatus { CLOCKED_IN_HERE, CLOCKED_IN_ELSEWHERE, NOT_CLOCKED_IN, UNKNOWN }

@Serializable
data class CreateBranchRequest(
    val id: String,
    val name: String,
    val branchType: BranchType,
)

@Serializable
data class RenameBranchRequest(
    val name: String,
)

@Serializable
data class BranchResponse(
    val id: String,
    val name: String,
    val branchType: BranchType = BranchType.UNKNOWN,
    // 503bf228 — commission for every product sold here; null = each product's shared one.
    val commissionAmount: String? = null,
)

/** 503bf228 — set the branch-wide commission; null or blank = use each product's shared one. */
@Serializable
data class SetBranchCommissionRequest(
    val commissionAmount: String? = null,
)

@Serializable
data class MeBranchResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType = BranchType.UNKNOWN,
    val clockInStatus: BranchClockInStatus = BranchClockInStatus.UNKNOWN,
    val isRelief: Boolean,
    val assignmentId: String? = null,
    // own-assignment slot (Branch Slot, 1 = senior); null for relief rows, which
    // have no branch assignment to order.
    val slot: Short? = null,
)
