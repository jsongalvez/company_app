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
}
