package com.companyb.companyapp.workforce

import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reading a branch's active assignments and its member directory. */
class UserBranchAssignmentListPostgresTest : UserBranchAssignmentServicePostgresBase() {
    private val sourceId = TestFixtures.uuid()

    @Test
    fun `findActiveByBranch returns active assignments sorted by slot`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 5)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(2, assignments.size)
        assertEquals(userAId, assignments[0].userId)
        assertEquals(1, assignments[0].slot)
        assertEquals(userBId, assignments[1].userId)
        assertEquals(5, assignments[1].slot)
    }

    @Test
    fun `findActiveByBranch excludes ended assignments`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)
        UserBranchAssignmentService.remove(callerId, branchId, idB)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(1, assignments.size)
        assertEquals(userAId, assignments[0].userId)
    }

    @Test
    fun `findActiveByBranch without MANAGE_USERS is forbidden at service layer`() {
        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.findActiveByBranch(nonManagerId, branchId)
        }
    }

    @Test
    fun `findActiveByBranch with MANAGE_USERS returns empty list`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertTrue(assignments.isEmpty())
    }

    @Test
    fun `findActiveByBranch with unknown branch throws NotFoundException`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.findActiveByBranch(callerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `findActiveByBranch without MANAGE_USERS on unknown branch stays forbidden`() {
        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.findActiveByBranch(nonManagerId, TestFixtures.uuid())
        }
    }

    // --- #366 requested-practitioner directory ---

    @Test
    fun `listActiveMembers returns active member names in slot order`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, callerId, 3)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 5)

        val members = UserBranchAssignmentService.listActiveMembers(userAId, branchId)

        assertEquals(
            listOf("Test usera", "Test caller", "Test userb"),
            members.map { it.displayName },
        )
        assertEquals(3, members.map { it.id }.distinct().size)
    }

    @Test
    fun `listActiveMembers without membership is forbidden at service layer`() {
        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)
        }
    }

    @Test
    fun `listActiveMembers with unknown branch throws NotFoundException`() {
        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.listActiveMembers(nonManagerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `listActiveMembers rejects a member of a different branch`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), otherBranchId, userBId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(userBId, branchId)
        }
    }

    @Test
    fun `listActiveMembers excludes deactivated users`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 2)
        transaction {
            AppUserTable.update({ AppUserTable.id eq userBId }) {
                it[status] = UserStatus.INACTIVE
            }
        }

        val members = UserBranchAssignmentService.listActiveMembers(userAId, branchId)

        assertEquals(listOf(userAId), members.map { it.id })
    }

    @Test
    fun `listActiveMembers allows a BRANCH_DAY edit grant for todays branch day`() {
        // #402 — relief duty: the grant shape the session-create gate accepts must also
        // load the practitioner directory those flows pick from.
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        val todayBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.reliefDay(
            userId = nonManagerId,
            branchDayId = todayBranchDayId,
            sourceId = sourceId,
        )

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(listOf("Test usera"), members.map { it.displayName })
    }

    @Test
    fun `listActiveMembers allows a branch member when a day row exists`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.member(nonManagerId, branchId)

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(setOf("Test usera", "Test nonmgr"), members.map { it.displayName }.toSet())
    }

    @Test
    fun `listActiveMembers allows a branch member with no day row yet`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        IdentityFixtures.member(nonManagerId, branchId)

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(setOf("Test usera", "Test nonmgr"), members.map { it.displayName }.toSet())
    }

    @Test
    fun `listActiveMembers rejects a day grant scoped to another days row`() {
        // Today's row exists but the grant references yesterday's — the mismatch path
        // (not the missing-day fallback) must reject.
        val todayBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val yesterdayBranchDayId =
            BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        check(todayBranchDayId != yesterdayBranchDayId)
        IdentityFixtures.reliefDay(
            userId = nonManagerId,
            branchDayId = yesterdayBranchDayId,
            sourceId = sourceId,
        )

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)
        }
    }
}
