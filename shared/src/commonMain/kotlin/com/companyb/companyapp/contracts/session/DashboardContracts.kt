package com.companyb.companyapp.contracts.session

import kotlinx.serialization.Serializable

@Serializable
data class DashboardResponse(
    val sessions: List<DashboardSessionResponse>,
    val commission: DashboardCommissionResponse,
)

@Serializable
data class DashboardSessionResponse(
    val id: String,
    val clientId: String,
    val clientName: String?,
    val sessionType: SessionType = SessionType.UNKNOWN,
    val isWalkIn: Boolean,
    val sessionStatus: SessionStatus = SessionStatus.UNKNOWN,
    val basePrice: String,
    val finalPrice: String,
    val remarks: String?,
    val otherConcerns: String?,
    val bookedAt: String?,
    val nextAppointmentDate: String?,
    val version: Int,
    val isVoided: Boolean,
    val practitioners: List<DashboardPractitionerResponse> = emptyList(),
    val concerns: List<ConcernResponse> = emptyList(),
    /** #366 — display name of the practitioner the client requested, when one was recorded. */
    val requestedPractitionerName: String? = null,
    /**
     * #382 — branch the session's day belongs to. Drives the client-side edit gate (strict
     * BRANCH-context `EDIT_BRANCH_DATA`) and the practitioner-add member directory read.
     * Defaults to blank (fail-closed → read-only detail) so older fixtures decode unchanged.
     */
    val branchId: String = "",
)

@Serializable
data class DashboardPractitionerResponse(
    val practitionerId: String,
    val displayName: String,
    val remarks: String?,
    val slotAtTime: Int,
)

@Serializable
data class DashboardCommissionResponse(
    val amount: String,
    val productSalesCount: Int,
)
