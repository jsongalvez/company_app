package com.companyb.companyapp.domain

/**
 * Lifecycle of a branch-initiated relief invite (#159): PENDING until the invitee
 * responds (ACCEPTED/DECLINED) or the inviter retracts (RETRACTED — PENDING only).
 * The accepted grant is written at ACCEPT time; day-state is the expiry (a PENDING
 * invite whose day is past renders "expired" — no cron).
 */
enum class ReliefInviteStatus { PENDING, ACCEPTED, DECLINED, RETRACTED }
