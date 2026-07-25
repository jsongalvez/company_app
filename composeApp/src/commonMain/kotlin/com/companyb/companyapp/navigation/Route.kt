package com.companyb.companyapp.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    data object Login : Route()

    @Serializable
    data object BranchSelect : Route()

    @Serializable
    data object Dashboard : Route()

    @Serializable
    data object Clients : Route()

    @Serializable
    data object Inventory : Route()

    @Serializable
    data object Finance : Route()

    @Serializable
    data object RemittanceList : Route()

    @Serializable
    data class RemittanceDetail(
        val id: String,
    ) : Route()

    @Serializable
    data object Notifications : Route()

    @Serializable
    data object AuditLog : Route()

    @Serializable
    data object Reports : Route()

    @Serializable
    data object UserManagement : Route()

    @Serializable
    data class SessionDetail(
        val sessionId: String,
    ) : Route()
}
