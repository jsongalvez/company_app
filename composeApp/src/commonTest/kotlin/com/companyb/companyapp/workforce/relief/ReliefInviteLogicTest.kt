package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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

    private fun request(
        status: ReliefAccessStatus,
        date: String?,
    ): ReliefAccessResponse =
        ReliefAccessResponse(
            id = "req-1",
            branchDayId = "bd1",
            requestedBy = "requester",
            requestStatus = status,
            date = date,
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
        assertNull(parseInviteDate(null))
    }

    @Test
    fun `operational date rolls at 04h00 Manila, not midnight`() {
        // 03:59 Manila on the 15th is still operational-day the 14th (mirrors BranchDayService).
        assertEquals(
            LocalDate(2026, 8, 14),
            currentOperationalDate(Instant.parse("2026-08-14T19:59:00Z")),
        )
        // 04:00 Manila sharp flips to the 15th.
        assertEquals(
            LocalDate(2026, 8, 15),
            currentOperationalDate(Instant.parse("2026-08-14T20:00:00Z")),
        )
        // Mid-afternoon stays plain calendar equality.
        assertEquals(
            LocalDate(2026, 8, 15),
            currentOperationalDate(Instant.parse("2026-08-15T09:30:00Z")),
        )
    }

    @Test
    fun `pending request with a past day is expired`() {
        assertTrue(isRequestExpired(request(ReliefAccessStatus.PENDING, "2026-08-14"), today))
        assertFalse(isRequestExpired(request(ReliefAccessStatus.PENDING, "2026-08-16"), today))
    }

    @Test
    fun `null or unparseable request date means today — never expired`() {
        assertFalse(isRequestExpired(request(ReliefAccessStatus.PENDING, null), today))
        assertFalse(isRequestExpired(request(ReliefAccessStatus.PENDING, "not-a-date"), today))
    }

    @Test
    fun `resolved requests are never expired`() {
        for (status in listOf(ReliefAccessStatus.GRANTED, ReliefAccessStatus.DENIED, ReliefAccessStatus.CANCELLED)) {
            assertFalse(isRequestExpired(request(status, "2026-08-01"), today), "$status must not render expired")
        }
    }

    // #401 — the deep-link panel's invite rows.

    @Test
    fun `panel rows carry the invitee name and the true status`() {
        val rows =
            toReliefDayInviteRows(
                listOf(
                    invite(ReliefInviteStatus.ACCEPTED, "2026-08-15"),
                    invite(ReliefInviteStatus.DECLINED, "2026-08-16"),
                    invite(ReliefInviteStatus.REVOKED, "2026-08-01"),
                ),
                today,
            )
        assertEquals(
            listOf(
                ReliefDayInviteRow("Invitee", "ACCEPTED"),
                ReliefDayInviteRow("Invitee", "DECLINED"),
                ReliefDayInviteRow("Invitee", "REVOKED"),
            ),
            rows,
        )
    }

    @Test
    fun `past-day pending panel row renders Expired, not a live PENDING`() {
        val rows =
            toReliefDayInviteRows(listOf(invite(ReliefInviteStatus.PENDING, "2026-08-14")), today)
        assertEquals(listOf(ReliefDayInviteRow("Invitee", "Expired")), rows)
    }

    @Test
    fun `blank invitee name falls back to A user`() {
        val anonymous = invite(ReliefInviteStatus.PENDING, "2026-08-15").copy(inviteeName = "")
        val rows = toReliefDayInviteRows(listOf(anonymous), today)
        assertEquals(listOf(ReliefDayInviteRow("A user", "PENDING")), rows)
    }
}
