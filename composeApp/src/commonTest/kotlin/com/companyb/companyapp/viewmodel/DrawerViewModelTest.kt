package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.navigation.Route
import com.companyb.companyapp.state.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        SessionState.clear()
    }

    @AfterTest
    fun teardown() {
        SessionState.clear()
        Dispatchers.resetMain()
    }

    // #156 — drawer items gate on any-context membership, so the seeded context is irrelevant
    // to visibility; BRANCH rows are used for realism.
    private fun row(code: String): UserCapabilityResponse = UserCapabilityResponse(code, "BRANCH", "b1", "DIRECT")

    @Test
    fun allCapabilityCodesVisible_allItemsVisible() {
        SessionState.setCapabilities(
            listOf(
                row(CapabilityCodes.EDIT_BRANCH_DATA),
                row(CapabilityCodes.ASSIGN_COMPENSATION),
                row(CapabilityCodes.SUBMIT_REMITTANCE),
                row(CapabilityCodes.VIEW_BRANCH_DATA),
                row(CapabilityCodes.MANAGE_USERS),
            ),
        )

        val vm = DrawerViewModel()
        val items = vm.uiState.value.drawerItems

        // #105 D1 — Finance & Reports collapsed into one item (7 total, was 8 with separate Reports).
        assertEquals(expected = 7, actual = items.size)
        assertTrue(items.all { it.visible }, "All 7 items should be visible when all capabilities are set")
    }

    @Test
    fun emptyCapabilities_onlyAlwaysVisibleItemsShow() {
        // Empty capabilities — only Notifications + AuditLog (capabilityCode == null) visible per ticket 11
        val vm = DrawerViewModel()
        val visibleItems =
            vm.uiState.value.drawerItems
                .filter { it.visible }

        assertEquals(expected = 2, actual = visibleItems.size)
        assertTrue(visibleItems.any { it.route is Route.Notifications })
        assertTrue(visibleItems.any { it.route is Route.AuditLog })
        assertFalse(visibleItems.any { it.route is Route.Clients })
        assertFalse(visibleItems.any { it.route is Route.UserManagement })
    }

    @Test
    fun partialCapabilities_filterMatchesCapabilityCode() {
        // Set only SUBMIT_REMITTANCE — should reveal only RemittanceList (+ always-visible items)
        SessionState.setCapabilities(listOf(row(CapabilityCodes.SUBMIT_REMITTANCE)))

        val vm = DrawerViewModel()
        val visibleLabels =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }

        assertTrue("Remittance" in visibleLabels)
        assertFalse("Clients" in visibleLabels)
        assertFalse("Inventory" in visibleLabels)
        assertFalse("Finance & Reports" in visibleLabels)
        assertFalse("User Management" in visibleLabels)
        assertTrue("Notifications" in visibleLabels)
        assertTrue("Audit Log" in visibleLabels)
    }

    @Test
    fun capabilityChangesAfterConstruction_uiStateUpdates() {
        val vm = DrawerViewModel()

        // Initially no caps — only 2 visible (Notifications + AuditLog)
        assertEquals(
            expected = 2,
            actual =
                vm.uiState.value.drawerItems
                    .count { it.visible },
        )

        // #105 D1 — VIEW_BRANCH_DATA reveals the merged Finance & Reports item
        SessionState.setCapabilities(listOf(row(CapabilityCodes.VIEW_BRANCH_DATA)))

        val after =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }
        assertTrue("Finance & Reports" in after)
        assertEquals(expected = 3, actual = after.size)
    }

    @Test
    fun mergedFinanceItem_isGatedOnViewBranchData() {
        // The merge collapses the ASSIGN_COMPENSATION-gated "Finance" and VIEW_BRANCH_DATA-gated
        // "Reports" into one item gated on the WIDEST capability: ASSIGN_COMPENSATION alone must
        // NOT reveal it (Accountant holds VIEW_BRANCH_DATA only, #105 F1).
        SessionState.setCapabilities(listOf(row(CapabilityCodes.ASSIGN_COMPENSATION)))

        val vm = DrawerViewModel()
        val labels =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }

        assertFalse("Finance & Reports" in labels)
    }
}
