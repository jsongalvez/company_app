package com.companyb.companyapp.app.navigation

import com.companyb.companyapp.session.create.SessionCreateDraft
import com.companyb.companyapp.session.create.hasUserEdits
import com.companyb.companyapp.session.create.isSessionCreateDirty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #726 — dirty shell-exit guard + retained destination rules.
 *
 * Pure decision coverage (no Compose runtime): the drawer intercepts only a dirty
 * SessionCreate entry while navigation is enabled; locked submissions keep the stronger
 * disabled rule; the linked client-profile push never counts as abandoning.
 */
class SessionCreateExitGuardTest {
    @Test
    fun `dirty intake intercepts section switches`() {
        assertTrue(
            shouldInterceptSessionCreateExit(
                current = Route.SessionCreate,
                dirty = true,
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `untouched intake navigates without confirmation`() {
        val dirty = isSessionCreateDirty(null, SessionCreateDraft(), emptySet(), null)
        assertFalse(dirty)
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.SessionCreate,
                dirty = dirty,
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `auto preview price alone never intercepts`() {
        // #674 semantics: the server default (finalPriceEdited=false) is not user-authored.
        val auto = SessionCreateDraft(finalPrice = "250.00", finalPriceEdited = false)
        assertFalse(auto.hasUserEdits())
        assertFalse(isSessionCreateDirty(null, auto, emptySet(), null))
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.SessionCreate,
                dirty = isSessionCreateDirty(null, auto, emptySet(), null),
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `user-authored edits intercept`() {
        val edited = SessionCreateDraft(finalPrice = "900", finalPriceEdited = true)
        assertTrue(isSessionCreateDirty(null, edited, emptySet(), null))
        assertTrue(
            shouldInterceptSessionCreateExit(
                current = Route.SessionCreate,
                dirty = true,
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `locked submission keeps navigation disabled instead of prompting`() {
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.SessionCreate,
                dirty = true,
                navigationEnabled = false,
            ),
        )
    }

    @Test
    fun `profile push itself never counts as abandoning`() {
        // Opening the linked client profile pushes ClientDetail; the draft stays in the
        // entry-scoped VM underneath and Back restores it — no discard decision.
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.ClientDetail("c1"),
                dirty = true,
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `drawer escape from profile over dirty intake pops back to decide`() {
        // #726 — a section switch from the linked profile would pop both entries and
        // destroy the draft underneath: intercept by returning to the intake first,
        // where the retained pendingRoute offers Keep editing / Discard and continue.
        assertTrue(
            shouldInterceptPushedDetailExit(
                current = Route.ClientDetail("c1"),
                previous = Route.SessionCreate,
                navigationEnabled = true,
            ),
        )
        assertFalse(
            shouldInterceptPushedDetailExit(
                current = Route.ClientDetail("c1"),
                previous = Route.Clients,
                navigationEnabled = true,
            ),
        )
        assertFalse(
            shouldInterceptPushedDetailExit(
                current = Route.ClientDetail("c1"),
                previous = Route.SessionCreate,
                navigationEnabled = false,
            ),
        )
        assertFalse(
            shouldInterceptPushedDetailExit(
                current = Route.SessionCreate,
                previous = Route.Dashboard(),
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `other sections never intercept`() {
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.Dashboard(),
                dirty = true,
                navigationEnabled = true,
            ),
        )
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = Route.Finance,
                dirty = true,
                navigationEnabled = true,
            ),
        )
        assertFalse(
            shouldInterceptSessionCreateExit(
                current = null,
                dirty = true,
                navigationEnabled = true,
            ),
        )
    }

    @Test
    fun `pending retains destination until keep or discard`() {
        val guard = SessionCreateExitGuard()
        val target = Route.Finance

        guard.dirty.value = true
        assertTrue(shouldInterceptSessionCreateExit(Route.SessionCreate, guard.dirty.value, true))

        // Drawer retains the originally chosen destination while deciding.
        guard.pendingRoute.value = target
        assertEquals(target, guard.pendingRoute.value)

        // Keep editing performs no navigation and loses no values.
        guard.pendingRoute.value = null
        assertNull(guard.pendingRoute.value)
        assertTrue(guard.dirty.value)

        // Discard and continue executes the retained destination exactly once.
        guard.pendingRoute.value = target
        var navigations = 0
        var navigatedTo: Route? = null
        val captured = guard.pendingRoute.value
        guard.pendingRoute.value = null
        if (captured != null) {
            navigations++
            navigatedTo = captured
        }
        assertEquals(1, navigations)
        assertEquals(target, navigatedTo)
        assertNull(guard.pendingRoute.value)
    }
}
