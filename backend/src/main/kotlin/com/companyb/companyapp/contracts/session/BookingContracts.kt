package com.companyb.companyapp.contracts.session

import kotlinx.serialization.Serializable

/** Where a booked visit stands: still expected, came in, or called off. */
@Serializable
enum class BookingStatus { BOOKED, CAME, CANCELLED }

/** Book a client for a future visit at a branch. [id] is client-made so a retry never doubles it. */
@Serializable
data class CreateBookingRequest(
    val id: String,
    val clientId: String,
    val visitDate: String,
    val visitTime: String? = null,
    val note: String? = null,
)

/** Move, re-note, cancel or mark a booked visit as came. */
@Serializable
data class UpdateBookingRequest(
    val visitDate: String,
    val visitTime: String? = null,
    val note: String? = null,
    val status: BookingStatus,
)

@Serializable
data class BookingResponse(
    val id: String,
    val branchId: String,
    val branchName: String,
    val clientId: String,
    val clientName: String,
    val clientPhone: String? = null,
    val visitDate: String,
    val visitTime: String? = null,
    val note: String? = null,
    val status: BookingStatus,
    val createdByName: String,
    val createdAt: String,
    val updatedByName: String? = null,
    val updatedAt: String? = null,
)
