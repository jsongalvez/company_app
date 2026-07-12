package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.BranchType
import kotlinx.serialization.Serializable

@Serializable
data class CreateBranchRequest(
    val id: String,
    val name: String,
    val branchType: BranchType,
)

@Serializable
data class BranchResponse(
    val id: String,
    val name: String,
    val branchType: BranchType,
)
