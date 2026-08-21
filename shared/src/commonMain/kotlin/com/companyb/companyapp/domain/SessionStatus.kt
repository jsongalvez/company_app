package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
