package com.companyb.companyapp.contracts.session

import com.companyb.companyapp.contracts.branchday.DayStatus
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
    /** display name of the practitioner the client requested, when one was recorded. */
    val requestedPractitionerName: String? = null,
    /**
     * branch the session's day belongs to. Drives the client-side edit gate (strict
     * BRANCH-context `EDIT_BRANCH_DATA`) and the practitioner-add member directory read.
     * Defaults to blank (fail-closed → read-only detail) so older fixtures decode unchanged.
     */
    val branchId: String = "",
    /**
     * The session's day: open, past or remitted. Set by the single-session read so its page
     * hides the changes the day refuses; day lists leave it null, their page holds the day.
     */
    val dayStatus: DayStatus? = null,
    /** The single-session read's day row, so its page lists and adds the session's product sales there. */
    val branchDayId: String? = null,
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

/**
 * Everything the Today screen shows, from one read. Each part keeps its own right to see it,
 * so a part the caller may not see is null: [dashboard] needs a clock-in here today (the
 * reason is in [dashboardRefusal]), [bookings] needs the right to view the branch, [roster]
 * needs a home-branch membership.
 */
@Serializable
data class TodayScreenResponse(
    val dashboard: DashboardResponse?,
    val dashboardRefusal: String?,
    val bookings: List<com.companyb.companyapp.contracts.session.BookingResponse>?,
    val roster: List<com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse>?,
    /**
     * Earlier open days (ISO dates, oldest first) that still hold a Waiting session, for anyone
     * who may see the branch: they no longer block today, so Today names them to be marked.
     */
    val earlierWaitingDays: List<String>? = null,
)
