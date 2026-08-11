package com.companyb.companyapp.navigation

import com.companyb.companyapp.dto.DashboardSessionResponse
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

    // #113 — pushed on both platforms (#99 outline: "Search → tap row → push Route.ClientDetail
    // (both platforms — push routes per #91/#95; no desktop pane)"). Unlike SessionDetail, this
    // route EXISTS on desktop (desktop SessionDetail is the #91 inline-pane exception).
    @Serializable
    data class ClientDetail(
        val clientId: String,
    ) : Route()

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

    // #123 — D8 "Full history for this record": pushed on both platforms (#91 push-route lock,
    // ClientDetail precedent — content-level Back TextButton; the pushed-route topbar pattern
    // stays fog).
    @Serializable
    data class AuditLogHistory(
        val tableName: String,
        val recordId: String,
    ) : Route()

    @Serializable
    data object Reports : Route()

    @Serializable
    data object UserManagement : Route()

    @Serializable
    data class SessionDetail(
        val sessionId: String,
        // #147 — mobile dashboard passes the enriched row from the dashboard poll (no
        // session-detail GET endpoint exists — the #146-corrected fact). The Notifications
        // call site navigates with sessionId only (row = null → the detail screen renders
        // its limited state; a session-detail GET + gate decision is its own fog).
        val row: DashboardSessionResponse? = null,
    ) : Route()
}
