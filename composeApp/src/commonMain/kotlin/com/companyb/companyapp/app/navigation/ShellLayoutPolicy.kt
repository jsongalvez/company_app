package com.companyb.companyapp.app.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #671 — the adaptive shell contract: width-driven chrome, parent-aware selection, and
 * task-grouped titles.
 *
 * Pure constants + pure decision helpers. Composable owners live in the NavHost actuals
 * and `DrawerContent`; this file stays composition-free so its decisions are unit-testable
 * without a Compose runtime (the #670 `OperationalUiContract` shape).
 *
 * This deliberately supersedes ADR-0020's compile-time platform-target split for shell
 * chrome: sidebar-vs-drawer follows measured viewport width, not the build target.
 * Route identifiers are unchanged. Feature-level list/detail splits stay with their
 * owners (#672 dashboard, #677 remittance) — this contract covers shell chrome only.
 */
object ShellLayoutPolicy {
    /** Window width at or above which the shell pins a sidebar; below it a modal drawer. */
    val sidebarBreakpoint: Dp = 1200.dp

    /** Pinned sidebar width (replaces the 360dp drawer that crowded working space). */
    val sidebarWidth: Dp = 224.dp

    /** Wide layout chrome; compact layouts change grouping/navigation instead. */
    fun useSidebar(viewportWidth: Dp): Boolean = viewportWidth >= sidebarBreakpoint

    /**
     * Section root for any route: detail/pushed routes resolve to the parent destination
     * the drawer highlights and the compact top bar titles. Top-level routes map to
     * themselves.
     */
    fun parentFor(route: Route): Route =
        when (route) {
            is Route.SessionCreate -> Route.Dashboard()
            is Route.SessionDetail -> Route.Dashboard()
            is Route.ClientDetail -> Route.Clients
            is Route.RemittanceDetail -> Route.RemittanceList
            is Route.AuditLogHistory -> Route.AuditLog
            else -> route
        }

    /**
     * Drawer/top-bar label for a section root. Domain nouns are preserved; route
     * identifiers need not change (Sessions rides the existing Dashboard route).
     */
    fun sectionTitle(root: Route): String =
        when (root) {
            is Route.Dashboard -> "Sessions"

            is Route.Clients -> "Clients"

            is Route.Inventory -> "Inventory"

            is Route.BaseRates -> "Base Rates"

            is Route.ProductCatalog -> "Product Catalog"

            is Route.Finance -> "Finance & Reports"

            is Route.RemittanceList -> "Remittance"

            is Route.Notifications -> "Notifications"

            is Route.AuditLog -> "Audit Log"

            is Route.UserManagement -> "Team & branches"

            is Route.MedicalMissionDelegates -> "Mission delegates"

            is Route.Profile -> "Profile"

            is Route.BranchSelect -> "Select branch"

            is Route.Login -> "Login"

            is Route.AcceptInvite -> "Accept invite"

            is Route.ForgotPassword -> "Reset password"

            // Intentional future-proofing for the never-blank top-bar contract: every
            // parentFor output today matches explicitly above, so a newly added route
            // titles Sessions until its owner assigns its noun.
            else -> "Sessions"
        }

    /** Compact top-bar title for whatever is on screen: the parent section, never blank. */
    fun topBarTitle(current: Route?): String = sectionTitle(parentFor(current ?: Route.Dashboard()))

    /**
     * Selecting the already-active destination is a no-op: no navigate, no drawer close,
     * no state reset. Deep-linked dashboard variants (`Dashboard(branchId, date)`) are
     * distinct destinations from the shift home (`Dashboard()`), so returning home from
     * a relief day still navigates.
     */
    fun isSameDestination(
        current: Route?,
        target: Route,
    ): Boolean = current == target

    /**
     * What the shell shows as branch/shift context. The viewed branch/date is the
     * relief deep-link pair when present, else the clocked-in shift. The shift is
     * surfaced as an explicitly labeled secondary only when it differs from the view —
     * choosing a viewed branch never implies clocking into it.
     */
    fun viewedContext(
        current: Route?,
        clockBranchId: String?,
        clockBranchName: String?,
        clockDate: String?,
    ): ViewedContext {
        val deepLink = current as? Route.Dashboard
        return if (deepLink?.branchId != null && deepLink.date != null) {
            // Same viewed branch AND same operational date: the shift secondary stays
            // hidden. A same-branch different-date view still names the shift (with its
            // date), so the viewed date is never mistaken for the shift date.
            // A foreign branch shows its id prefix (2^-32 collision for UUIDs): the
            // shell never invents a branch name it was not given.
            val matchesShift = deepLink.branchId == clockBranchId && deepLink.date == clockDate
            ViewedContext(
                branchLabel =
                    if (deepLink.branchId == clockBranchId && clockBranchName != null) {
                        clockBranchName
                    } else {
                        "Branch ${deepLink.branchId.take(BRANCH_ID_PREFIX)}"
                    },
                dateLabel = deepLink.date,
                shiftLabel =
                    if (!matchesShift && clockBranchName != null) {
                        listOfNotNull("Shift: $clockBranchName", clockDate).joinToString(" · ")
                    } else {
                        null
                    },
            )
        } else {
            ViewedContext(
                branchLabel = clockBranchName ?: "",
                dateLabel = clockDate,
                shiftLabel = null,
            )
        }
    }

    private const val BRANCH_ID_PREFIX = 8
}

/**
 * #671 — shell branch/shift context: the viewed branch + operational date, with the
 * clocked-in shift as an explicitly labeled secondary when different.
 */
data class ViewedContext(
    val branchLabel: String,
    val dateLabel: String?,
    val shiftLabel: String?,
)
