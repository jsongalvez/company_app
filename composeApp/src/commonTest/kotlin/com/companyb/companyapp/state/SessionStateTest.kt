package com.companyb.companyapp.state

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #94-grad / #156 — SessionState writer integration: setUser / setCapabilities / setSelectedBranch /
 * setExpiredNotice populate the flows, clear() resets the session surface (but not the
 * expired notice — that's consumed by LoginScreen, not part of the session). Capabilities
 * are stored as the FULL row list (#156 — the ADR-0021 two-slice client filters are gone).
 */
class SessionStateTest {
    private val user =
        MeResponse(
            id = "u1",
            username = "dev",
            status = com.companyb.companyapp.domain.UserStatus.ACTIVE,
            createdAt = "2026-08-10T00:00:00+08:00",
        )

    private val rows =
        listOf(
            UserCapabilityResponse(
                "MANAGE_USERS",
                com.companyb.companyapp.domain.CapabilityContextType.GLOBAL,
                "00000000-0000-0000-0000-000000000000",
                com.companyb.companyapp.domain.CapabilitySourceType.ROLE,
            ),
            UserCapabilityResponse(
                "EDIT_BRANCH_DATA",
                com.companyb.companyapp.domain.CapabilityContextType.BRANCH,
                "b1",
                com.companyb.companyapp.domain.CapabilitySourceType.MANUAL_OVERRIDE,
            ),
            UserCapabilityResponse(
                "EDIT_BRANCH_DATA",
                com.companyb.companyapp.domain.CapabilityContextType.BRANCH_DAY,
                "d1",
                com.companyb.companyapp.domain.CapabilitySourceType.RELIEF_ACCESS,
            ),
        )

    @Test
    fun setters_populate_all_surfaces() {
        SessionState.clear()
        SessionState.setUser(user)
        SessionState.setCapabilities(rows)
        SessionState.setSelectedBranch("b1", "Main Branch")
        SessionState.setClockState("a1", "d1")

        assertEquals(user, SessionState.currentUser.value)
        assertEquals(rows, SessionState.capabilities.value)
        assertEquals("b1", SessionState.selectedBranchId.value)
        assertEquals("Main Branch", SessionState.selectedBranchName.value)
        assertEquals("a1", SessionState.attendanceId.value)
        assertEquals("d1", SessionState.branchDayId.value)
    }

    // #147 (Q3) — clock-out transition: user stays logged in, branch + caps reset to empty,
    // attendance slots cleared; clear() (logout/401) resets everything.
    @Test
    fun clear_clock_state_keeps_user_but_resets_branch_caps_and_attendance() {
        SessionState.setUser(user)
        SessionState.setCapabilities(rows)
        SessionState.setSelectedBranch("b1", "Main Branch")
        SessionState.setClockState("a1", "d1")

        SessionState.clearClockState()

        assertEquals(user, SessionState.currentUser.value, "clock-out must NOT log the user out")
        assertNull(SessionState.selectedBranchId.value)
        assertNull(SessionState.selectedBranchName.value)
        assertEquals(emptyList<UserCapabilityResponse>(), SessionState.capabilities.value)
        assertNull(SessionState.attendanceId.value)
        assertNull(SessionState.branchDayId.value)
    }

    @Test
    fun clear_resets_session_but_not_expired_notice() {
        SessionState.setUser(user)
        SessionState.setCapabilities(rows)
        SessionState.setSelectedBranch("b1", "Main Branch")
        SessionState.setClockState("a1", "d1")
        SessionState.setExpiredNotice(true)

        SessionState.clear()

        assertNull(SessionState.currentUser.value)
        assertEquals(emptyList<UserCapabilityResponse>(), SessionState.capabilities.value)
        assertNull(SessionState.selectedBranchId.value)
        assertNull(SessionState.selectedBranchName.value)
        assertNull(SessionState.attendanceId.value)
        assertNull(SessionState.branchDayId.value)
        // The notice is consumed by LoginScreen, not the session surface.
        assertTrue(SessionState.expiredNotice.value)
        SessionState.setExpiredNotice(false)
    }
}
