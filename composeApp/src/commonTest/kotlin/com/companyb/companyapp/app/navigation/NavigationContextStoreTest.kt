package com.companyb.companyapp.app.navigation

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #671 — section working context: per-section anchors survive section switches, stay
 * isolated across users and branches, and leave on clear (logout/access loss).
 */
class NavigationContextStoreTest {
    @AfterTest
    fun teardown() {
        NavigationContextStore.clear()
    }

    @Test
    fun `retained selection survives a section roundtrip`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1")

        // Leaving for Finance and returning restores the Sessions anchor.
        val restored = NavigationContextStore.retained("u1", "b1", Route.Dashboard())
        assertEquals("s1", restored?.selectedId)
    }

    @Test
    fun `detail routes share their parent section slot`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1")

        // Pushing the detail does not fork the context; returning reads the same slot.
        assertEquals("sessions", NavigationContextStore.sectionKey(Route.SessionDetail("s1")))
        assertEquals(
            "s1",
            NavigationContextStore.retained("u1", "b1", Route.SessionDetail("s1"))?.selectedId,
        )
    }

    @Test
    fun `branch switch never surfaces old-branch anchors`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1")

        // A different viewed branch is a fresh key: no stale selection leaks across.
        assertNull(NavigationContextStore.retained("u1", "b2", Route.Dashboard()))
        // And a different user is isolated too.
        assertNull(NavigationContextStore.retained("u2", "b1", Route.Dashboard()))
    }

    @Test
    fun `null legs leave stored values intact`() {
        NavigationContextStore.retain("u1", "b1", Route.Clients, selectedId = "c1", scrollAnchorId = "c1")

        NavigationContextStore.retain("u1", "b1", Route.Clients, selectedId = null)

        val restored = NavigationContextStore.retained("u1", "b1", Route.Clients)
        assertEquals("c1", restored?.selectedId)
        assertEquals("c1", restored?.scrollAnchorId)
    }

    @Test
    fun `clear drops every retained anchor`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1")
        NavigationContextStore.retain("u1", "b1", Route.RemittanceList, selectedId = "r1")

        NavigationContextStore.clear()

        assertNull(NavigationContextStore.retained("u1", "b1", Route.Dashboard()))
        assertNull(NavigationContextStore.retained("u1", "b1", Route.RemittanceList))
    }

    @Test
    fun `forget drops one section only`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1")
        NavigationContextStore.retain("u1", "b1", Route.Clients, selectedId = "c1")

        NavigationContextStore.forget("u1", "b1", Route.Dashboard())

        assertNull(NavigationContextStore.retained("u1", "b1", Route.Dashboard()))
        assertEquals("c1", NavigationContextStore.retained("u1", "b1", Route.Clients)?.selectedId)
    }

    @Test
    fun `null user or branch legs never read or write`() {
        // The transient clock-out window (clock null, route still Dashboard) must not
        // leave orphan entries under a "?|?" key.
        NavigationContextStore.retain("u1", null, Route.Dashboard(), selectedId = "s1")
        NavigationContextStore.retain(null, "b1", Route.Dashboard(), selectedId = "s1")

        assertNull(NavigationContextStore.retained("u1", null, Route.Dashboard()))
        assertNull(NavigationContextStore.retained(null, "b1", Route.Dashboard()))
        assertNull(NavigationContextStore.retained("u1", "b1", Route.Dashboard()))
    }

    @Test
    fun `retained tab survives a section roundtrip`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1", tab = "COMPLETED")

        val restored = NavigationContextStore.retained("u1", "b1", Route.Dashboard())
        assertEquals("COMPLETED", restored?.tab)
        assertEquals("s1", restored?.selectedId)
    }

    @Test
    fun `null tab leaves the stored tab intact`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1", tab = "PENDING")

        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s2")

        val restored = NavigationContextStore.retained("u1", "b1", Route.Dashboard())
        assertEquals("PENDING", restored?.tab)
        assertEquals("s2", restored?.selectedId)
    }

    @Test
    fun `tab is keyed per branch like the anchors`() {
        NavigationContextStore.retain("u1", "b1", Route.Dashboard(), selectedId = "s1", tab = "COMPLETED")

        assertNull(NavigationContextStore.retained("u1", "b2", Route.Dashboard()))
    }

    @Test
    fun `inventory query and filter survive a section roundtrip`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "serum",
            lowStockOnly = true,
            scrollAnchorId = "card-9",
        )

        val restored = NavigationContextStore.retained("u1", "b1", Route.Inventory)
        assertEquals("serum", restored?.query)
        assertEquals(true, restored?.lowStockOnly)
        assertEquals("card-9", restored?.scrollAnchorId)
    }

    @Test
    fun `explicit inventory clear overwrites and never resurrects`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "serum",
            lowStockOnly = true,
        )

        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "",
            lowStockOnly = false,
        )

        val restored = NavigationContextStore.retained("u1", "b1", Route.Inventory)
        assertEquals("", restored?.query)
        assertEquals(false, restored?.lowStockOnly)
    }

    @Test
    fun `null inventory legs leave stored query and filter intact`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "serum",
            lowStockOnly = true,
            scrollAnchorId = "card-9",
        )

        NavigationContextStore.retain("u1", "b1", Route.Inventory, selectedId = null)

        val restored = NavigationContextStore.retained("u1", "b1", Route.Inventory)
        assertEquals("serum", restored?.query)
        assertEquals(true, restored?.lowStockOnly)
        assertEquals("card-9", restored?.scrollAnchorId)
    }

    @Test
    fun `inventory context is keyed per user and branch`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "serum",
            lowStockOnly = true,
        )

        assertNull(NavigationContextStore.retained("u1", "b2", Route.Inventory))
        assertNull(NavigationContextStore.retained("u2", "b1", Route.Inventory))
    }

    @Test
    fun `inventory context never shares a slot with sessions`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Inventory,
            selectedId = null,
            query = "serum",
        )

        assertNull(NavigationContextStore.retained("u1", "b1", Route.Dashboard())?.query)
    }

    @Test
    fun `finance applied scope survives a section roundtrip`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = "day-9",
            scrollAnchorId = "day-7",
            financeBranchId = "branch-b",
            financeMode = "MONTHLY",
            financeMonth = "2026-08",
            financeRangeFrom = "2026-08-01",
            financeRangeTo = "2026-08-10",
            financeJumpMonth = "2026-07",
            financeShowCards = true,
        )

        val restored = NavigationContextStore.retained("u1", "b1", Route.Finance)
        assertEquals("branch-b", restored?.financeBranchId)
        assertEquals("MONTHLY", restored?.financeMode)
        assertEquals("2026-08", restored?.financeMonth)
        assertEquals("2026-08-01", restored?.financeRangeFrom)
        assertEquals("2026-08-10", restored?.financeRangeTo)
        assertEquals("2026-07", restored?.financeJumpMonth)
        assertEquals(true, restored?.financeShowCards)
        assertEquals("day-9", restored?.selectedId)
        assertEquals("day-7", restored?.scrollAnchorId)
    }

    @Test
    fun `explicit finance clear overwrites and never resurrects`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = "day-9",
            financeBranchId = "branch-b",
            financeMode = "DATE_RANGE",
            financeRangeFrom = "2026-08-01",
            financeRangeTo = "2026-08-10",
            financeJumpMonth = "2026-07",
            financeShowCards = true,
        )

        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = "",
            financeRangeFrom = "",
            financeRangeTo = "",
            financeJumpMonth = "",
            financeShowCards = false,
        )

        val restored = NavigationContextStore.retained("u1", "b1", Route.Finance)
        assertEquals("", restored?.selectedId)
        assertEquals("", restored?.financeRangeFrom)
        assertEquals("", restored?.financeRangeTo)
        assertEquals("", restored?.financeJumpMonth)
        assertEquals(false, restored?.financeShowCards)
        assertEquals("branch-b", restored?.financeBranchId)
    }

    @Test
    fun `null finance legs leave stored scope intact`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = "day-9",
            scrollAnchorId = "day-7",
            financeBranchId = "branch-b",
            financeMode = "MONTHLY",
            financeMonth = "2026-08",
            financeShowCards = true,
        )

        NavigationContextStore.retain("u1", "b1", Route.Finance, selectedId = null)

        val restored = NavigationContextStore.retained("u1", "b1", Route.Finance)
        assertEquals("day-9", restored?.selectedId)
        assertEquals("day-7", restored?.scrollAnchorId)
        assertEquals("branch-b", restored?.financeBranchId)
        assertEquals("MONTHLY", restored?.financeMode)
        assertEquals("2026-08", restored?.financeMonth)
        assertEquals(true, restored?.financeShowCards)
    }

    @Test
    fun `finance context is keyed per user and branch`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = "day-9",
            financeBranchId = "branch-b",
            financeMode = "MONTHLY",
        )

        assertNull(NavigationContextStore.retained("u1", "b2", Route.Finance))
        assertNull(NavigationContextStore.retained("u2", "b1", Route.Finance))
    }

    @Test
    fun `finance context never shares a slot with inventory`() {
        NavigationContextStore.retain(
            "u1",
            "b1",
            Route.Finance,
            selectedId = null,
            financeBranchId = "branch-b",
            financeMode = "MONTHLY",
        )

        assertNull(NavigationContextStore.retained("u1", "b1", Route.Inventory)?.financeBranchId)
        assertNull(NavigationContextStore.retained("u1", "b1", Route.Finance)?.query)
    }
}
