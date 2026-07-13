package com.companyb.companyapp.dto

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
    val status: String,
    val version: Int,
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
    val sessionType: String,
    val isWalkIn: Boolean,
    val sessionStatus: String,
    val basePrice: String,
    val finalPrice: String,
    val remarks: String?,
    val otherConcerns: String?,
    val bookedAt: String?,
    val nextAppointmentDate: String?,
    val version: Int,
)
