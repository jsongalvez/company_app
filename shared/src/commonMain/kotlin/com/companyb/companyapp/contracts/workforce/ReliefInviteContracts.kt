package com.companyb.companyapp.contracts.workforce

import kotlinx.serialization.Serializable

/**
 * Lifecycle of a branch-initiated relief invite (#159): PENDING until the invitee
 * responds (ACCEPTED/DECLINED), the inviter retracts (RETRACTED — PENDING only), or
 * any active branch member revokes an acceptance (REVOKED — #374, the #363 rulings;
 * removes the day grant atomically and stops before the invitee clocks in). The
 * accepted grant is written at ACCEPT time; day-state is the expiry (a PENDING invite
 * whose day is past renders "expired" — no cron).
 *
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`). Never persisted, never sent.
 */
@Serializable
enum class ReliefInviteStatus { PENDING, ACCEPTED, DECLINED, RETRACTED, REVOKED, UNKNOWN }

/**
 * One relief invite as rendered by both lists (received + sent) and returned by every
 * lifecycle endpoint. The invite is branch/day-scoped: branchName + date render the
 * row ("who invited me where, for when"), status drives the frontend lifecycle
 * (PENDING → Accept/Decline; expired = PENDING + date in the past).
 */
@Serializable
data class ReliefInviteResponse(
    val id: String,
    val branchId: String,
    val branchName: String,
    val branchDayId: String,
    val date: String,
    val invitedBy: String,
    val inviterName: String,
    val invitee: String,
    val inviteeName: String,
    val status: ReliefInviteStatus = ReliefInviteStatus.UNKNOWN,
    val createdAt: String,
    val respondedAt: String? = null,
)

@Serializable
data class CreateReliefInviteRequest(
    val inviteeUserId: String,
    val date: String,
)

@Serializable
data class ReliefCandidateResponse(
    val id: String,
    val username: String,
    val displayName: String,
)
