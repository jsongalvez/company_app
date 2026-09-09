package com.companyb.companyapp.app.profile

import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #682 — the Access summary derivation: exact destination names from navigation's
 * capability predicates, grouped by context, with #653-precise client gating.
 */
class ProfileAccessSummaryTest {
    private fun cap(
        code: String,
        type: CapabilityContextType,
        id: String = "b1",
    ): UserCapabilityResponse =
        UserCapabilityResponse(
            capabilityCode = code,
            contextType = type,
            contextId = id,
            sourceType = CapabilitySourceType.ROLE,
        )

    @Test
    fun empty_capabilities_lists_only_always_available_destinations() {
        val groups = accessDestinationGroups(emptyList())

        assertEquals(listOf("Sessions", "Notifications", "Audit Log"), groups.always)
        assertTrue(groups.branch.isEmpty())
        assertTrue(groups.global.isEmpty())
    }

    @Test
    fun branch_edit_grant_lists_inventory_but_not_clients() {
        // #653 precision: a BRANCH-scoped EDIT grant never implies unrestricted client reads.
        val groups =
            accessDestinationGroups(listOf(cap(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH)))

        assertTrue(groups.branch.contains("Inventory"))
        assertFalse(groups.global.contains("Clients"))
    }

    @Test
    fun global_edit_grant_lists_clients_and_inventory() {
        val groups =
            accessDestinationGroups(
                listOf(
                    cap(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID),
                ),
            )

        assertTrue(groups.global.contains("Clients"))
        assertTrue(groups.branch.contains("Inventory"))
    }

    @Test
    fun finance_lists_on_view_grant_or_relief_day_grant() {
        val viewGroups =
            accessDestinationGroups(listOf(cap(CapabilityCodes.VIEW_BRANCH_DATA, CapabilityContextType.BRANCH)))
        assertTrue(viewGroups.branch.contains("Finance & Reports"))

        val dayGroups =
            accessDestinationGroups(
                listOf(cap(CapabilityCodes.EDIT_BRANCH_DATA, CapabilityContextType.BRANCH_DAY, "day1")),
            )
        assertTrue(dayGroups.branch.contains("Finance & Reports"))
        // The day grant alone still implies no unrestricted client reads.
        assertFalse(dayGroups.global.contains("Clients"))
    }

    @Test
    fun remittance_team_and_rates_follow_navigation_predicates() {
        val groups =
            accessDestinationGroups(
                listOf(
                    cap(CapabilityCodes.SUBMIT_REMITTANCE, CapabilityContextType.BRANCH),
                    cap(CapabilityCodes.MANAGE_USERS, CapabilityContextType.BRANCH),
                    cap(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH),
                ),
            )

        assertTrue(groups.branch.contains("Remittance"))
        assertTrue(groups.branch.contains("Base Rates"))
        assertTrue(groups.global.contains("Team & branches"))
    }

    @Test
    fun catalog_and_delegates_require_global_scope() {
        val branchScoped =
            accessDestinationGroups(
                listOf(
                    cap(CapabilityCodes.MANAGE_CATALOG, CapabilityContextType.BRANCH),
                    cap(CapabilityCodes.ASSIGN_DELEGATE, CapabilityContextType.BRANCH),
                ),
            )
        assertFalse(branchScoped.global.contains("Product Catalog"))
        assertFalse(branchScoped.global.contains("Mission delegates"))

        val globalScoped =
            accessDestinationGroups(
                listOf(
                    cap(CapabilityCodes.MANAGE_CATALOG, CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID),
                    cap(CapabilityCodes.ASSIGN_DELEGATE, CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID),
                ),
            )
        assertTrue(globalScoped.global.contains("Product Catalog"))
        assertTrue(globalScoped.global.contains("Mission delegates"))
    }
}
