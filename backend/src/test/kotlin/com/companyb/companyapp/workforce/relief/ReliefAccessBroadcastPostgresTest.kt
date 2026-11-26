package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Notifications a relief ask sends, committed with the command that causes them. */
class ReliefAccessBroadcastPostgresTest : ReliefAccessServicePostgresBase() {
    /** Unread-or-read notification rows owned by [userId] (#358 broadcast assertions). */
    private fun notificationsFor(userId: UUID): List<com.companyb.companyapp.notification.Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.userId eq userId }
                .map { row ->
                    com.companyb.companyapp.notification.Notification(
                        id = row[NotificationTable.id],
                        sessionId = row[NotificationTable.sessionId],
                        userId = row[NotificationTable.userId],
                        branchId = row[NotificationTable.branchId],
                        message = row[NotificationTable.message],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                        eventType = row[NotificationTable.eventType],
                        sourceId = row[NotificationTable.sourceId],
                        targetDate = row[NotificationTable.targetDate],
                    )
                }
        }

    // ──────────────────────────────────────────────
    // #358 notification events (broadcast writes commit with the causing command)
    // ──────────────────────────────────────────────

    @Test
    fun `request creation broadcasts one message per member naming today`() {
        val requestId = TestFixtures.uuid()

        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        val notices = notificationsFor(memberId)
        assertEquals(1, notices.size)
        val notice = notices.single()
        assertEquals(ReliefNotifications.REQUESTED, notice.eventType)
        assertEquals(requestId, notice.sourceId)
        assertEquals(branchId, notice.branchId)
        assertEquals(TestFixtures.today, notice.targetDate)
        assertTrue(notice.message.contains("requested relief duty at $branchName for today"))
        assertTrue(notice.message.contains("relief"))
        // The requester raised it — no self-ping.
        assertTrue(notificationsFor(reliefUserId).isEmpty())
    }

    // #409 — deactivation revokes access but leaves the assignment open; the audience
    // definition joins user status, so an INACTIVE member never receives broadcast rows.
    @Test
    fun `broadcast skips deactivated members with open assignments`() {
        val inactiveMember = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(inactiveMember, "inactive-member")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = inactiveMember,
            branchId = branchId,
            slot = 3,
            assignedBy = memberId,
        )
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveMember }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, null, reliefUserId)
        trackRequest(requestId)

        assertTrue(notificationsFor(memberId).any { it.eventType == ReliefNotifications.REQUESTED })
        assertTrue(notificationsFor(inactiveMember).isEmpty())
    }

    @Test
    fun `future-dated request copy names the date instead of today`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val requestId = TestFixtures.uuid()

        ReliefAccessService.requestReliefAccess(requestId, branchId, futureDate, reliefUserId)
        trackRequest(requestId)

        val notice = notificationsFor(memberId).single()
        assertTrue(notice.message.endsWith("on $futureDate"))
        assertEquals(futureDate, notice.targetDate)
    }

    @Test
    fun `grant broadcasts the outcome to members and the requester`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.grantAccess(requestId, memberId)

        val memberNotices = notificationsFor(memberId).filter { it.eventType == ReliefNotifications.GRANTED }
        val requesterNotices = notificationsFor(reliefUserId).filter { it.eventType == ReliefNotifications.GRANTED }
        assertEquals(1, memberNotices.size)
        assertEquals(1, requesterNotices.size)
        val message = memberNotices.single().message
        assertTrue(message.contains("granted"))
        assertTrue(message.contains("member"))
        assertTrue(message.contains("relief"))
    }

    @Test
    fun `deny broadcasts the outcome to members and the requester`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.denyAccess(requestId, memberId)

        val requesterNotice = notificationsFor(reliefUserId).single { it.eventType == ReliefNotifications.DENIED }
        assertTrue(requesterNotice.message.contains("denied"))
    }

    @Test
    fun `idempotent grant replay does not duplicate the outcome broadcast`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.grantAccess(requestId, memberId)
        ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(
            1,
            notificationsFor(memberId).count { it.eventType == ReliefNotifications.GRANTED },
        )
    }

    @Test
    fun `expiry job announces past-day pending requests to the original ping list`() {
        // A live request whose day silently slides into the past: create for a future
        // date, then rewind the day row — requestReliefAccess itself rejects past dates.
        val expiredDate = TestFixtures.today.minusDays(1)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(
            requestId,
            branchId,
            TestFixtures.today.plusDays(FUTURE_DAYS),
            reliefUserId,
        )
        trackRequest(requestId)
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq ReliefAccessRepository.findById(requestId)!!.branchDayId }) {
                it[BranchDayTable.date] = expiredDate
            }
        }

        val announced = ReliefRequestExpiryJob.run()

        assertEquals(1, announced)
        val memberNotice = notificationsFor(memberId).single { it.eventType == ReliefNotifications.EXPIRED }
        assertEquals(requestId, memberNotice.sourceId)
        assertTrue(memberNotice.message.contains("expired unanswered"))

        // Idempotent: the stored notice is the marker — re-runs announce nothing.
        assertEquals(0, ReliefRequestExpiryJob.run())
    }

    @Test
    fun `invite acceptance broadcasts to the branch members`() {
        val inviteeId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(inviteeId, "relief-invitee")
        // The acceptance audit row is authored by the invitee; the capability by grant.

        // Mint through the service: the invite lands on the pre-seeded today day row.
        val invite = ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)

        ReliefInviteService.acceptInvite(inviteeId, invite.id)

        val notice = notificationsFor(memberId).single { it.eventType == ReliefNotifications.INVITE_ACCEPTED }
        assertEquals(invite.id, notice.sourceId)
        assertTrue(notice.message.contains("accepted the relief invite"))
        assertTrue(notice.message.contains("relief-invitee"))
    }
}
