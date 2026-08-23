package com.companyb.companyapp.service
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.service.attendance.AttendanceRepository
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.attendance.ClockInParams
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeServiceBranchesPostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()
    private val noAssignmentUserId = TestFixtures.uuid()
    private val inactiveUserId = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val reliefBranch = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(userId, "me-branches")
        trackOwned(AppUserTable, AppUserTable.id, userId)
        DatabaseTestHelper.insertTestUser(noAssignmentUserId, "me-no-assignments")
        trackOwned(AppUserTable, AppUserTable.id, noAssignmentUserId)
        DatabaseTestHelper.insertUser(
            id = inactiveUserId,
            username = "inactive-$inactiveUserId",
            passwordHash = "test-password-hash",
            email = "${inactiveUserId.toString().take(8)}@t.st",
            displayName = "Inactive User",
            status = UserStatus.INACTIVE,
        )
        trackOwned(AppUserTable, AppUserTable.id, inactiveUserId)
        DatabaseTestHelper.insertTestBranch(branchA, "Branch A")
        DatabaseTestHelper.insertTestBranch(branchB, "Branch B")
        DatabaseTestHelper.insertTestBranch(reliefBranch, "Relief Branch")
        trackOwned(BranchTable, BranchTable.id, branchA)
        trackOwned(BranchTable, BranchTable.id, branchB)
        trackOwned(BranchTable, BranchTable.id, reliefBranch)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchA)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchB)
        trackOwned(BranchDayTable, BranchDayTable.branchId, reliefBranch)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.userId, userId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.assignedBy, userId)
        trackOwned(AttendanceTable, AttendanceTable.userId, userId)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, userId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, userId)
    }

    @Test
    fun `getBranches returns assigned branches with NOT_CLOCKED_IN status`() {
        assignToBranch(branchA)
        assignToBranch(branchB)

        val branches = MeService.getBranches(userId)

        assertEquals(2, branches.size)
        assertEquals(listOf("Branch A", "Branch B"), branches.map { it.branchName })
        assertTrue(branches.all { it.clockInStatus == BranchClockInStatus.NOT_CLOCKED_IN })
        assertTrue(branches.all { !it.isRelief })
        // #381 — the assignment slot rides on the row (both fixtures assign at slot 1).
        assertTrue(branches.all { it.slot == 1.toShort() })
        assertTrue(branches.all { it.branchType == BranchType.CLINIC })
        assertTrue(branches.all { it.branchId.isNotBlank() })
    }

    @Test
    fun `getBranches marks clocked-in branch HERE and other assigned branches ELSEWHERE`() {
        assignToBranch(branchA)
        assignToBranch(branchB)
        AttendanceService.clockIn(TestFixtures.uuid(), branchA, userId)

        val branches = MeService.getBranches(userId).associateBy { it.branchId }

        assertEquals(2, branches.size)
        assertEquals(BranchClockInStatus.CLOCKED_IN_HERE, branches[branchA.toString()]?.clockInStatus)
        assertEquals(BranchClockInStatus.CLOCKED_IN_ELSEWHERE, branches[branchB.toString()]?.clockInStatus)
        assertTrue(branches.getValue(branchA.toString()).isRelief.not())
    }

    @Test
    fun `getBranches includes relief clock-in branch with isRelief true`() {
        AttendanceService.clockIn(TestFixtures.uuid(), reliefBranch, userId)

        val branches = MeService.getBranches(userId)

        assertEquals(1, branches.size)
        assertEquals(reliefBranch.toString(), branches.single().branchId)
        assertEquals(BranchClockInStatus.CLOCKED_IN_HERE, branches.single().clockInStatus)
        assertTrue(branches.single().isRelief)
        // #381 — relief rows have no assignment, hence no slot.
        assertEquals(null, branches.single().slot)
    }

    @Test
    fun `getBranches returns empty list for user with no assignments and no clock-in`() {
        val branches = MeService.getBranches(noAssignmentUserId)

        assertTrue(branches.isEmpty())
    }

    @Test
    fun `getBranches throws ForbiddenException for INACTIVE user`() {
        assertFailsWith<ForbiddenException> {
            MeService.getBranches(inactiveUserId)
        }
    }

    @Test
    fun `getBranches throws NotFoundException for non-existent user`() {
        assertFailsWith<NotFoundException> {
            MeService.getBranches(TestFixtures.uuid())
        }
    }

    @Test
    fun `getBranches excludes ended assignments`() {
        DatabaseTestHelper.insertTestAssignment(
            userId = userId,
            branchId = branchA,
            slot = 1,
            assignedBy = userId,
            ended = true,
        )

        val branches = MeService.getBranches(userId)

        assertTrue(branches.isEmpty())
    }

    @Test
    fun `getBranches ignores active clock-in from a previous day`() {
        assignToBranch(branchA)
        val yesterday = TestFixtures.today.minusDays(1)
        val staleBranchDay = BranchDayService.resolveOrCreate(branchB, yesterday)
        transaction {
            AttendanceRepository.clockInInTransaction(
                ClockInParams(
                    attendanceId = TestFixtures.uuid(),
                    branchDayId = staleBranchDay.id,
                    userId = userId,
                    markedBy = userId,
                    branchDayAssignmentId = TestFixtures.uuid(),
                    isRelief = true,
                    branchId = branchB,
                ),
            )
        }

        val branches = MeService.getBranches(userId)

        assertEquals(1, branches.size)
        assertEquals(branchA.toString(), branches.single().branchId)
        assertEquals(BranchClockInStatus.NOT_CLOCKED_IN, branches.single().clockInStatus)
    }

    private fun assignToBranch(branchId: UUID): UUID =
        DatabaseTestHelper.insertTestAssignment(
            userId = userId,
            branchId = branchId,
            slot = 1,
            assignedBy = userId,
        )
}
