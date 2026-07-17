package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val id: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val suffix: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val gender: String,
    val age: Int,
    val systolicBp: Short? = null,
    val diastolicBp: Short? = null,
    val medicalConditions: String? = null,
)

@Serializable
data class UpdateClientRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val suffix: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val systolicBp: Short? = null,
    val diastolicBp: Short? = null,
    val medicalConditions: String? = null,
)

@Serializable
data class ClientResponse(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String?,
    val gender: String,
    val age: Int,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
)
