package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

/**
 * #425 - the session status correction vocabulary (owner ruling 2026-08-26), shared so the
 * backend enforcement and the dashboard dropdown mirror can never diverge.
 *
 * - A ROUTINE MARK is the forward direction the lifecycle already allows anyone with edit
 *   authority: PENDING -> COMPLETED / NO_SHOW / CANCELLED (walk-ins still blocked from
 *   NO_SHOW/CANCELLED server-side).
 * - A CORRECTION is any other swap among PENDING / NO_SHOW / CANCELLED -- fixing a mis-mark
 *   (NO_SHOW -> PENDING) or reclassifying between terminal outcomes. Coordinator authority
 *   ([CapabilityCodes.EDIT_PAST_DAY]) is required at every day state; day-state rules
 *   (PAST/REMITTED gates, REMITTED reason) apply on top.
 * - COMPLETED is immutable outside the void/unvoid machinery: [isStatusCorrection] is false
 *   for any pair touching it, so such requests are rejected outright - money flows are never
 *   resurrected through status edits.
 */
fun isRoutineStatusMark(
    from: SessionStatus,
    to: SessionStatus,
): Boolean = from == SessionStatus.PENDING && to != SessionStatus.PENDING

fun isStatusCorrection(
    from: SessionStatus,
    to: SessionStatus,
): Boolean =
    from != to && from != SessionStatus.COMPLETED && to != SessionStatus.COMPLETED &&
        !isRoutineStatusMark(from, to)

private val WALK_IN_FORBIDDEN_STATUS_VALUES = setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)

fun isStatusTransitionAllowed(
    from: SessionStatus,
    to: SessionStatus,
    isWalkIn: Boolean,
): Boolean =
    (!isWalkIn || to !in WALK_IN_FORBIDDEN_STATUS_VALUES) &&
        (isRoutineStatusMark(from, to) || isStatusCorrection(from, to))
