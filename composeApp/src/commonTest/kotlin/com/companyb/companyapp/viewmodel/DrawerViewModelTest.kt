package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.CapabilityCodes
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

    @Test
    fun allCapabilityCodesVisible_allItemsVisible() {
        SessionState.setCapabilities(
            setOf(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.ASSIGN_COMPENSATION,
                CapabilityCodes.SUBMIT_REMITTANCE,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.MANAGE_USERS,
            ),
        )

        val vm = DrawerViewModel()
        val items = vm.uiState.value.drawerItems

        assertEquals(expected = 8, actual = items.size)
        assertTrue(items.all { it.visible }, "All 8 items should be visible when all capabilities are set")
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
        assertFalse(visibleItems.any { it.route is Route.Reports })
    }

    @Test
    fun partialCapabilities_filterMatchesCapabilityCode() {
        // Set only SUBMIT_REMITTANCE — should reveal only RemittanceList (+ always-visible items)
        SessionState.setCapabilities(setOf(CapabilityCodes.SUBMIT_REMITTANCE))

        val vm = DrawerViewModel()
        val visibleLabels =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }

        assertTrue("Remittance" in visibleLabels)
        assertFalse("Clients" in visibleLabels)
        assertFalse("Inventory" in visibleLabels)
        assertFalse("Finance" in visibleLabels)
        assertFalse("Reports" in visibleLabels)
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

        // Set VIEW_BRANCH_DATA — Reports should now be visible
        SessionState.setCapabilities(setOf(CapabilityCodes.VIEW_BRANCH_DATA))

        val after =
            vm.uiState.value.drawerItems
                .filter { it.visible }
                .map { it.label }
        assertTrue("Reports" in after)
        assertEquals(expected = 3, actual = after.size)
    }
}
