package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SessionType { REGULAR, SECOND_SESSION, SUBSEQUENT, PROVINCIAL_FIRST, MEDICAL_MISSION }
