package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import kotlinx.serialization.Serializable

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
    val bookedAt: String? = null,
    val nextAppointmentDate: String? = null,
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
