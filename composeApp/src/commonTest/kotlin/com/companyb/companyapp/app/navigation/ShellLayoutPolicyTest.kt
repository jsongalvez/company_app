package com.companyb.companyapp.app.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #671 — focused behavior tests for the adaptive shell contract's pure decisions:
 * width-driven chrome, parent-aware selection, no-op destinations, and viewed/shift
 * context. Composition itself stays in targeted desktop validation; these pin the
 * decisions every shell owner reuses.
 */
class ShellLayoutPolicyTest {
    @Test
    fun `sidebar pins at 1200dp with a 224dp rail`() {
        assertEquals(1200.dp, ShellLayoutPolicy.sidebarBreakpoint)
        assertEquals(224.dp, ShellLayoutPolicy.sidebarWidth)
        assertTrue(ShellLayoutPolicy.useSidebar(1366.dp))
        assertTrue(ShellLayoutPolicy.useSidebar(1200.dp))
        // 1024-wide windows and compact phones fall back to the modal drawer so the
        // working space is never permanently crowded.
        assertFalse(ShellLayoutPolicy.useSidebar(1024.dp))
        assertFalse(ShellLayoutPolicy.useSidebar(390.dp))
    }

    @Test
    fun `detail routes resolve to their parent section`() {
        assertEquals(Route.Dashboard(), ShellLayoutPolicy.parentFor(Route.SessionCreate))
        assertEquals(Route.Dashboard(), ShellLayoutPolicy.parentFor(Route.SessionDetail("s1")))
        assertEquals(Route.Clients, ShellLayoutPolicy.parentFor(Route.ClientDetail("c1")))
        assertEquals(Route.RemittanceList, ShellLayoutPolicy.parentFor(Route.RemittanceDetail("r1")))
        assertEquals(Route.AuditLog, ShellLayoutPolicy.parentFor(Route.AuditLogHistory("t", "r")))
        // Top-level routes map to themselves.
        assertEquals(Route.Finance, ShellLayoutPolicy.parentFor(Route.Finance))
        assertEquals(Route.Dashboard(), ShellLayoutPolicy.parentFor(Route.Dashboard()))
    }

    @Test
    fun `top bar titles the parent section and never blanks`() {
        assertEquals("Sessions", ShellLayoutPolicy.topBarTitle(Route.SessionDetail("s1")))
        assertEquals("Sessions", ShellLayoutPolicy.topBarTitle(Route.Dashboard()))
        assertEquals("Clients", ShellLayoutPolicy.topBarTitle(Route.ClientDetail("c1")))
        assertEquals("Remittance", ShellLayoutPolicy.topBarTitle(Route.RemittanceDetail("r1")))
        assertEquals("Sessions", ShellLayoutPolicy.topBarTitle(null))
    }

    @Test
    fun `selecting the active destination is a no-op`() {
        assertTrue(ShellLayoutPolicy.isSameDestination(Route.Clients, Route.Clients))
        assertTrue(ShellLayoutPolicy.isSameDestination(Route.Dashboard(), Route.Dashboard()))
        assertFalse(ShellLayoutPolicy.isSameDestination(Route.Clients, Route.Inventory))
        // A relief-day deep link is a distinct destination from the shift home, so
        // returning home from it still navigates.
        assertFalse(
            ShellLayoutPolicy.isSameDestination(
                Route.Dashboard(branchId = "b9", date = "2026-09-01"),
                Route.Dashboard(),
            ),
        )
    }

    @Test
    fun `shell shows viewed branch and date with shift secondary only when different`() {
        val home =
            ShellLayoutPolicy.viewedContext(
                current = Route.Dashboard(),
                clockBranchId = "b1",
                clockBranchName = "Main",
                clockDate = "2026-09-08",
            )
        assertEquals("Main", home.branchLabel)
        assertEquals("2026-09-08", home.dateLabel)
        assertNull(home.shiftLabel)

        val reliefDay =
            ShellLayoutPolicy.viewedContext(
                current = Route.Dashboard(branchId = "b9", date = "2026-09-01"),
                clockBranchId = "b1",
                clockBranchName = "Main",
                clockDate = "2026-09-08",
            )
        assertEquals("2026-09-01", reliefDay.dateLabel)
        assertEquals("Shift: Main · 2026-09-08", reliefDay.shiftLabel)

        val sameBranchDeepLink =
            ShellLayoutPolicy.viewedContext(
                current = Route.Dashboard(branchId = "b1", date = "2026-09-08"),
                clockBranchId = "b1",
                clockBranchName = "Main",
                clockDate = "2026-09-08",
            )
        assertEquals("Main", sameBranchDeepLink.branchLabel)
        assertNull(sameBranchDeepLink.shiftLabel)
    }

    @Test
    fun `same branch different date still names the shift`() {
        val viewed =
            ShellLayoutPolicy.viewedContext(
                current = Route.Dashboard(branchId = "b1", date = "2026-09-01"),
                clockBranchId = "b1",
                clockBranchName = "Main",
                clockDate = "2026-09-08",
            )
        assertEquals("Main", viewed.branchLabel)
        assertEquals("2026-09-01", viewed.dateLabel)
        assertEquals("Shift: Main · 2026-09-08", viewed.shiftLabel)
    }
}
