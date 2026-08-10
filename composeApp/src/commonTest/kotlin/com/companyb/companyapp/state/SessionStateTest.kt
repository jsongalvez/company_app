package com.companyb.companyapp.state

import com.companyb.companyapp.dto.MeResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #94-grad — SessionState writer integration: setUser / setCapabilities / setSelectedBranch /
 * setExpiredNotice populate the flows, clear() resets the session surface (but not the
 * expired notice — that's consumed by LoginScreen, not part of the session).
 */
class SessionStateTest {
    private val user =
        MeResponse(id = "u1", username = "dev", status = "ACTIVE", createdAt = "2026-08-10T00:00:00+08:00")

    @Test
    fun setters_populate_all_surfaces() {
        SessionState.clear()
        SessionState.setUser(user)
        SessionState.setCapabilities(setOf("MANAGE_USERS"))
        SessionState.setSelectedBranch("b1", "Main Branch")

        assertEquals(user, SessionState.currentUser.value)
        assertEquals(setOf("MANAGE_USERS"), SessionState.capabilities.value)
        assertEquals("b1", SessionState.selectedBranchId.value)
        assertEquals("Main Branch", SessionState.selectedBranchName.value)
    }

    @Test
    fun clear_resets_session_but_not_expired_notice() {
        SessionState.setUser(user)
        SessionState.setCapabilities(setOf("MANAGE_USERS"))
        SessionState.setSelectedBranch("b1", "Main Branch")
        SessionState.setExpiredNotice(true)

        SessionState.clear()

        assertNull(SessionState.currentUser.value)
        assertEquals(emptySet<String>(), SessionState.capabilities.value)
        assertNull(SessionState.selectedBranchId.value)
        assertNull(SessionState.selectedBranchName.value)
        // The notice is consumed by LoginScreen, not the session surface.
        assertTrue(SessionState.expiredNotice.value)
        SessionState.setExpiredNotice(false)
    }
}
