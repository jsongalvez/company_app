package com.companyb.companyapp.navigation

import com.companyb.companyapp.dto.DashboardSessionResponse
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    data object Login : Route()

    // #350 — public invite redemption: paste the minted code, set the account password.
    @Serializable
    data object AcceptInvite : Route()

    // #353 — public forgot-password flow: request a reset code, then redeem it.
    @Serializable
    data object ForgotPassword : Route()

    @Serializable
    data object BranchSelect : Route()

    @Serializable
    data class Dashboard(
        // #358 — relief deep link: dashboard scoped to branch+date. Null = today at the
        // selected branch (every pre-#358 call site).
        val branchId: String? = null,
        val date: String? = null,
    ) : Route()

    @Serializable
    data object Clients : Route()

    // #113 — pushed on both platforms (#99 outline: "Search → tap row → push Route.ClientDetail
    // (both platforms — push routes per #91/#95; no desktop pane)"). SessionDetail got a desktop
    // pushed route in #152 (the #151 Q6 scoped revision, notification entry point only).
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
    data object UserManagement : Route()

    // #348 — start-a-session flow: pushed from the dashboard's "New session" button; the
    // branch context comes from SessionState (entry-scoped VMs bake it in at construction).
    @Serializable
    data object SessionCreate : Route()

    @Serializable
    data class SessionDetail(
        val sessionId: String,
        // #152 — the dashboard path passes the enriched row via nav args (zero extra requests);
        // the Notifications call site navigates with sessionId only (row = null → the detail
        // screen fetches once via GET /api/sessions/{sessionId}, bearer-only gate — the #151
        // resolution, #152 build).
        val row: DashboardSessionResponse? = null,
    ) : Route()
}
