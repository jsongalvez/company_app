package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #401 — the deep-link day read (`listForDay`): a notification tap carries (branchId,
 * date), and the panel behind it must render the tapped day's invite truth. Members see
 * every invite at that branch+date across all statuses; non-member audiences see only
 * their own invitee rows; unrelated callers see nothing.
 */
class ReliefInviteDayReadPostgresTest : BasePostgresTest() {
    private val inviterId = TestFixtures.uuid()
    private val secondInviterId = TestFixtures.uuid()
    private val inviteeId = TestFixtures.uuid()
    private val strangerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(inviterId, "dayread-inviter")
        IdentityFixtures.insertTestUser(secondInviterId, "dayread-second")
        IdentityFixtures.insertTestUser(inviteeId, "dayread-invitee")
        IdentityFixtures.insertTestUser(strangerId, "dayread-stranger")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Day Read Branch ${branchId.toString().take(8)}")
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
                    userId = secondInviterId,
                    branchId = branchId,
                    slot = 2,
                    assignedBy = inviterId,
                ),
            )
        }
        // Invite responses broadcast (#358) — branch-keyed notification tracking covers it.
    }

    @Test
    fun `member sees every invite at the branch on the date across inviters and statuses`() {
        val duty = TestFixtures.today.plusDays(3)
        val first = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        val second =
            ReliefInviteService.createInvite(secondInviterId, branchId, strangerId, duty.plusDays(0))

        val rows = ReliefInviteService.listForDay(inviterId, branchId, duty)

        assertEquals(2, rows.size, "both invites land on the same day")
        assertEquals(setOf(first.id, second.id), rows.map { it.invite.id }.toSet())
        assertTrue(rows.all { it.date == duty }, "only the tapped day's rows")
    }

    @Test
    fun `other dates are excluded from the day read`() {
        val duty = TestFixtures.today.plusDays(3)
        val other = TestFixtures.today.plusDays(4)
        val invite = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)

        assertTrue(ReliefInviteService.listForDay(inviterId, branchId, other).isEmpty())

        val memberRows = ReliefInviteService.listForDay(inviterId, branchId, duty)
        assertEquals(listOf(invite.id), memberRows.map { it.invite.id })
    }

    @Test
    fun `invitee who is not a member sees only their own rows`() {
        val duty = TestFixtures.today.plusDays(3)
        val mine = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        ReliefInviteService.createInvite(inviterId, branchId, strangerId, duty)

        val rows = ReliefInviteService.listForDay(inviteeId, branchId, duty)

        assertEquals(listOf(mine.id), rows.map { it.invite.id })
    }

    @Test
    fun `unrelated caller sees no rows - no existence leak`() {
        val duty = TestFixtures.today.plusDays(3)
        val invite = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)

        assertTrue(ReliefInviteService.listForDay(TestFixtures.uuid(), branchId, duty).isEmpty())
    }

    @Test
    fun `resolved statuses stay visible - the tap renders true state`() {
        val duty = TestFixtures.today.plusDays(3)
        val declined = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        ReliefInviteService.declineInvite(inviteeId, declined.id)

        val rows = ReliefInviteService.listForDay(secondInviterId, branchId, duty)

        assertEquals(1, rows.size)
        assertEquals(ReliefInviteStatus.DECLINED, rows.single().invite.status)
    }
}
