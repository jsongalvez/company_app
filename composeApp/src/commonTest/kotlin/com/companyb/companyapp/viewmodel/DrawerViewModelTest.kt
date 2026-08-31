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
    private fun row(code: String): UserCapabilityResponse =
        UserCapabilityResponse(
            code,
            com.companyb.companyapp.domain.CapabilityContextType.BRANCH,
            "b1",
            com.companyb.companyapp.domain.CapabilitySourceType.MANUAL_OVERRIDE,
        )

    private fun globalRow(code: String): UserCapabilityResponse =
        UserCapabilityResponse(
            code,
            com.companyb.companyapp.domain.CapabilityContextType.GLOBAL,
            com.companyb.companyapp.state.GLOBAL_CAPABILITY_CONTEXT_ID,
            com.companyb.companyapp.domain.CapabilitySourceType.MANUAL_OVERRIDE,
        )

    @Test
    fun allCapabilityCodesVisible_allItemsVisible() {
        SessionState.setCapabilities(
            listOf(
                row(CapabilityCodes.EDIT_BRANCH_DATA),
                row(CapabilityCodes.ASSIGN_COMPENSATION),
                row(CapabilityCodes.SUBMIT_REMITTANCE),
                row(CapabilityCodes.VIEW_BRANCH_DATA),
                row(CapabilityCodes.MANAGE_USERS),
                globalRow(CapabilityCodes.ASSIGN_DELEGATE),
                // #418 — the Base Rates item's gate.
                row(CapabilityCodes.MANAGE_PRODUCTS),
            ),
        )

        val vm = DrawerViewModel()
        val items = vm.uiState.value.drawerItems

        // #105 D1 — Finance & Reports collapsed into one item (8 total, was 8 with separate Reports);
        // #381 — Profile added as the always-visible self surface.
        // #389 — Dashboard added as the always-visible home surface (9 total).
        // #418 — Base Rates added as a MANAGE_PRODUCTS-gated item; #438 adds Mission Delegates.
        assertEquals(expected = 11, actual = items.size)
        assertTrue(items.all { it.visible }, "All 11 items should be visible when all capabilities are set")
    }

    @Test
    fun emptyCapabilities_onlyAlwaysVisibleItemsShow() {
        // Empty capabilities — only Dashboard + Notifications + AuditLog + Profile (capabilityCode == null)
        val vm = DrawerViewModel()
        val visibleItems =
            vm.uiState.value.drawerItems
                .filter { it.visible }

        assertEquals(expected = 4, actual = visibleItems.size)
        assertTrue(visibleItems.any { it.route is Route.Dashboard })
        assertTrue(visibleItems.any { it.route is Route.Notifications })
        assertTrue(visibleItems.any { it.route is Route.AuditLog })
        // #381 — the own profile is reachable by every authenticated user.
        assertTrue(visibleItems.any { it.route is Route.Profile })
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
        assertTrue("Dashboard" in visibleLabels)
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

        // Initially no caps — only 4 visible (Dashboard + Notifications + AuditLog + Profile)
        assertEquals(
            expected = 4,
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
        assertEquals(expected = 5, actual = after.size)
    }

    @Test
    fun dashboardItem_isAlwaysVisibleAndFirst() {
        // #389 — the clocked-in home is reachable from every post-clock-in screen; it leads
        // the drawer and gates on nothing (capabilityCode == null → visible at zero caps).
        val vm = DrawerViewModel()
        val items = vm.uiState.value.drawerItems

        assertEquals(expected = "Dashboard", actual = items.first().label)
        assertEquals(expected = Route.Dashboard(), actual = items.first().route)
        assertTrue(items.first().visible)

        SessionState.setCapabilities(emptyList())
        assertTrue(
            vm.uiState.value.drawerItems
                .first()
                .visible,
            "Dashboard stays visible with zero capabilities",
        )
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

    @Test
    fun dayGrantHolder_seesFinanceItem() {
        // #158 — a BRANCH_DAY relief grant (EDIT_BRANCH_DATA at BRANCH_DAY context) opens the
        // Finance day-scoped surface even with no VIEW_BRANCH_DATA anywhere.
        SessionState.setCapabilities(
            listOf(
                UserCapabilityResponse(
                    capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                    contextType = com.companyb.companyapp.domain.CapabilityContextType.BRANCH_DAY,
                    contextId = "d1",
                    sourceType = com.companyb.companyapp.domain.CapabilitySourceType.MANUAL_OVERRIDE,
                ),
            ),
        )

        val vm = DrawerViewModel()
        val labels =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }

        assertTrue("Finance & Reports" in labels)
    }

    @Test
    fun branchScopedEditHolder_withoutView_doesNotSeeFinance() {
        // #158 — a plain BRANCH EDIT_BRANCH_DATA grant (no VIEW, no day grant) must NOT reveal
        // Finance: the read surface is VIEW-gated, the day leg is BRANCH_DAY-only.
        SessionState.setCapabilities(listOf(row(CapabilityCodes.EDIT_BRANCH_DATA)))

        val vm = DrawerViewModel()
        val labels =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }

        assertFalse("Finance & Reports" in labels)
    }

    @Test
    fun missionDelegates_requiresGlobalAssignDelegate() {
        SessionState.setCapabilities(listOf(row(CapabilityCodes.ASSIGN_DELEGATE)))
        val branchScopedLabels =
            DrawerViewModel()
                .uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }
        assertFalse("Mission Delegates" in branchScopedLabels)

        SessionState.setCapabilities(listOf(globalRow(CapabilityCodes.ASSIGN_DELEGATE)))
        val globalLabels =
            DrawerViewModel()
                .uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }
        assertTrue("Mission Delegates" in globalLabels)
    }
}
