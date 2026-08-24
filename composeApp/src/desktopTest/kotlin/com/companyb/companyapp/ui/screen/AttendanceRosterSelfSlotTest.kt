package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.MemberAttendanceResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #416 — the dashboard self-service slot affordance: the overflow menu renders on the
 * caller's own row only (every other row stays read-only display), swap candidates are the
 * other roster members, and a missing caller identity fail-closes both.
 */
class AttendanceRosterSelfSlotTest {
    private fun row(
        userId: String,
        slot: Short,
        present: Boolean = true,
    ) = MemberAttendanceResponse(
        userId = userId,
        displayName = "Member $userId",
        slot = slot,
        present = present,
    )

    @Test
    fun `own row offers the self-service affordance`() {
        assertTrue(AttendanceRosterLogic.canEditOwnSlot(row("me", 1), currentUserId = "me"))
    }

    @Test
    fun `another member's row stays read-only`() {
        assertFalse(AttendanceRosterLogic.canEditOwnSlot(row("other", 2), currentUserId = "me"))
    }

    @Test
    fun `missing caller identity fail-closes the own-row gate`() {
        assertFalse(AttendanceRosterLogic.canEditOwnSlot(row("me", 1), currentUserId = null))
    }

    @Test
    fun `swap candidates are every other roster member`() {
        val rows = listOf(row("me", 1), row("a", 2), row("b", 3))

        assertEquals(listOf("a", "b"), AttendanceRosterLogic.swapCandidates(rows, "me").map { it.userId })
    }

    @Test
    fun `no caller identity means no swap targets`() {
        assertTrue(AttendanceRosterLogic.swapCandidates(listOf(row("a", 1)), null).isEmpty())
    }

    @Test
    fun `solo roster leaves no one to trade with`() {
        assertTrue(AttendanceRosterLogic.swapCandidates(listOf(row("me", 1)), "me").isEmpty())
    }

    @Test
    fun `candidate label names the member and the slot it trades with`() {
        assertEquals("Member a · slot 2", AttendanceRosterLogic.swapCandidateLabel(row("a", 2)))
    }
}
