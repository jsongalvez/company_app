package com.companyb.companyapp.contracts.branchday

import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`): an unknown day state degrades the row read-only client-side.
 * Never persisted, never sent.
 */
@Serializable
enum class DayStatus { OPEN, PAST, REMITTED, UNKNOWN }

@Serializable
data class BranchDayTodayResponse(
    val branchDayId: String,
    val status: DayStatus = DayStatus.UNKNOWN,
)

@Serializable
data class BranchDayUserResponse(
    val userId: String,
    val displayName: String,
)
