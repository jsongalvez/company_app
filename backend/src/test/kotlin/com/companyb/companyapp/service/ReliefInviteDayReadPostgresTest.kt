package com.companyb.companyapp.service

import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.ReliefInviteTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
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
        DatabaseTestHelper.insertTestUser(inviterId, "dayread-inviter")
        trackOwned(AppUserTable, AppUserTable.id, inviterId)
        DatabaseTestHelper.insertTestUser(secondInviterId, "dayread-second")
        trackOwned(AppUserTable, AppUserTable.id, secondInviterId)
        DatabaseTestHelper.insertTestUser(inviteeId, "dayread-invitee")
        trackOwned(AppUserTable, AppUserTable.id, inviteeId)
        DatabaseTestHelper.insertTestUser(strangerId, "dayread-stranger")
        trackOwned(AppUserTable, AppUserTable.id, strangerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Day Read Branch ${branchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        // Invite creation resolves-or-creates its duty-day rows at this branch.
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, inviterId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, secondInviterId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, inviterId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, secondInviterId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, inviteeId)
        // Invite responses broadcast (#358) — branch-keyed notification tracking covers it.
        trackOwned(NotificationTable, NotificationTable.branchId, branchId)
    }

    @Test
    fun `member sees every invite at the branch on the date across inviters and statuses`() {
        val duty = TestFixtures.today.plusDays(3)
        val first = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, first.id)
        val second =
            ReliefInviteService.createInvite(secondInviterId, branchId, strangerId, duty.plusDays(0))
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, second.id)

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
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, invite.id)

        assertTrue(ReliefInviteService.listForDay(inviterId, branchId, other).isEmpty())

        val memberRows = ReliefInviteService.listForDay(inviterId, branchId, duty)
        assertEquals(listOf(invite.id), memberRows.map { it.invite.id })
    }

    @Test
    fun `invitee who is not a member sees only their own rows`() {
        val duty = TestFixtures.today.plusDays(3)
        val mine = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, mine.id)
        val theirs = ReliefInviteService.createInvite(inviterId, branchId, strangerId, duty)
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, theirs.id)

        val rows = ReliefInviteService.listForDay(inviteeId, branchId, duty)

        assertEquals(listOf(mine.id), rows.map { it.invite.id })
    }

    @Test
    fun `unrelated caller sees no rows - no existence leak`() {
        val duty = TestFixtures.today.plusDays(3)
        val invite = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, invite.id)

        assertTrue(ReliefInviteService.listForDay(TestFixtures.uuid(), branchId, duty).isEmpty())
    }

    @Test
    fun `resolved statuses stay visible - the tap renders true state`() {
        val duty = TestFixtures.today.plusDays(3)
        val declined = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)
        trackOwned(ReliefInviteTable, ReliefInviteTable.id, declined.id)
        ReliefInviteService.declineInvite(inviteeId, declined.id)

        val rows = ReliefInviteService.listForDay(secondInviterId, branchId, duty)

        assertEquals(1, rows.size)
        assertEquals(ReliefInviteStatus.DECLINED, rows.single().invite.status)
    }
}
