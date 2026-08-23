package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

/**
 * Lifecycle of a branch-initiated relief invite (#159): PENDING until the invitee
 * responds (ACCEPTED/DECLINED), the inviter retracts (RETRACTED — PENDING only), or
 * any active branch member revokes an acceptance (REVOKED — #374, the #363 rulings;
 * removes the day grant atomically and stops before the invitee clocks in). The
 * accepted grant is written at ACCEPT time; day-state is the expiry (a PENDING invite
 * whose day is past renders "expired" — no cron).
 */
@Serializable
enum class ReliefInviteStatus { PENDING, ACCEPTED, DECLINED, RETRACTED, REVOKED }
