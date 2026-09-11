package com.companyb.companyapp.contracts.branch

import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`): old clients decode newer server values as UNKNOWN instead of failing
 * the whole response. Never persisted, never sent.
 */
@Serializable
enum class BranchType { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION, UNKNOWN }

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`). Never persisted, never sent.
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
data class BranchResponse(
    val id: String,
    val name: String,
    val branchType: BranchType = BranchType.UNKNOWN,
)

@Serializable
data class MeBranchResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType = BranchType.UNKNOWN,
    val clockInStatus: BranchClockInStatus = BranchClockInStatus.UNKNOWN,
    val isRelief: Boolean,
    val assignmentId: String? = null,
    // #381 — own-assignment slot (Branch Slot, 1 = senior); null for relief rows, which
    // have no branch assignment to order.
    val slot: Short? = null,
)
