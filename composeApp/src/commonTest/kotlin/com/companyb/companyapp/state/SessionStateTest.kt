package com.companyb.companyapp.state

import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #498 — atomic session snapshot: one StateFlow<SessionSnapshot> owns user + full
 * capability rows + nullable clock context. Bootstrap publishes user+caps together;
 * clock-in publishes the complete clock context at once; clock-out clears clock+caps
 * together; clear() resets the session surface (but not the expired notice).
 */
class SessionStateTest {
    private val user =
        MeResponse(
            id = "u1",
            username = "dev",
            displayName = "Dev",
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

    private val clockIn =
        ClockInResponse(
            id = "a1",
            branchDayId = "d1",
            userId = "u1",
            markedBy = "u1",
            clockIn = "2026-08-10T08:00:00+08:00",
            clockOut = null,
            isRelief = false,
        )

    @Test
    fun clocked_in_snapshot_holds_complete_context() {
        SessionState.clear()
        SessionState.setBootstrapState(user, emptyList())
        SessionState.setClockedIn("b1", "Main Branch", clockIn)
        SessionState.setCapabilities(rows)

        val snap = SessionState.snapshot.value
        assertEquals(user, snap.user)
        assertEquals(rows, snap.capabilities)
        assertEquals("b1", snap.clock?.branchId)
        assertEquals("Main Branch", snap.clock?.branchName)
        assertEquals("a1", snap.clock?.attendanceId)
        assertEquals("d1", snap.clock?.branchDayId)
        // #351 — the relief flag rides the same clock write; default false.
        assertEquals(false, snap.clock?.isRelief)
    }

    @Test
    fun bootstrap_state_publishes_user_and_caps_together() {
        SessionState.clear()

        SessionState.setBootstrapState(user, rows)

        val snap = SessionState.snapshot.value
        assertEquals(user, snap.user)
        assertEquals(rows, snap.capabilities)
        assertNull(snap.clock)
    }

    // #147 (Q3) — clock-out transition: user stays logged in, clock + caps clear together;
    // clear() (logout/401) resets everything.
    @Test
    fun clear_clock_state_keeps_user_but_resets_clock_and_caps() {
        SessionState.setBootstrapState(user, rows)
        SessionState.setClockedIn("b1", "Main Branch", clockIn)

        SessionState.clearClockState()

        val snap = SessionState.snapshot.value
        assertEquals(user, snap.user, "clock-out must NOT log the user out")
        assertNull(snap.clock)
        assertEquals(emptyList<UserCapabilityResponse>(), snap.capabilities)
    }

    @Test
    fun clear_resets_session_but_not_expired_notice() {
        SessionState.setBootstrapState(user, rows)
        SessionState.setClockedIn("b1", "Main Branch", clockIn)
        SessionState.setExpiredNotice(true)

        SessionState.clear()

        val snap = SessionState.snapshot.value
        assertNull(snap.user)
        assertEquals(emptyList<UserCapabilityResponse>(), snap.capabilities)
        assertNull(snap.clock)
        // The notice is consumed by LoginScreen, not the session surface.
        assertTrue(SessionState.expiredNotice.value)
        SessionState.setExpiredNotice(false)
    }
}
