package com.companyb.companyapp.app.profile

import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.app.hasDayGrant
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.app.navigation.ShellLayoutPolicy
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse

/**
 * #682 — the Profile Access summary derivation: exact currently available destination
 * names from the same capability predicates as navigation, grouped by branch/global
 * context where known. Destination labels ride [ShellLayoutPolicy.sectionTitle] so the
 * summary names match the shell exactly; no competing authorization map lives here.
 *
 * Precision: Clients requires GLOBAL `EDIT_BRANCH_DATA` (the #653 backend gate), not
 * the navigation route gate's wider any-context check — a branch-scoped grant never
 * implies unrestricted client reads. Finance mirrors navigation exactly (any-context
 * `VIEW_BRANCH_DATA` or the `BRANCH_DAY` relief day grant). The remaining destinations
 * follow drawer-visibility semantics: Base Rates lists on any-context `MANAGE_PRODUCTS`
 * (the screen itself re-checks exact BRANCH scope at the clocked-in branch), and Team
 * & branches lists on any-context `MANAGE_USERS` (the wider route/drawer gate — the
 * backend UserRoutes gate stays GLOBAL-authoritative either way).
 */
data class AccessDestinationGroups(
    val always: List<String>,
    val branch: List<String>,
    val global: List<String>,
)

/** #682 — the unit-tested seam: capability rows in, grouped destination names out. */
fun accessDestinationGroups(caps: List<UserCapabilityResponse>): AccessDestinationGroups =
    AccessDestinationGroups(
        always =
            listOf(
                ShellLayoutPolicy.sectionTitle(Route.Dashboard()),
                ShellLayoutPolicy.sectionTitle(Route.Notifications),
                ShellLayoutPolicy.sectionTitle(Route.AuditLog),
            ),
        branch = branchDestinations(caps),
        global = globalDestinations(caps),
    )

private fun branchDestinations(caps: List<UserCapabilityResponse>): List<String> =
    buildList {
        if (caps.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA)) {
            add(ShellLayoutPolicy.sectionTitle(Route.Inventory))
        }
        if (caps.hasCapabilityAnyContext(CapabilityCodes.VIEW_BRANCH_DATA) ||
            caps.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
        ) {
            add(ShellLayoutPolicy.sectionTitle(Route.Finance))
        }
        if (caps.hasCapabilityAnyContext(CapabilityCodes.SUBMIT_REMITTANCE)) {
            add(ShellLayoutPolicy.sectionTitle(Route.RemittanceList))
        }
        if (caps.hasCapabilityAnyContext(CapabilityCodes.MANAGE_PRODUCTS)) {
            add(ShellLayoutPolicy.sectionTitle(Route.BaseRates))
        }
    }

private fun globalDestinations(caps: List<UserCapabilityResponse>): List<String> =
    buildList {
        // #653-precise: GLOBAL only — a BRANCH grant never lists Clients here.
        if (caps.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.GLOBAL,
                GLOBAL_CAPABILITY_CONTEXT_ID,
            )
        ) {
            add(ShellLayoutPolicy.sectionTitle(Route.Clients))
        }
        if (caps.hasCapabilityAnyContext(CapabilityCodes.MANAGE_USERS)) {
            add(ShellLayoutPolicy.sectionTitle(Route.UserManagement))
        }
        if (caps.hasCapability(
                CapabilityCodes.MANAGE_CATALOG,
                CapabilityContextType.GLOBAL,
                GLOBAL_CAPABILITY_CONTEXT_ID,
            )
        ) {
            add(ShellLayoutPolicy.sectionTitle(Route.ProductCatalog))
        }
        if (caps.hasCapability(
                CapabilityCodes.ASSIGN_DELEGATE,
                CapabilityContextType.GLOBAL,
                GLOBAL_CAPABILITY_CONTEXT_ID,
            )
        ) {
            add(ShellLayoutPolicy.sectionTitle(Route.MedicalMissionDelegates))
        }
    }
