package com.companyb.companyapp.contracts.session

import kotlinx.serialization.Serializable

@Serializable
enum class SessionType { REGULAR, SECOND_SESSION, SUBSEQUENT, PROVINCIAL_FIRST, MEDICAL_MISSION }

@Serializable
enum class SessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

/**
 * the session status correction vocabulary (owner ruling 2026-08-26), shared so the
 * backend enforcement and the dashboard dropdown mirror can never diverge.
 *
 * - A ROUTINE MARK is the forward direction the lifecycle already allows anyone with edit
 *   authority: PENDING -> COMPLETED / NO_SHOW / CANCELLED (walk-ins still blocked from
 *   NO_SHOW/CANCELLED server-side).
 * - A CORRECTION is any other swap among PENDING / NO_SHOW / CANCELLED -- fixing a mis-mark
 *   (NO_SHOW -> PENDING) or reclassifying between terminal outcomes. Coordinator authority
 *   ([com.companyb.companyapp.contracts.authorization.CapabilityCodes.EDIT_PAST_DAY])
 *   is required at every day state; day-state rules
 *   (PAST/REMITTED gates, REMITTED reason) apply on top.
 * - An UNDO puts a marked session (COMPLETED / NO_SHOW / CANCELLED) back to PENDING. Anyone who
 *   may mark may undo (owner ruling 2026-10-18: mis-taps must be fixable); day-state rules still
 *   apply, and the command refuses to undo a COMPLETED session that sits on a remittance.
 * - COMPLETED is otherwise immutable outside void/unvoid: [isStatusCorrection] is false for any
 *   pair touching it.
 */
fun isRoutineStatusMark(
    from: SessionStatus,
    to: SessionStatus,
): Boolean = from == SessionStatus.PENDING && to != SessionStatus.PENDING

fun isStatusUndo(
    from: SessionStatus,
    to: SessionStatus,
): Boolean = to == SessionStatus.PENDING && from != SessionStatus.PENDING

fun isStatusCorrection(
    from: SessionStatus,
    to: SessionStatus,
): Boolean =
    from != to && from != SessionStatus.COMPLETED && to != SessionStatus.COMPLETED &&
        !isRoutineStatusMark(from, to) && !isStatusUndo(from, to)

private val WALK_IN_FORBIDDEN_STATUS_VALUES = setOf(SessionStatus.NO_SHOW, SessionStatus.CANCELLED)

fun isStatusTransitionAllowed(
    from: SessionStatus,
    to: SessionStatus,
    isWalkIn: Boolean,
): Boolean =
    (!isWalkIn || to !in WALK_IN_FORBIDDEN_STATUS_VALUES) &&
        (isRoutineStatusMark(from, to) || isStatusUndo(from, to) || isStatusCorrection(from, to))

@Serializable
data class CreateSessionRequest(
    val id: String,
    val clientId: String,
    val branchId: String,
    val isWalkIn: Boolean,
    val requestedPractitionerId: String? = null,
    val finalPrice: String,
    val remarks: String? = null,
    val otherConcerns: String? = null,
    val nextAppointmentDate: String? = null,
    val reason: String? = null,
    /** The day the session belongs to (yyyy-MM-dd); today when absent. A past day takes adds from any branch member. */
    val date: String? = null,
    /** Practitioners to add right after the session is saved, so a save is one request. */
    val practitioners: List<AddPractitionerRequest> = emptyList(),
    /** Listed concerns to tick right after the save, so ticking them costs no extra round trips. */
    val concernIds: List<String> = emptyList(),
    /** Concerns typed by name that are not on the list yet: each joins the list and this session. */
    val newConcerns: List<NewSessionConcernRequest> = emptyList(),
)

@Serializable
data class NewSessionConcernRequest(
    val id: String,
    val label: String,
)

@Serializable
data class UpdateSessionStatusRequest(
    val status: SessionStatus,
    val version: Int,
    val reason: String? = null,
)

@Serializable
data class UpdateSessionFinalPriceRequest(
    val finalPrice: String,
    val version: Int,
    val reason: String? = null,
)

@Serializable
data class VoidSessionRequest(
    val id: String,
    val voidReason: String,
)

@Serializable
data class UnvoidSessionRequest(
    val unvoidedReason: String,
)

@Serializable
data class SessionVoidResponse(
    val id: String,
    val sessionId: String,
    val voidedAt: String,
    val voidedBy: String,
    val voidReason: String,
    val unvoidedAt: String? = null,
    val unvoidedBy: String? = null,
    val unvoidedReason: String? = null,
)

@Serializable
data class SessionResponse(
    val id: String,
    val clientId: String,
    val branchDayId: String,
    val requestedPractitionerId: String?,
    val sessionType: SessionType,
    val isWalkIn: Boolean,
    val sessionStatus: SessionStatus,
    val basePrice: String,
    val finalPrice: String,
    val remarks: String?,
    val otherConcerns: String?,
    val bookedAt: String?,
    val nextAppointmentDate: String?,
    val version: Int,
    val concerns: List<ConcernResponse> = emptyList(),
    /** Only on a create asked with `?withToday=true`: Today after the save, so the phone needs no second request. */
    val todayScreen: TodayScreenResponse? = null,
)

@Serializable
data class AddPractitionerRequest(
    val id: String,
    val practitionerId: String,
    val remarks: String? = null,
    val reason: String? = null,
)

@Serializable
data class UpdatePractitionerRemarksRequest(
    val remarks: String? = null,
    val reason: String? = null,
)

@Serializable
data class RemovePractitionerRequest(
    val reason: String? = null,
)

@Serializable
data class SessionPractitionerResponse(
    val id: String,
    val sessionId: String,
    val practitionerId: String,
    val remarks: String?,
    val slotAtTime: Int,
)

/**
 * pre-create preview for the SessionCreate screen: the session type the server WILL
 * assign (same [SessionType] algorithm as create) and the base
 * rate that will default the final price. Server-authoritative — the frontend never replicates
 * history counting or rate lookup.
 */
@Serializable
data class SessionPreviewResponse(
    val sessionType: SessionType,
    val basePrice: String,
)
