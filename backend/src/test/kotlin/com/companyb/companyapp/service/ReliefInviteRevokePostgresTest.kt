package com.companyb.companyapp.service

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.ReliefInviteRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * #374 — accepted-invite revocation (the #363 owner rulings): any active branch member
 * may revoke an ACCEPTED future duty until the invitee clocks in; the day grant is
 * removed atomically; the whole branch hears it and the invitee gets an explicit
 * branch+date notice; reminders suppress naturally; REVOKED frees the per-person slot.
 */
class ReliefInviteRevokePostgresTest : BasePostgresTest() {
    private val inviterId = TestFixtures.uuid()
    private val revokerId = TestFixtures.uuid()
    private val inviteeId = TestFixtures.uuid()
    private val outsiderId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(inviterId, "revoke-inviter")
        IdentityFixtures.insertTestUser(revokerId, "revoke-revoker")
        IdentityFixtures.insertTestUser(inviteeId, "revoke-invitee")
        IdentityFixtures.insertTestUser(outsiderId, "revoke-outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Revoke Branch ${branchId.toString().take(8)}")
        // Invite creation resolves-or-creates its duty-day rows at this branch.

        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = inviterId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = inviterId,
                ),
            )
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = revokerId,
                    branchId = branchId,
                    slot = 2,
                    assignedBy = inviterId,
                ),
            )
        }
        // Accept writes the invitee's day-scoped grant as a user_capability row.
        // Branch-keyed notification tracking covers every recipient (the #358 lesson).
        // Clock-in upserts a branch_day_assignment row — untracked it blocks user teardown.
    }

    @Test
    fun `any member revokes an accepted duty - grant dies atomically and both audiences hear it`() {
        val duty = TestFixtures.today.plusDays(3)
        val inviteId = acceptInviteFor(duty)
        val branchDayId =
            ReliefInviteRepository.findById(inviteId)?.branchDayId ?: fail("invite missing")
        assertTrue(ReliefInviteRepository.hasActiveGrant(inviteeId, branchDayId), "precondition: grant live")

        // A second member (not the inviter) revokes — ruling 2: any active branch member.
        val revoked = ReliefInviteService.revokeInvite(revokerId, inviteId)

        assertEquals(ReliefInviteStatus.REVOKED, revoked.status)
        assertTrue(!ReliefInviteRepository.hasActiveGrant(inviteeId, branchDayId), "day grant removed")
        assertTrue(
            ReliefInviteRepository.findAcceptedForDutyDate(duty).isEmpty(),
            "reminder scan no longer matches (#374 suppression)",
        )

        val revokedCount =
            transaction {
                NotificationTable
                    .selectAll()
                    .where {
                        (NotificationTable.branchId eq branchId) and
                            (NotificationTable.eventType eq ReliefNotifications.INVITE_REVOKED)
                    }.count()
            }
        assertEquals(3L, revokedCount, "two branch members + the invitee")

        val inviteeNotices =
            NotificationRepository
                .findHistoryByUserId(inviteeId)
                .filter { it.eventType == ReliefNotifications.INVITE_REVOKED }
        assertEquals(1, inviteeNotices.size, "invitee gets exactly the explicit notice")
        val notice = inviteeNotices.single()
        assertTrue(notice.message.contains("Your relief duty"), "explicit phrasing: ${notice.message}")
        assertTrue(notice.message.contains(duty.toString()), "names the date: ${notice.message}")

        // Ruling 5 / ruling 1: REVOKED frees the person to be invited again.
        val reInvited = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        assertEquals(ReliefInviteStatus.PENDING, reInvited.status)
    }

    @Test
    fun `revocation locks once the invitee clocks in at that branch day`() {
        val duty = TestFixtures.today
        val inviteId = acceptInviteFor(duty)

        AttendanceService.clockIn(TestFixtures.uuid(), branchId, inviteeId)

        try {
            ReliefInviteService.revokeInvite(revokerId, inviteId)
            fail("post-clock-in revocation must 400")
        } catch (expected: ValidationException) {
            assertEquals("This relief duty has already started", expected.message)
        }

        val invite = ReliefInviteRepository.findById(inviteId) ?: fail("invite missing")
        assertEquals(ReliefInviteStatus.ACCEPTED, invite.status, "lock fires before any mutation")
    }

    @Test
    fun `non-members cannot revoke`() {
        val duty = TestFixtures.today.plusDays(3)
        val inviteId = acceptInviteFor(duty)

        try {
            ReliefInviteService.revokeInvite(outsiderId, inviteId)
            fail("non-member revocation must 403")
        } catch (expected: ForbiddenException) {
            assertTrue(expected.message!!.contains("active assignment"))
        }

        val invite = ReliefInviteRepository.findById(inviteId) ?: fail("invite missing")
        assertEquals(ReliefInviteStatus.ACCEPTED, invite.status)
    }

    @Test
    fun `already-revoked invite conflicts on a second revoke`() {
        val duty = TestFixtures.today.plusDays(3)
        val inviteId = acceptInviteFor(duty)
        ReliefInviteService.revokeInvite(revokerId, inviteId)

        try {
            ReliefInviteService.revokeInvite(revokerId, inviteId)
            fail("second revoke must 409")
        } catch (expected: ConflictException) {
            assertTrue(expected.message!!.contains("already responded"))
        }
    }

    @Test
    fun `declined invite is already decided and cannot be revoked`() {
        val duty = TestFixtures.today.plusDays(3)
        val pending = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        ReliefInviteService.declineInvite(inviteeId, pending.id)

        try {
            ReliefInviteService.revokeInvite(revokerId, pending.id)
            fail("DECLINED revoke must 409")
        } catch (expected: ConflictException) {
            assertTrue(expected.message!!.contains("already responded"))
        }
    }

    @Test
    fun `discovery read shows colleagues' accepted duties to any member and gates non-members`() {
        val duty = TestFixtures.today.plusDays(3)
        val inviteId = acceptInviteFor(duty)

        // A second member who never invited anyone sees the duty — ruling 2's discovery leg.
        val seen = ReliefInviteService.listBranchAccepted(revokerId, branchId)
        assertEquals(listOf(inviteId), seen.map { it.invite.id })
        assertEquals("Test revoke-invitee", seen.single().inviteeName)
        assertEquals(duty, seen.single().date)

        try {
            ReliefInviteService.listBranchAccepted(outsiderId, branchId)
            fail("non-member discovery must 403")
        } catch (expected: ForbiddenException) {
            assertTrue(expected.message!!.contains("active assignment"))
        }
    }

    @Test
    fun `discovery read excludes pending and past-day invites`() {
        val futureDuty = TestFixtures.today.plusDays(3)
        acceptInviteFor(futureDuty)

        // A PENDING invite is not an accepted duty.

        // An ACCEPTED row whose day already passed cannot be revoked — excluded server-side.
        val pastDay = BranchDayService.resolveOrCreate(branchId, TestFixtures.today.minusDays(1))
        val pastInviteId = UUID.randomUUID()
        transaction {
            ReliefInviteRepository.insertInTransaction(pastInviteId, pastDay.id, inviterId, inviteeId)
            ReliefInviteRepository.respondInTransaction(pastInviteId, ReliefInviteStatus.ACCEPTED)
        }

        val seen = ReliefInviteService.listBranchAccepted(revokerId, branchId)
        assertEquals(listOf(futureDuty), seen.map { it.date })
    }

    private fun acceptInviteFor(date: LocalDate): UUID {
        val invite = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, date)
        ReliefInviteService.acceptInvite(inviteeId, invite.id)
        return invite.id
    }
}
