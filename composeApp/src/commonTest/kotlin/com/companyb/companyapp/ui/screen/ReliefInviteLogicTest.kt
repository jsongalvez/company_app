package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.ReliefInviteResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReliefInviteLogicTest {
    private fun invite(
        status: ReliefInviteStatus,
        date: String,
    ): ReliefInviteResponse =
        ReliefInviteResponse(
            id = "invite-1",
            branchId = "b1",
            branchName = "Branch A",
            branchDayId = "bd1",
            date = date,
            invitedBy = "inviter",
            inviterName = "Inviter",
            invitee = "invitee",
            inviteeName = "Invitee",
            status = status,
            createdAt = "2026-08-01T00:00:00Z",
        )

    private val today = LocalDate(2026, 8, 15)

    @Test
    fun `pending invite with a past day is expired`() {
        assertTrue(isInviteExpired(invite(ReliefInviteStatus.PENDING, "2026-08-14"), today))
        assertFalse(isInviteActionable(invite(ReliefInviteStatus.PENDING, "2026-08-14"), today))
    }

    @Test
    fun `pending invite for today or later is actionable`() {
        assertFalse(isInviteExpired(invite(ReliefInviteStatus.PENDING, "2026-08-15"), today))
        assertFalse(isInviteExpired(invite(ReliefInviteStatus.PENDING, "2026-08-16"), today))
        assertTrue(isInviteActionable(invite(ReliefInviteStatus.PENDING, "2026-08-16"), today))
    }

    @Test
    fun `resolved invites are never expired or actionable`() {
        val resolved =
            listOf(
                ReliefInviteStatus.ACCEPTED,
                ReliefInviteStatus.DECLINED,
                ReliefInviteStatus.RETRACTED,
                ReliefInviteStatus.REVOKED,
            )
        for (status in resolved) {
            val past = invite(status, "2026-08-01")
            assertFalse(isInviteExpired(past, today), "$status must not render expired")
            assertFalse(isInviteActionable(past, today), "$status must not be actionable")
        }
    }

    @Test
    fun `unparseable date renders not-expired and not-actionable`() {
        val bad = invite(ReliefInviteStatus.PENDING, "not-a-date")
        assertFalse(isInviteExpired(bad, today))
        assertFalse(isInviteActionable(bad, today))
    }

    @Test
    fun `parseInviteDate parses ISO dates and rejects garbage`() {
        assertEquals(LocalDate(2026, 8, 15), parseInviteDate("2026-08-15"))
        assertNull(parseInviteDate("15/08/2026"))
    }
}
