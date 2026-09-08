package com.companyb.companyapp.contracts.workforce

import com.companyb.companyapp.contracts.branch.BranchType
import kotlinx.serialization.Serializable

@Serializable
enum class ReliefAccessStatus { PENDING, GRANTED, DENIED, CANCELLED }

@Serializable
data class ClockInRequest(
    val attendanceId: String,
    val branchId: String,
)

@Serializable
data class ClockOutRequest(
    val attendanceId: String,
)

@Serializable
data class ClockInResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val markedBy: String,
    val clockIn: String,
    val clockOut: String? = null,
    val isRelief: Boolean,
)

@Serializable
data class ClockOutResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val markedBy: String,
    val clockIn: String,
    val clockOut: String?,
    val isRelief: Boolean,
)

/**
 * #669 — the caller's authoritative active shift for the current operational day
 * (Asia/Manila): the exact identifiers AppSessionState needs to resume without another
 * clock-in. A null shift means no active shift — the client opens branch selection.
 */
@Serializable
data class ActiveShiftResponse(
    val attendanceId: String,
    val branchId: String,
    val branchName: String,
    val branchDayId: String,
    /** ISO yyyy-MM-dd operational date owning the shift. */
    val date: String,
    val isRelief: Boolean,
)

@Serializable
data class ActiveAttendanceResponse(
    val shift: ActiveShiftResponse?,
)

/**
 * #404 — mark another home-branch member present or absent at one branch today.
 * `attendanceId` is the client-generated idempotency key and is required when
 * [present] is true; absent-marks close the target's open window and need no id.
 */
@Serializable
data class MarkAttendanceRequest(
    val userId: String,
    val present: Boolean,
    val attendanceId: String? = null,
)

@Serializable
data class AttendanceMarkResponse(
    /** The affected attendance row; null when an absent-mark found no open window (idempotent no-op). */
    val attendance: ClockInResponse?,
)

/** #404 — one home-branch member's live attendance state at the branch today. */
@Serializable
data class MemberAttendanceResponse(
    val assignmentId: String,
    val userId: String,
    val displayName: String,
    /** Branch Slot ordering (1 = senior); cosmetic, mirrored from the assignment. */
    val slot: Short,
    val present: Boolean,
)

@Serializable
data class ReliefAccessRequest(
    val requestId: String,
    val branchId: String,
    /** ISO yyyy-MM-dd; null = the current operational day (Asia/Manila). Future dates allowed. */
    val date: String? = null,
    val reason: String? = null,
)

@Serializable
data class GrantReliefAccessRequest(
    val reason: String? = null,
)

@Serializable
data class DenyReliefAccessRequest(
    val reason: String? = null,
)

@Serializable
data class ReliefAccessResponse(
    val id: String,
    val branchDayId: String,
    val requestedBy: String,
    val requestStatus: ReliefAccessStatus,
    val grantedBy: String? = null,
    val grantedAt: String? = null,
    /** Branch context — populated on the mine list only (the per-day read implies it). */
    val branchId: String? = null,
    val branchName: String? = null,
    val date: String? = null,
    /**
     * #358 — requester display name; populated on the per-day read (the deep-link panel
     * labels rows with it). Null on the mine list (the caller is the requester).
     */
    val requesterName: String? = null,
)

@Serializable
data class ReliefBranchOptionResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType,
)
