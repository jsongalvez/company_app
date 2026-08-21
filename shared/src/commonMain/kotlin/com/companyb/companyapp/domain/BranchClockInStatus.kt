package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

@Serializable
enum class BranchClockInStatus { CLOCKED_IN_HERE, CLOCKED_IN_ELSEWHERE, NOT_CLOCKED_IN }
