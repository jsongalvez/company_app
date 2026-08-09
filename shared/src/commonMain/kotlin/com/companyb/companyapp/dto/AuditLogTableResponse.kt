package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogTableResponse(
    val tableName: String,
    val label: String,
)
